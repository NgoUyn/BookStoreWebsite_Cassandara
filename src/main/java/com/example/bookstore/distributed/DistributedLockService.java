package com.example.bookstore.distributed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final DistributedLockRepository lockRepository;

    @Value("${distributed.lock.enabled:true}")
    private boolean lockEnabled;

    @Value("${distributed.lock.name:NOTIFICATION_QUEUE_WORKER}")
    private String lockName;

    @Value("${distributed.lock.ttl-seconds:30}")
    private int lockTtlSeconds;

    @Value("${spring.application.name:bookstore-app}")
    private String applicationName;

    public String getInstanceId() {
        String hostname = System.getenv("DOCKER_HOSTNAME");
        if (hostname != null && !hostname.isBlank()) return hostname;
        String containerName = System.getenv("HOSTNAME");
        if (containerName != null && !containerName.isBlank()) return containerName;
        return applicationName + "-" + ProcessHandle.current().pid();
    }

    @Transactional
    public boolean acquireLock() {
        if (!lockEnabled) return true;

        // Đảm bảo bản ghi lock luôn tồn tại trong DB trống (Idempotent initialization)
        if (!lockRepository.existsById(lockName)) {
            try {
                DistributedLock initialLock = new DistributedLock(lockName);
                // Bơm thêm các thông số bắt buộc để vượt qua ải NOT NULL
                initialLock.setInstanceId("UNOWNED");
                initialLock.setLockHolderId("UNOWNED");
                initialLock.setAcquiredAt(LocalDateTime.now()); // Chìa khóa fix lỗi đây bro
                initialLock.setLockExpiresAt(LocalDateTime.now());

                lockRepository.save(initialLock);
            } catch (Exception e) {
                // Phòng trường hợp instance khác đã nhanh tay insert trước
                log.debug("Lock row already initialized by another instance.");
            }
        }
        String instanceId = getInstanceId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(lockTtlSeconds);

        int updatedRows = lockRepository.acquireLock(lockName, instanceId, expiresAt, now);
        boolean acquired = updatedRows > 0;

        if (acquired) {
            log.info("✓ Distributed lock acquired by instance: {}", instanceId);
        } else {
            log.info("✗ Distributed lock NOT acquired (held by another instance)");
        }
        return acquired;
    }

    @Transactional
    public boolean refreshLock() {
        if (!lockEnabled) return true;

        String instanceId = getInstanceId();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(lockTtlSeconds);

        int updatedRows = lockRepository.refreshLock(lockName, instanceId, expiresAt);
        boolean refreshed = updatedRows > 0;

        if (!refreshed) {
            log.warn("⚠ Lost distributed lock - another instance acquired it");
        }
        return refreshed;
    }

    @Transactional
    public void releaseLock() {
        if (!lockEnabled) return;
        String instanceId = getInstanceId();
        lockRepository.releaseLock(lockName, instanceId, LocalDateTime.now());
        log.info("✓ Distributed lock released by instance: {}", instanceId);
    }

    public Optional<String> getCurrentLockHolder() {
        if (!lockEnabled) return Optional.empty();
        return lockRepository.findById(lockName)
                .map(lock -> "UNOWNED".equals(lock.getLockHolderId()) ? null : lock.getLockHolderId());
    }
}