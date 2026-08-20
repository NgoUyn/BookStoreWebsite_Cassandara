package com.example.bookstore.sse;

import com.example.bookstore.model.Notification;
import com.example.bookstore.model.NotificationDelivery;
import com.example.bookstore.repository.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * NotificationDeliveryQueue Service
 * 
 * ==============================================================================
 * PURPOSE & ARCHITECTURE
 * ==============================================================================
 * 
 * Handles resilient notification delivery with retry logic and backoff.
 * Manages the lifecycle from "notification created" → "notification sent successfully"
 * or "notification failed after retries".
 * 
 * Key Responsibilities:
 *   1. Create delivery records when notifications are created
 *   2. Poll DB queue for pending deliveries (every 1 second)
 *   3. Attempt SSE delivery via NotificationSseService
 *   4. On failure: calculate backoff, increment retry count, reschedule
 *   5. On success: mark as SENT, log success
 *   6. After MAX_ATTEMPTS: mark as DROPPED, log failure
 * 
 * ==============================================================================
 * DISTRIBUTED LOCK & MULTI-INSTANCE COORDINATION
 * ==============================================================================
 *
 * In a 3-instance deployment (Docker Compose with 3 replicas):
 *   - Only 1 instance should process the queue at a time
 *   - Use distributed lock to coordinate
 *   - Lock holder periodically refreshes to prove it's still alive
 *   - If lock holder crashes, another instance takes over
 *
 * Integration with HeartbeatService:
 *   - HeartbeatService checks isQueueWorkerEnabled() before processing
 *   - Only instance with lock has this flag set to true
 *   - If lock is lost, HeartbeatService disables queue worker
 *   - This processQueue() method will skip processing
 * 
 * ==============================================================================
 * WHY DB-BACKED QUEUE (NOT IN-MEMORY)
 * ==============================================================================
 * 
 * In-memory queue (first implementation):
 *   ✓ Simple, low latency
 *   ✗ Lost on app restart
 *   ✗ Can't share state between multiple Spring instances
 * 
 * DB-backed queue (production-ready):
 *   ✓ Survives app crashes/restarts
 *   ✓ Multiple instances can coordinate (shared DB)
 *   ✓ Audit trail: all attempts logged forever (or TTL)
 *   ✓ Manual intervention: ops can mark for retry, view failures
 *   ✓ Metrics: can query delivery statistics at any time
 *   ✗ Slightly higher latency (DB I/O vs in-memory)
 *   ✗ Requires careful indexing for performance at scale
 * 
 * ==============================================================================
 * RETRY & BACKOFF STRATEGY
 * ==============================================================================
 * 
 * When a delivery fails, we don't retry immediately. Instead, we use exponential
 * backoff to avoid:
 *   - Hammering a temporarily offline user (SSE connection lost)
 *   - Overwhelming the system with immediate retries
 *   - Creating a "thundering herd" problem (many retries at same time)
 * 
 * Backoff Schedule (Base = 2 seconds, Max Attempts = 5):
 *   Attempt 1: try at T=0s         → if fail, next at T=2s
 *   Attempt 2: try at T=2s         → if fail, next at T=6s (2+4)
 *   Attempt 3: try at T=6s         → if fail, next at T=14s (6+8)
 *   Attempt 4: try at T=14s        → if fail, next at T=30s (14+16)
 *   Attempt 5: try at T=30s        → if fail, next at T=62s (30+32)
 *   Attempt 6: would be at T=62s   → but we cap at 5, so DROPPED
 * 
 * ==============================================================================
 * QUEUE WORKER LIFECYCLE
 * ==============================================================================
 * 
 * @Scheduled(fixedDelay = 1000, initialDelay = 2000)
 *   runs every 1 second (1000 ms), starting 2s after app startup
 * 
 * processQueue():
 *   1. Check if queue worker is enabled (has lock)
 *   2. Query DB: SELECT * FROM notification_delivery 
 *      WHERE status='PENDING' AND next_retry_at <= NOW LIMIT 100
 *   3. For each task: attempt delivery, mark SENT or reschedule
 *   4. Commit all updates to DB atomically
 *   5. Loop again in 1 second
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationDeliveryQueue {
    
    // Maximum retry attempts before abandoning
    private static final int MAX_ATTEMPTS = 5;
    
    // Base backoff: 2 seconds (exponential multiplier applies on top)
    private static final long BASE_INTERVAL_MS = 2000;
    
    // Process 100 tasks per cycle to avoid long DB transactions
    private static final int BATCH_SIZE = 100;
    
    private final NotificationDeliveryRepository deliveryRepo;
    private final NotificationSseService sseService;
    private final HeartbeatService heartbeatService;
    
    /**
     * Enqueue a new delivery task
     * 
     * Called from: NotificationService.createNotification()
     * When: right after saving notification to DB
     * 
     * What it does:
     *   1. Create NotificationDelivery record in DB
     *   2. Set status=PENDING, next_retry_at=NOW (send immediately)
     *   3. Queue worker will pick up in next cycle (within 1 second)
     */
    public void enqueue(Notification notification, String channel) {
        log.debug("Enqueueing notification {} to channel {}", notification.getId(), channel);
        
        NotificationDelivery delivery = NotificationDelivery.builder()
            .notification(notification)
            .channel(channel)
            .status(NotificationDelivery.DeliveryStatus.PENDING)
            .attemptCount(0)
            .nextRetryAt(LocalDateTime.now())
            .build();
        
        deliveryRepo.save(delivery);
        log.debug("Notification {} enqueued with delivery id {}", notification.getId(), delivery.getId());
    }
    
    /**
     * Background worker: poll for pending deliveries and attempt sending
     * 
     * Scheduled: every 1 second (1000 ms fixed delay)
     * 
     * Behavior:
     *   1. Check if this instance has queue worker lock enabled
     *   2. If not enabled (running as standby): skip processing
     *   3. If enabled: Query pending deliveries ready to retry
     *   4. For each, attempt delivery via SSE
     *   5. On success: mark SENT
     *   6. On failure: reschedule with backoff or mark DROPPED
     * 
     * Error handling: catch all exceptions to prevent worker crash
     */
    @Scheduled(fixedDelay = 1000, initialDelay = 2000)
    public void processQueue() {
        // Check if this instance is the queue worker (has distributed lock)
        if (!heartbeatService.isQueueWorkerEnabled()) {
            // Not the worker, skip processing
            return;
        }
        
        try {
            Pageable batchLimit = PageRequest.of(0, BATCH_SIZE);
            List<NotificationDelivery> pendingTasks = 
                deliveryRepo.findPendingRetries(batchLimit);
            
            if (pendingTasks.isEmpty()) {
                return;  // queue empty or nothing ready yet
            }
            
            log.debug("Processing {} pending delivery tasks", pendingTasks.size());
            
            for (NotificationDelivery task : pendingTasks) {
                processDeliveryTask(task);
            }
            
        } catch (Exception e) {
            log.error("Queue worker error: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Process single delivery task with retry/backoff logic
     */
    private void processDeliveryTask(NotificationDelivery task) {
        try {
            log.debug("Processing delivery {} for notification {}, attempt {}/{}",
                      task.getId(),
                      task.getNotification().getId(),
                      task.getAttemptCount(),
                      MAX_ATTEMPTS);
            
            Long userId = task.getNotification().getUser().getId();
            
            // Attempt delivery via SSE
            boolean delivered = sseService.sendEventToUser(
                userId,
                "notification",
                task.getNotification()
            );
            
            if (delivered) {
                // SUCCESS: Mark as SENT
                task.setStatus(NotificationDelivery.DeliveryStatus.SENT);
                task.setSentAt(LocalDateTime.now());
                task.setNextRetryAt(null);
                
                deliveryRepo.save(task);
                
                log.info("✓ Notification {} delivered to user {} via {} at attempt {}",
                         task.getNotification().getId(),
                         userId,
                         task.getChannel(),
                         task.getAttemptCount() + 1);
                
            } else {
                // FAILURE: Check if should retry or give up
                int nextAttemptCount = task.getAttemptCount() + 1;
                
                if (nextAttemptCount < MAX_ATTEMPTS) {
                    // Reschedule with exponential backoff
                    LocalDateTime nextRetryAt = calculateNextRetryTime(nextAttemptCount);
                    
                    task.setAttemptCount(nextAttemptCount);
                    task.setNextRetryAt(nextRetryAt);
                    task.setLastError("Delivery failed (user offline or SSE error)");
                    // status remains PENDING
                    
                    deliveryRepo.save(task);
                    
                    long backoffMs = calculateBackoffMs(nextAttemptCount);
                    log.warn("⚠ Notification {} delivery failed, retry {} in {} ms",
                             task.getNotification().getId(),
                             nextAttemptCount,
                             backoffMs);
                    
                } else {
                    // GIVE UP: Exceeded MAX_ATTEMPTS
                    task.setStatus(NotificationDelivery.DeliveryStatus.DROPPED);
                    task.setAttemptCount(nextAttemptCount);
                    task.setLastError("Abandoned after " + MAX_ATTEMPTS + " attempts");
                    task.setNextRetryAt(null);
                    
                    deliveryRepo.save(task);
                    
                    log.error("✗ Notification {} DROPPED after {} attempts for user {}",
                              task.getNotification().getId(),
                              MAX_ATTEMPTS,
                              userId);
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing delivery task {}: {}",
                      task.getId(), e.getMessage(), e);
            
            task.setLastError("Worker exception: " + e.getMessage());
            task.setAttemptCount(task.getAttemptCount() + 1);
            
            if (task.getAttemptCount() < MAX_ATTEMPTS) {
                task.setNextRetryAt(calculateNextRetryTime(task.getAttemptCount()));
            } else {
                task.setStatus(NotificationDelivery.DeliveryStatus.DROPPED);
                task.setNextRetryAt(null);
            }
            
            deliveryRepo.save(task);
        }
    }
    
    /**
     * Calculate backoff interval for next retry
     * 
     * Formula: backoff_ms = BASE_INTERVAL_MS * (2 ^ (attemptCount - 1))
     * 
     * Examples:
     *   attemptCount=1 → 2000 * 2^0 = 2s
     *   attemptCount=2 → 2000 * 2^1 = 4s
     *   attemptCount=3 → 2000 * 2^2 = 8s
     *   attemptCount=4 → 2000 * 2^3 = 16s
     *   attemptCount=5 → 2000 * 2^4 = 32s
     */
    private long calculateBackoffMs(int attemptCount) {
        int exponent = Math.min(attemptCount - 1, 20);  // cap to avoid overflow
        return BASE_INTERVAL_MS * (1L << exponent);  // 1L << exponent = 2^exponent
    }
    
    /**
     * Calculate absolute time for next retry: NOW + backoff_ms
     */
    private LocalDateTime calculateNextRetryTime(int attemptCount) {
        long backoffMs = calculateBackoffMs(attemptCount);
        return LocalDateTime.now().plusNanos(backoffMs * 1_000_000);  // ms to ns
    }
}
