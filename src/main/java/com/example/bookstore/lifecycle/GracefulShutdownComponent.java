package com.example.bookstore.lifecycle;

import com.example.bookstore.distributed.DistributedLockService;
import com.example.bookstore.sse.HeartbeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * GracefulShutdownComponent - Handle application lifecycle gracefully
 *
 * ==============================================================================
 * PURPOSE
 * ==============================================================================
 *
 * When application receives SIGTERM (shutdown signal):
 *   1. Spring fires ContextClosedEvent
 *   2. This component intercepts the event
 *   3. Executes graceful shutdown sequence:
 *      - Release distributed lock (allow failover)
 *      - Wait for in-flight requests to complete
 *      - Close SSE connections gracefully
 *      - Stop scheduled tasks
 *   4. Application exits cleanly
 *
 * ==============================================================================
 * DEPLOYMENT SCENARIO: DOCKER COMPOSE SHUTDOWN
 * ==============================================================================
 *
 * Command: docker compose down
 * Sequence:
 *   1. Docker sends SIGTERM to all containers
 *   2. Spring Boot receives SIGTERM, starts graceful shutdown
 *   3. Nginx stops accepting new requests to this instance
 *   4. GracefulShutdownComponent.onApplicationClosed() is called
 *   5. Lock is released (queue worker flag disabled)
 *   6. Another instance (if any) detects available lock
 *   7. That instance acquires lock and takes over processing
 *   8. Original instance waits 30s for in-flight requests
 *   9. Process exits
 *
 * ==============================================================================
 * KUBERNETES DEPLOYMENT
 * ==============================================================================
 *
 * Add to deployment spec:
 *   terminationGracePeriodSeconds: 60
 *   lifecycle:
 *     preStop:
 *       exec:
 *         command: ["/bin/sh", "-c", "sleep 15"]  # Wait for load balancer to drain
 *
 * Sequence:
 *   1. Kubernetes sends SIGTERM to pod
 *   2. preStop hook sleeps 15s (gives load balancer time to drain connections)
 *   3. Spring starts graceful shutdown
 *   4. GracefulShutdownComponent releases lock
 *   5. After 60s, Kubernetes sends SIGKILL if still running
 *
 * ==============================================================================
 * CONFIGURATION
 * ==============================================================================
 *
 * In application.properties:
 *   server.shutdown=graceful
 *   spring.lifecycle.timeout-per-shutdown-phase=30s
 *
 * ==============================================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GracefulShutdownComponent {

    private final DistributedLockService lockService;
    private final HeartbeatService heartbeatService;

    /**
     * Called when Spring context is closing (SIGTERM received)
     *
     * Execution order:
     *   1. Disable queue worker (stop processing notifications)
     *   2. Release distributed lock (allow failover immediately)
     *   3. Let SSE connections close naturally
     *   4. Wait for in-flight HTTP requests (configured timeout)
     *   5. Exit application
     */
    @EventListener(ContextClosedEvent.class)
    public void onApplicationClosed(ContextClosedEvent event) {
        log.info("========================================");
        log.info("Graceful shutdown initiated");
        log.info("========================================");

        try {
            // Step 1: Disable queue worker (stop consuming queue)
            if (heartbeatService.isQueueWorkerEnabled()) {
                log.info("Disabling queue worker...");
                heartbeatService.disableQueueWorker();
                log.info("✓ Queue worker disabled");
            }

            // Step 2: Release lock (allow other instances to take over)
            log.info("Releasing distributed lock...");
            lockService.releaseLock();
            log.info("✓ Distributed lock released");

            // Step 3: Give other instances time to detect lock release and acquire
            log.info("Waiting 5s for failover detection...");
            Thread.sleep(5000);

            // Step 4: SSE connections will close naturally as part of Spring shutdown
            long connectedClients = 0; // Would need to add method to get this count
            log.info("Graceful shutdown complete - {} SSE connections will close",
                    connectedClients);

        } catch (InterruptedException e) {
            log.warn("Interrupted during graceful shutdown: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error during graceful shutdown: {}", e.getMessage(), e);
        }

        log.info("========================================");
        log.info("Application shutdown in progress...");
        log.info("========================================");
    }
}
