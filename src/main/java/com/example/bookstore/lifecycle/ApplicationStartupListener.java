package com.example.bookstore.lifecycle;

import com.example.bookstore.distributed.DistributedLockService;
import com.example.bookstore.sse.HeartbeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ApplicationStartupListener - Initialize distributed lock at startup
 *
 * ==============================================================================
 * PURPOSE
 * ==============================================================================
 *
 * When application finishes startup:
 *   1. Try to acquire the distributed lock
 *   2. If successful: enable queue worker for this instance
 *   3. If failed: run as standby (queue worker disabled)
 *   4. Log the outcome for operations monitoring
 *
 * ==============================================================================
 * STARTUP SEQUENCE (3-instance deployment)
 * ==============================================================================
 *
 * T=0s: Start 3 containers
 *   - app-1 starts, Spring loads, ApplicationReadyEvent fires
 *   - app-2 starts, Spring loads, ApplicationReadyEvent fires
 *   - app-3 starts, Spring loads, ApplicationReadyEvent fires
 *
 * T=3s: All try to acquire lock (via sp_acquire_queue_lock)
 *   - Database processes 3 concurrent lock acquisition attempts
 *   - First to execute wins (app-1 acquires lock)
 *   - app-2 and app-3 fail to acquire
 *
 * T=3s: app-1 enables queue worker
 *   - heartbeatService.enableQueueWorker()
 *   - Scheduled task processQueue() starts polling
 *   - Begins sending pending notifications
 *
 * T=3s: app-2, app-3 run as standby
 *   - Queue worker disabled
 *   - Still serve HTTP requests (health checks, SSE, etc.)
 *   - Still accept notifications via API (stored in DB)
 *   - Heartbeat still runs (sends heartbeat to clients)
 *
 * T=33s: Lock refresh (every 15s, first refresh at T=15s)
 *   - app-1 calls lockService.refreshLock()
 *   - Database extends lock expiry to T=63s
 *   - app-2, app-3 check refreshLock() in their heartbeat (disabled, so no-op)
 *
 * Scenario: app-1 crashes at T=40s
 *   - app-1 stops (no graceful shutdown)
 *   - Lock expiry was at T=63s
 *   - At T=63s, lock expires
 *   - app-2 heartbeat tries refreshLock() (no-op, disabled)
 *   - But next lock acquisition attempt will succeed for app-2
 *   - Either: app-2 detects lock available, or app-3 does
 *   - First to acquire wins, other stays standby
 *   - Max downtime: 30s (TTL) until failover
 *
 * ==============================================================================
 * LOCK ACQUISITION STRATEGIES
 * ==============================================================================
 *
 * Option 1: Eager acquisition (current implementation)
 *   - All instances try at startup
 *   - First wins, others become standby
 *   - Pro: Simple, clear winner
 *   - Con: Requires synchronization at startup
 *
 * Option 2: Scheduled retry (alternative)
 *   - app-1 acquires lock
 *   - app-2, app-3 retry every 5s
 *   - When app-1 dies, first retry from app-2/app-3 after 5s
 *   - Pro: Automatic failover without central coordination
 *   - Con: Wasted retry attempts
 *
 * ==============================================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationStartupListener {

    private final DistributedLockService lockService;
    private final HeartbeatService heartbeatService;

    /**
     * Called when Spring Boot application is ready to receive requests
     *
     * This is the right place to:
     *   - Acquire locks
     *   - Initialize cluster coordination
     *   - Start background workers
     *   - Check database connectivity
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        log.info("========================================");
        log.info("Application startup - initializing distributed system");
        log.info("========================================");

        try {
            // Step 1: Try to acquire distributed lock
            log.info("Attempting to acquire distributed lock...");
            String instanceId = lockService.getInstanceId();
            log.info("Instance ID: {}", instanceId);

            boolean lockAcquired = lockService.acquireLock();

            if (lockAcquired) {
                // Step 2a: Lock acquired - enable queue worker
                log.info("✓ Lock acquired - enabling queue worker");
                heartbeatService.enableQueueWorker();

                log.info("========================================");
                log.info("This instance is NOW the QUEUE WORKER");
                log.info("========================================");
                log.info("Processing notifications from queue...");

            } else {
                // Step 2b: Lock not acquired - run as standby
                log.info("✗ Lock not acquired - running as STANDBY");
                heartbeatService.disableQueueWorker();

                log.info("========================================");
                log.info("This instance is in STANDBY mode");
                log.info("Still serving HTTP requests and SSE connections");
                log.info("Will monitor for lock availability");
                log.info("========================================");
            }

            // Step 3: Print cluster status
            printClusterStatus();

        } catch (Exception e) {
            log.error("Error during startup initialization: {}", e.getMessage(), e);
            log.warn("Proceeding without queue worker enabled (running in safe mode)");
            heartbeatService.disableQueueWorker();
        }

        log.info("========================================");
        log.info("Application startup complete");
        log.info("========================================");
    }

    /**
     * Print cluster status for monitoring/debugging
     */
    private void printClusterStatus() {
        String lockHolder = lockService.getCurrentLockHolder().orElse("UNKNOWN");
        String instanceId = lockService.getInstanceId();
        boolean isWorker = heartbeatService.isQueueWorkerEnabled();

        log.info("Cluster Status:");
        log.info("  - Current instance: {}", instanceId);
        log.info("  - Queue worker role: {}", isWorker ? "ACTIVE" : "STANDBY");
        log.info("  - Lock holder: {}", lockHolder);
        log.info("  - Instance is lock holder: {}", instanceId.equals(lockHolder));
    }
}
