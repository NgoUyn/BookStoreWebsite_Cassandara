package com.example.bookstore.distributed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface DistributedLockRepository extends JpaRepository<DistributedLock, String> {

    @Modifying
    @Query("UPDATE DistributedLock l SET l.lockHolderId = :instanceId, l.lockExpiresAt = :expiresAt " +
            "WHERE l.lockName = :lockName AND (l.lockHolderId = 'UNOWNED' OR l.lockExpiresAt < :now)")
    int acquireLock(@Param("lockName") String lockName,
                    @Param("instanceId") String instanceId,
                    @Param("expiresAt") LocalDateTime expiresAt,
                    @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE DistributedLock l SET l.lockExpiresAt = :expiresAt " +
            "WHERE l.lockName = :lockName AND l.lockHolderId = :instanceId")
    int refreshLock(@Param("lockName") String lockName,
                    @Param("instanceId") String instanceId,
                    @Param("expiresAt") LocalDateTime expiresAt);

    @Modifying
    @Query("UPDATE DistributedLock l SET l.lockHolderId = 'UNOWNED', l.lockExpiresAt = :now " +
            "WHERE l.lockName = :lockName AND l.lockHolderId = :instanceId")
    void releaseLock(@Param("lockName") String lockName,
                     @Param("instanceId") String instanceId,
                     @Param("now") LocalDateTime now);
}