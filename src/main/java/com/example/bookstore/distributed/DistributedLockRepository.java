package com.example.bookstore.distributed;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository collection distributed_locks.
 *
 * <p>Ban SQL Server dung 3 stored procedure (acquire/refresh/release) voi
 * UPDLOCK/HOLDLOCK de tranh race condition. MongoDB khong can stored procedure:
 * cac thao tac nguyen tu duoc thuc hien bang {@code findAndModify} trong
 * {@link DistributedLockService} (xem method acquireLock/refreshLock/releaseLock).</p>
 */
@Repository
public interface DistributedLockRepository extends MongoRepository<DistributedLock, String> {

    Optional<DistributedLock> findByLockName(String lockName);

    long countByHolderIdAndExpiresAtAfter(String holderId, LocalDateTime now);

    long countByInstanceId(String instanceId);
}
