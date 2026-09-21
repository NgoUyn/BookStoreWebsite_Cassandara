package com.example.bookstore.distributed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * KHOA PHAN TAN (lease) cho queue worker - phien ban MongoDB.
 *
 * <p>So sanh ban SQL Server: truoc day phai tao bang {@code distributed_lock}
 * + 3 STORED PROCEDURE (V9/V10/V11) dung {@code UPDLOCK/HOLDLOCK} de tranh
 * race condition. MongoDB KHONG CAN stored procedure: {@code findAndModify} la
 * thao tac ATOMIC tren mot document, dam bao chi 1 instance lay duoc khoa.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final DistributedLockRepository lockRepository;
    private final MongoTemplate mongoTemplate;

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
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        String containerName = System.getenv("HOSTNAME");
        if (containerName != null && !containerName.isBlank()) {
            return containerName;
        }
        return applicationName + "-" + ProcessHandle.current().pid();
    }

    /**
     * Lay khoa bang findAndModify (atomic): chi thanh cong khi khoa dang
     * "UNOWNED" hoac da het han; upsert=true de tu tao ban ghi khoa lan dau.
     */
    @Transactional
    public boolean acquireLock() {
        if (!lockEnabled) {
            return true;
        }
        String instanceId = getInstanceId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(lockTtlSeconds);

        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(lockName),
                new Criteria().orOperator(
                        Criteria.where("holderId").is("UNOWNED"),
                        Criteria.where("expiresAt").lte(now))));

        Update update = new Update()
                .set("holderId", instanceId)
                .set("instanceId", instanceId)
                .set("acquiredAt", now)
                .set("heartbeatAt", now)
                .set("expiresAt", expiresAt);

        DistributedLock lock = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true).upsert(true), DistributedLock.class);

        boolean acquired = lock != null && instanceId.equals(lock.getHolderId());
        if (acquired) {
            log.info("✓ Distributed lock acquired by instance: {}", instanceId);
        } else {
            log.info("✗ Distributed lock NOT acquired (held by another instance)");
        }
        return acquired;
    }

    /** Gia han khoa - chi khi CHINH MINH dang giu (atomic filter). */
    @Transactional
    public boolean refreshLock() {
        if (!lockEnabled) {
            return true;
        }
        String instanceId = getInstanceId();
        LocalDateTime now = LocalDateTime.now();

        Query query = Query.query(Criteria.where("_id").is(lockName).and("holderId").is(instanceId));
        Update update = new Update()
                .set("expiresAt", now.plusSeconds(lockTtlSeconds))
                .set("heartbeatAt", now);

        boolean refreshed = mongoTemplate.updateFirst(query, update, DistributedLock.class)
                .getModifiedCount() > 0;

        if (!refreshed) {
            log.warn("⚠ Lost distributed lock - another instance acquired it");
        }
        return refreshed;
    }

    /** Tra khoa: dat holderId ve UNOWNED (chi khi minh dang giu). */
    @Transactional
    public void releaseLock() {
        if (!lockEnabled) {
            return;
        }
        String instanceId = getInstanceId();
        Query query = Query.query(Criteria.where("_id").is(lockName).and("holderId").is(instanceId));
        Update update = new Update()
                .set("holderId", "UNOWNED")
                .set("expiresAt", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, DistributedLock.class);
        log.info("✓ Distributed lock released by instance: {}", instanceId);
    }

    public Optional<String> getCurrentLockHolder() {
        if (!lockEnabled) {
            return Optional.empty();
        }
        return lockRepository.findByLockName(lockName)
                .map(lock -> "UNOWNED".equals(lock.getHolderId()) ? null : lock.getHolderId());
    }
}
