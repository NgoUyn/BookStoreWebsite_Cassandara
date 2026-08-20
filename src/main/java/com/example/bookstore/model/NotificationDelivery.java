package com.example.bookstore.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * NotificationDelivery Entity
 * 
 * Purpose:
 *   Track each delivery attempt for a notification.
 *   Enables retry logic, audit trail, and multi-instance queue coordination.
 * 
 * Schema:
 *   Maps to notification_delivery table created by V7 migration
 *   One row per notification-user-channel combination
 * 
 * Lifecycle:
 *   1. Created when notification is created (status=PENDING, next_retry_at=NOW)
 *   2. Updated each retry (attempt_count++, next_retry_at += backoff)
 *   3. Marked SENT when delivery succeeds (sent_at=NOW, status=SENT)
 *   4. Marked DROPPED when max retries exceeded (status=DROPPED)
 * 
 * Key Design Decisions:
 *   - Separate table from Notification (not a column) because:
 *     * Multiple channels per notification (SSE, email, push)
 *     * Multiple retry attempts need history
 *     * Reduces notification table bloat
 *   - Immutable foreign key (notification_id never changes)
 *   - Status enum (not string) to prevent typos
 *   - Use @PrePersist/@PreUpdate for audit timestamps
 */
@Entity
@Table(name = "notification_delivery", indexes = {
    // Index 1: For queue worker to poll pending retries
    @Index(name = "IX_notification_delivery_pending_retry", 
           columnList = "status, next_retry_at",
           unique = false),
    // Index 2: For audit trail queries (find all attempts for a notification)
    @Index(name = "IX_notification_delivery_by_notification",
           columnList = "notification_id, created_at DESC",
           unique = false),
    // Index 3: For failed delivery investigation
    @Index(name = "IX_notification_delivery_failed",
           columnList = "status, created_at DESC",
           unique = false),
    // Index 4: For channel health monitoring
    @Index(name = "IX_notification_delivery_channel",
           columnList = "delivery_channel, created_at DESC, status",
           unique = false)
})
@Data
@ToString(exclude = {"notification"})
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDelivery {
    
    /**
     * Surrogate key: unique ID for this delivery record
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Foreign key to parent Notification
     * Why NOT NULL? Each delivery record MUST reference a notification
     * Cascade DELETE: if notification deleted, all delivery records deleted too
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false, 
                foreignKey = @ForeignKey(name = "FK_notification_delivery_notification"))
    private Notification notification;
    
    /**
     * Delivery channel: SSE, EMAIL, PUSH, SMS
     * 
     * Why use String instead of Enum?
     *   Flexibility to add new channels without migration
     *   But still validate in service layer
     * 
     * Supported values:
     *   - SSE: Server-Sent Events (browser real-time)
     *   - EMAIL: Email notification (future)
     *   - PUSH: Mobile push notification (future)
     *   - SMS: SMS text message (future)
     */
    @Column(name = "delivery_channel", length = 50, nullable = false)
    private String channel;
    
    /**
     * Delivery status: tracks current state in retry lifecycle
     * 
     * Transitions:
     *   PENDING → (success) → SENT
     *   PENDING → (fail) → PENDING [with backoff] → ... → DROPPED
     * 
     * Queue worker behavior:
     *   - Poll: SELECT * WHERE status='PENDING' AND next_retry_at <= NOW
     *   - After success: UPDATE status='SENT', sent_at=NOW
     *   - After failure: UPDATE attempt_count++, next_retry_at=NOW + backoff
     *   - After MAX_ATTEMPTS: UPDATE status='DROPPED'
     */
    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;
    
    /**
     * When this notification was actually delivered (NULL until success)
     * 
     * Purpose:
     *   - Track delivery latency (sent_at - notification.created_at)
     *   - Audit trail: when was user notified?
     * 
     * Usage:
     *   SELECT AVG(DATEDIFF(ms, notification.created_at, notification_delivery.sent_at))
     *   FROM notification_delivery
     *   WHERE created_at >= @start_date
     */
    @Column(name = "sent_at", nullable = true)
    private LocalDateTime sentAt;
    
    /**
     * Exception message from last failed delivery attempt
     * 
     * Examples:
     *   - "Connection reset by peer"
     *   - "SseEmitter not connected"
     *   - "Email service timeout"
     * 
     * Purpose:
     *   - Debugging: ops can investigate failed deliveries
     *   - Alerting: pattern matching for specific failure types
     * 
     * Limitation:
     *   Limited to 500 chars (truncated if longer)
     */
    @Column(name = "last_error", length = 500)
    private String lastError;
    
    /**
     * Number of delivery attempts made
     * 
     * Lifecycle:
     *   - Created: attempt_count = 0
     *   - First attempt fails: attempt_count = 1, next_retry_at = NOW + 2s
     *   - Second attempt fails: attempt_count = 2, next_retry_at = NOW + 4s
     *   - ...up to 5th attempt...
     *   - After 5 attempts: status=DROPPED, attempt_count=5 (final)
     * 
     * Purpose:
     *   - Queue worker checks: if (attempt_count >= MAX_ATTEMPTS) drop
     *   - Dashboard shows: which deliveries have high retry counts
     *   - Metrics: avg attempts per delivery
     */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;
    
    /**
     * When to retry this delivery (NULL if sent or dropped)
     * 
     * Purpose:
     *   - Guide queue worker: only process rows where next_retry_at <= NOW
     *   - Avoid busy-waiting: worker sleeps between poll cycles
     * 
     * Backoff schedule (exponential):
     *   Attempt 1: immediate (next_retry_at = NOW)
     *   Attempt 2: NOW + 2s
     *   Attempt 3: NOW + 4s
     *   Attempt 4: NOW + 8s
     *   Attempt 5: NOW + 16s
     *   Attempt 6: NOW + 32s
     *   After Attempt 6: drop (MAX_ATTEMPTS=5 means max 6 total attempts: 0-indexed)
     * 
     * Formula:
     *   next_retry_at = NOW + (BASE_INTERVAL_MILLIS * (2 ^ (attemptCount - 1)))
     *   next_retry_at = NOW + (2000 * (2 ^ (attempt - 1)))
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;
    
    /**
     * When this delivery record was created
     * 
     * Purpose:
     *   - Audit trail: when was notification queued for delivery?
     *   - Latency calculation: sent_at - created_at = delivery delay
     *   - Index scan: find recent deliveries efficiently
     * 
     * Auto-set: @PrePersist
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * When this delivery record was last modified
     * 
     * Purpose:
     *   - Audit trail: track when retry attempts happened
     *   - Detect stale records: if updated_at too old and still PENDING → investigate
     * 
     * Auto-set: @PrePersist and @PreUpdate
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Lifecycle: Set created_at and updated_at before insert
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    /**
     * Lifecycle: Update updated_at before every update
     * Why important?
     *   Allows DBAs to detect stale records (updated_at much older than NOW)
     *   Indicates potential queue worker failure
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Enum: Delivery status codes
     * 
     * Design:
     *   Using @Enumerated(EnumType.STRING) to store text names in DB
     *   Easier to debug than numeric codes
     */
    public enum DeliveryStatus {
        /**
         * Awaiting first send attempt or retry
         * Queue worker actively processes these
         */
        PENDING,
        
        /**
         * Successfully delivered
         * Terminal state: no more processing
         */
        SENT,
        
        /**
         * Failed after max retries
         * Terminal state: manually reviewable in dashboard
         */
        FAILED,
        
        /**
         * Abandoned after exhausting retries
         * Terminal state: same as FAILED, but explicitly marked "give up"
         */
        DROPPED
    }
}
