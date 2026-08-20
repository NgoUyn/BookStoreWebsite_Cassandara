package com.example.bookstore.sse;

import com.example.bookstore.distributed.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * HeartbeatService - Keep SSE connections alive and refresh distributed lock
 *
 * ==============================================================================
 * PURPOSE
 * ==============================================================================
 *
 * SSE connections can be closed by proxies/firewalls if no data is sent for a
 * while. This service:
 *
 *   1. Sends periodic heartbeat events to all connected SSE clients
 *   2. Refreshes the distributed lock (if this instance is queue worker)
 *   3. Detects if this instance lost the lock
 *   4. Stops queue processing if lock is lost
 *
 * ==============================================================================
 * HEARTBEAT PROTOCOL
 * ==============================================================================
 *
 * Event name: "heartbeat"
 * Event data: { "timestamp": 1234567890, "status": "ok" }
 * Sent every: 15 seconds (configurable)
 * TTL: SSE connection must receive heartbeat within 30 seconds to stay alive
 *
 * Client-side (JavaScript):
 *   eventSource.addEventListener('heartbeat', (e) => {
 *       console.log('Server is alive:', e.data);
 *   });
 *
 * ==============================================================================
 * DISTRIBUTED LOCK REFRESH
 * ==============================================================================
 *
 * If this instance holds the queue worker lock:
 *   - Calls lockService.refreshLock()
 *   - If refresh succeeds: continue processing
 *   - If refresh fails: another instance acquired lock, stop processing
 *   - If lock is lost: disableQueueWorker() is called
 *
 * This ensures:
 *   - Only 1 instance processes notifications
 *   - Failover when lock holder crashes
 *   - Graceful switchover when instance shuts down
 *
 * ==============================================================================
 * DEPLOYMENT
 * ==============================================================================
 *
 * In docker-compose.yml:
 *   - 3 app replicas: bookom-app-1, app-2, app-3
 *   - nginx load balancer routes HTTP traffic
 *   - SSE connections from clients go to any replica (sticky session needed)
 *   - Only 1 replica processes notification queue (distributed lock)
 *
 * Heartbeat flow:
 *   - Nginx sends SSE subscription request to one replica
 *   - All replicas maintain SSE connections from their clients
 *   - Every 15s, each replica sends heartbeat to its connected clients
 *   - This keeps connections alive even if no notifications are sent
 *
 * ==============================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatService {

    private final NotificationSseService sseService;
    private final DistributedLockService lockService;

    // Flag: whether this instance should process queue
    private volatile boolean queueWorkerEnabled = false;

    /**
     * Scheduled heartbeat - runs every 15 seconds
     *
     * 1. Sends heartbeat to all SSE clients
     * 2. Refreshes lock for queue worker
     * 3. Detects lock loss
     */
    @Scheduled(fixedDelay = 15000, initialDelay = 5000)
    public void sendHeartbeat() {
        try {
            // Send heartbeat to all connected clients
            long now = System.currentTimeMillis() / 1000;
            boolean sent = sseService.sendEventToAll(
                    "heartbeat",
                    Map.of(
                            "timestamp", now,
                            "status", "ok"
                    )
            );

            if (sent) {
                log.debug("✓ Heartbeat sent to {} connected client(s)", countConnectedClients());
            }

            // If this instance is the queue worker, refresh lock
            if (queueWorkerEnabled) {
                refreshQueueLock();
            }

        } catch (Exception e) {
            log.error("Error in heartbeat service: {}", e.getMessage(), e);
        }
    }

    /**
     * Initialize queue worker flag (called at startup after lock acquisition)
     */
    public void enableQueueWorker() {
        this.queueWorkerEnabled = true;
        log.info("✓ Queue worker ENABLED for this instance");
    }

    /**
     * Disable queue worker (called when lock is lost or on shutdown)
     */
    public void disableQueueWorker() {
        this.queueWorkerEnabled = false;
        log.warn("✗ Queue worker DISABLED - lock lost to another instance");
    }

    /**
     * Check if queue worker is enabled
     */
    public boolean isQueueWorkerEnabled() {
        return queueWorkerEnabled;
    }

    /**
     * Refresh the distributed lock
     *
     * Called every heartbeat cycle
     * If lock is lost, queue worker is disabled
     */
    private void refreshQueueLock() {
        if (!lockService.refreshLock()) {
            // Lock was lost to another instance
            disableQueueWorker();
            log.error("⚠ Queue worker lock lost to another instance - stopping queue processing");
        }
    }

    /**
     * Count connected clients (for metrics/monitoring)
     *
     * This is a best-effort count - may not be 100% accurate due to race conditions
     */
    private long countConnectedClients() {
        // Could implement a more precise counter in NotificationSseService if needed
        return sseService.getConnectedClientCount();
    }

    /**
     * Get status for health check endpoint
     */
    public Map<String, Object> getStatus() {
        return Map.of(
                "queueWorkerEnabled", queueWorkerEnabled,
                "connectedClients", countConnectedClients(),
                "currentLockHolder", lockService.getCurrentLockHolder().orElse("UNKNOWN")
        );
    }
}
