package com.example.bookstore.distributed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * Collection {@code distributed_locks} - "thue" (lease) phan tan cho queue worker.
 *
 * <p>SQL Server truoc day dung bang + 3 STORED PROCEDURE (V9/V10/V11) voi
 * UPDLOCK/HOLDLOCK de chong race condition. MongoDB khong can SP: dung
 * {@code findAndModify} - thao tac ATOMIC tren 1 document (xem
 * DistributedLockService.acquireLock).</p>
 */
@Document(collection = "distributed_locks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistributedLock {

    /** {@code _id} chinh la ten lock (vd: NOTIFICATION_QUEUE_WORKER). */
    @Id
    private String lockName;

    @Field("holderId")
    @Builder.Default
    private String holderId = "UNOWNED";

    private String instanceId;

    private LocalDateTime acquiredAt;
    private LocalDateTime heartbeatAt;

    /** TTL index: khoa het han tu dong duoc don (khong giu khoa "chet"). */
    private LocalDateTime expiresAt;
}
