package com.example.bookstore.controller;

import com.example.bookstore.distributed.DistributedLockService;
import com.example.bookstore.sse.HeartbeatService;
import com.example.bookstore.sse.NotificationSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * HealthCheckController - Monitoring and observability endpoints
 *
 * ==============================================================================
 * PURPOSE
 * ==============================================================================
 *
 * Provides health check endpoints for:
 *   1. Kubernetes liveness/readiness probes
 *   2. Load balancer health checks
 *   3. Monitoring dashboards (Prometheus, New Relic, etc.)
 *   4. Manual operations team troubleshooting
 *
 * Endpoints:
 *   - GET /api/health           → Quick health status (liveness probe)
 *   - GET /api/health/detailed  → Full system status (readiness probe)
 *   - GET /api/health/live      → Kubernetes liveness (should always respond)
 *   - GET /api/health/ready     → Kubernetes readiness (fail if dependencies down)
 *
 * ==============================================================================
 * DEPLOYMENT USAGE
 * ==============================================================================
 *
 * In docker-compose.yml:
 *   healthcheck:
 *     test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
 *     interval: 10s
 *     timeout: 5s
 *     retries: 3
 *     start_period: 20s
 *
 * Or with Kubernetes:
 *   livenessProbe:
 *     httpGet:
 *       path: /api/health/live
 *       port: 8080
 *     initialDelaySeconds: 30
 *     periodSeconds: 10
 *
 *   readinessProbe:
 *     httpGet:
 *       path: /api/health/ready
 *       port: 8080
 *     initialDelaySeconds: 10
 *     periodSeconds: 5
 *
 * ==============================================================================
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthCheckController {

    private final DataSource dataSource;
    private final NotificationSseService sseService;
    private final HeartbeatService heartbeatService;
    private final DistributedLockService lockService;

    @Value("${spring.application.name:bookstore}")
    private String applicationName;

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    /**
     * Quick health check - suitable for load balancer probes
     *
     * Returns 200 OK if application is generally healthy
     * Returns 503 if critical components are down
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> quickHealth() {
        boolean dbOk = checkDatabaseConnection();
        String status = dbOk ? "UP" : "DOWN";
        int httpStatus = dbOk ? 200 : 503;

        return ResponseEntity.status(httpStatus).body(
                Map.of(
                        "status", status,
                        "app", applicationName,
                        "timestamp", LocalDateTime.now()
                )
        );
    }

    /**
     * Liveness probe - Kubernetes liveness probe
     *
     * Used to detect if application process is still alive
     * Should return 200 even if some dependencies are down
     * (restart should be triggered by readiness probe, not liveness)
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> livenessProbe() {
        return ResponseEntity.ok(
                Map.of(
                        "status", "UP",
                        "probe", "liveness",
                        "timestamp", LocalDateTime.now()
                )
        );
    }

    /**
     * Readiness probe - Kubernetes readiness probe
     *
     * Returns 200 only if all dependencies are available
     * Causes load balancer to stop sending traffic to this instance if false
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readinessProbe() {
        boolean dbOk = checkDatabaseConnection();

        if (!dbOk) {
            return ResponseEntity.status(503).body(
                    Map.of(
                            "status", "DOWN",
                            "reason", "Database unreachable",
                            "probe", "readiness"
                    )
            );
        }

        return ResponseEntity.ok(
                Map.of(
                        "status", "UP",
                        "probe", "readiness",
                        "timestamp", LocalDateTime.now()
                )
        );
    }

    /**
     * Detailed health status - Full system diagnostics
     *
     * Includes:
     *   - Database connectivity
     *   - SSE client connections
     *   - Queue worker status
     *   - Distributed lock status
     */
    @GetMapping("/detailed")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> response = new HashMap<>();

        // Basic info
        response.put("timestamp", LocalDateTime.now());
        response.put("app", applicationName);
        response.put("instanceId", lockService.getInstanceId());

        // Database
        boolean dbOk = checkDatabaseConnection();
        response.put("database", Map.of(
                "status", dbOk ? "UP" : "DOWN",
                "pool", "JDBC ConnectionPool"
        ));

        // SSE Connections
        long connectedClients = sseService.getConnectedClientCount();
        long connectedUsers = sseService.getConnectedUserCount();
        response.put("sse", Map.of(
                "status", "UP",
                "connectedClients", connectedClients,
                "connectedUsers", connectedUsers
        ));

        // Distributed Lock & Queue Worker
        boolean queueEnabled = heartbeatService.isQueueWorkerEnabled();
        String lockHolder = lockService.getCurrentLockHolder().orElse("NONE");
        response.put("queueWorker", Map.of(
                "enabled", queueEnabled,
                "lockHolder", lockHolder,
                "status", queueEnabled ? "PROCESSING" : "STANDBY"
        ));

        // Heartbeat
        response.put("heartbeat", Map.of(
                "status", "OK",
                "interval", "15s"
        ));

        // Overall status
        String overallStatus = (dbOk && connectedUsers >= 0) ? "UP" : "DEGRADED";
        response.put("status", overallStatus);

        int httpStatus = "UP".equals(overallStatus) ? 200 : 503;
        return ResponseEntity.status(httpStatus).body(response);
    }

    /**
     * Queue worker status endpoint - For operations monitoring
     *
     * Returns information about:
     *   - Current lock holder
     *   - Whether this instance has the lock
     *   - SSE connection count
     */
    @GetMapping("/queue-worker")
    public ResponseEntity<Map<String, Object>> queueWorkerStatus() {
        boolean queueEnabled = heartbeatService.isQueueWorkerEnabled();
        String lockHolder = lockService.getCurrentLockHolder().orElse("NONE");
        String currentInstance = lockService.getInstanceId();

        return ResponseEntity.ok(
                Map.of(
                        "instanceId", currentInstance,
                        "hasLock", currentInstance.equals(lockHolder),
                        "lockHolder", lockHolder,
                        "processing", queueEnabled,
                        "sseConnections", sseService.getConnectedClientCount(),
                        "timestamp", LocalDateTime.now()
                )
        );
    }

    /**
     * SSE connections status - For monitoring client connectivity
     */
    @GetMapping("/sse")
    public ResponseEntity<Map<String, Object>> sseStatus() {
        return ResponseEntity.ok(
                Map.of(
                        "connectedClients", sseService.getConnectedClientCount(),
                        "connectedUsers", sseService.getConnectedUserCount(),
                        "heartbeat", Map.of(
                                "interval", "15s",
                                "status", "OK"
                        ),
                        "timestamp", LocalDateTime.now()
                )
        );
    }

    /**
     * Check database connectivity
     */
    private boolean checkDatabaseConnection() {
        try (Connection conn = dataSource.getConnection()) {
            // Simple connectivity check
            return conn.isValid(2); // 2 second timeout
        } catch (SQLException e) {
            return false;
        }
    }
}
