package com.example.bookstore.repository;

import com.example.bookstore.model.NotificationDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * NotificationDeliveryRepository
 * 
 * Purpose:
 *   Data access layer for notification_delivery table
 *   Query methods optimized for:
 *     - Queue worker polling pending retries
 *     - Audit trail investigation
 *     - Failed delivery dashboard
 *     - Channel health metrics
 * 
 * Design:
 *   - Uses derived query methods for simple queries
 *   - Uses @Query for complex queries (index hints, batch operations)
 *   - All methods are read-only or write-specific (not mixed)
 */
@Repository
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    
    /**
     * Query 1: Find pending deliveries ready for retry
     * 
     * Purpose:
     *   Queue worker calls this every 1 second to find ready-to-send tasks
     * 
     * SQL Query Plan:
     *   Uses IX_notification_delivery_pending_retry index
     *   Efficient because:
     *     - Filter by status='PENDING' (low cardinality = few matches)
     *     - Filter by next_retry_at <= NOW (reduces result set further)
     *     - Index is small (only 2 columns)
     * 
     * Usage:
     *   List<NotificationDelivery> pending = repo.findPendingRetries(Pageable.ofSize(100));
     *   for (NotificationDelivery task : pending) {
     *       try {
     *           sseService.sendEventToUser(task.getNotification().getUser().getId(), ...);
     *           task.setStatus(SENT);
     *       } catch (Exception e) {
     *           task.setAttemptCount(task.getAttemptCount() + 1);
     *           task.setNextRetryAt(calcBackoffTime(task.getAttemptCount()));
     *           task.setLastError(e.getMessage());
     *       }
     *       repo.save(task);
     *   }
     * 
     * Performance:
     *   - Indexed lookup: O(log n) to find rows
     *   - No full table scan
     *   - Scales to millions of delivery records
     * 
     * Pagination:
     *   limit 100 to batch: avoid locking many rows at once
     *   worker calls this method repeatedly until queue empty
     */
    @Query(value = """
        SELECT nd FROM NotificationDelivery nd
        WHERE nd.status = 'PENDING'
        AND nd.nextRetryAt <= CURRENT_TIMESTAMP
        ORDER BY nd.nextRetryAt ASC, nd.id ASC
        """)
    List<NotificationDelivery> findPendingRetries(Pageable pageable);
    
    /**
     * Query 2: Find all delivery attempts for a notification (audit trail)
     * 
     * Purpose:
     *   Dashboard/admin tool to investigate: "why wasn't this notification delivered?"
     * 
     * SQL Query Plan:
     *   Uses IX_notification_delivery_by_notification index
     *   Fast lookup by notification_id (FK)
     * 
     * Usage:
     *   List<NotificationDelivery> attempts = repo.findByNotificationId(123L);
     *   // Shows: attempt 1 failed 14:30, attempt 2 failed 14:32, attempt 3 succeeded 14:36
     * 
     * Result Order:
     *   Newest first (ORDER BY created_at DESC)
     *   Allows quick inspection of latest attempts
     */
    @Query(value = """
        SELECT nd FROM NotificationDelivery nd
        WHERE nd.notification.id = :notificationId
        ORDER BY nd.createdAt DESC
        """)
    List<NotificationDelivery> findByNotificationId(@Param("notificationId") Long notificationId);
    
    /**
     * Query 3: Find failed deliveries for manual intervention
     * 
     * Purpose:
     *   Dashboard to show "which notifications are stuck after 5 retries?"
     * 
     * SQL Query Plan:
     *   Uses IX_notification_delivery_failed index
     *   Filters by status='FAILED' (very low cardinality)
     * 
     * Usage:
     *   Page<NotificationDelivery> failed = repo.findFailedDeliveries(
     *       Pageable.ofSize(50)
     *   );
     *   // ops manually investigate why email failed
     * 
     * Pagination:
     *   Support large result sets (could be thousands per day)
     *   Dashboard shows 50 per page, sorted by newest first
     */
    @Query(value = """
        SELECT nd FROM NotificationDelivery nd
        WHERE nd.status IN ('FAILED', 'DROPPED')
        ORDER BY nd.createdAt DESC
        """)
    List<NotificationDelivery> findFailedDeliveries(Pageable pageable);
    
    /**
     * Query 4: Monitor per-channel health
     * 
     * Purpose:
     *   Metrics/SRE dashboard: "what's the success rate for SSE channel?"
     * 
     * SQL Query Plan:
     *   Uses IX_notification_delivery_channel index
     *   Filters by channel (low cardinality) + recent created_at (index ordered)
     * 
     * Usage:
     *   int sseCount = repo.countByChannelAndStatusAndCreatedAtAfter(
     *       "SSE",
     *       DeliveryStatus.SENT,
     *       LocalDateTime.now().minusHours(1)
     *   );
     *   int sseFailCount = repo.countByChannelAndStatusAndCreatedAtAfter(
     *       "SSE",
     *       DeliveryStatus.FAILED,
     *       LocalDateTime.now().minusHours(1)
     *   );
     *   successRate = (double) sseCount / (sseCount + sseFailCount);
     * 
     * Time window: last 1 hour, can be parameterized
     */
    @Query(value = """
        SELECT COUNT(nd) FROM NotificationDelivery nd
        WHERE nd.channel = :channel
        AND nd.status = :status
        AND nd.createdAt >= :startTime
        """)
    long countByChannelAndStatusAndCreatedAtAfter(
        @Param("channel") String channel,
        @Param("status") NotificationDelivery.DeliveryStatus status,
        @Param("startTime") LocalDateTime startTime
    );
    
    /**
     * Query 5: Find stuck deliveries (potential queue worker failure)
     * 
     * Purpose:
     *   Alert: "queue worker hasn't processed this for 30 minutes" → investigate app health
     * 
     * SQL Query Plan:
     *   Filters by status='PENDING' + updated_at very old
     *   May not be indexed, but used infrequently (health check only)
     * 
     * Usage:
     *   List<NotificationDelivery> stuck = repo.findStuckDeliveries(
     *       LocalDateTime.now().minusMinutes(30)
     *   );
     *   if (!stuck.isEmpty()) {
     *       alertOps("Notification queue worker might be stuck");
     *   }
     * 
     * Threshold: 30 minutes of no progress
     */
    @Query(value = """
        SELECT nd FROM NotificationDelivery nd
        WHERE nd.status = 'PENDING'
        AND nd.updatedAt <= :staleThreshold
        ORDER BY nd.updatedAt ASC
        """)
    List<NotificationDelivery> findStuckDeliveries(@Param("staleThreshold") LocalDateTime staleThreshold);
    
    /**
     * Query 6: Bulk retry (operational tool)
     * 
     * Purpose:
     *   Ops tool: "Email service was down 1 hour, retry all failed email deliveries"
     * 
     * Usage:
     *   repo.markForRetry("EMAIL", LocalDateTime.now().minusHours(1));
     *   // Next queue worker cycle will retry all these
     * 
     * Rationale:
     *   - Sets all FAILED/DROPPED back to PENDING
     *   - Resets attempt_count to 0 (fresh start)
     *   - Sets next_retry_at = NOW (immediate retry)
     *   - Updated_at timestamp will show when retry was triggered
     */
    @Query(value = """
        UPDATE NotificationDelivery nd
        SET nd.status = 'PENDING',
            nd.attemptCount = 0,
            nd.nextRetryAt = CURRENT_TIMESTAMP,
            nd.lastError = NULL
        WHERE nd.channel = :channel
        AND nd.status IN ('FAILED', 'DROPPED')
        AND nd.createdAt >= :fromTime
        """)
    void markForRetry(
        @Param("channel") String channel,
        @Param("fromTime") LocalDateTime fromTime
    );
    
    /**
     * Query 7: Clean up very old records (maintenance task)
     * 
     * Purpose:
     *   Monthly cleanup: delete delivery logs older than 90 days
     *   Prevents table from growing unbounded
     * 
     * Usage:
     *   repo.deleteOldDeliveries(LocalDateTime.now().minusDays(90));
     *   // Usually run as scheduled task at 2 AM
     * 
     * Why 90 days?
     *   - Balances storage vs. audit trail
     *   - Long enough for post-incident investigation
     *   - Short enough to keep table performant
     * 
     * Caveat:
     *   This is just a marker; actual deletion job should be managed separately
     *   to avoid long-running transactions locking other queries
     */
    @Query(value = """
        DELETE FROM NotificationDelivery nd
        WHERE nd.createdAt <= :cutoffDate
        AND nd.status IN ('SENT', 'DROPPED')
        """)
    void deleteOldDeliveries(@Param("cutoffDate") LocalDateTime cutoffDate);
}
