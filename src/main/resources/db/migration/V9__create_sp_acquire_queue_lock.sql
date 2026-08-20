-- ============================================================================
-- V9: Create sp_acquire_queue_lock stored procedure
-- ============================================================================
-- Usage from Java:
--   EXEC sp_acquire_queue_lock 
--     @lock_name = 'NOTIFICATION_QUEUE_WORKER',
--     @instance_id = 'bookom-app-1',
--     @ttl_seconds = 30,
--     @acquired = @result OUT
--
-- Returns: 1 if lock acquired, 0 if already held by another instance
-- ============================================================================

CREATE PROCEDURE sp_acquire_queue_lock
    @lock_name NVARCHAR(100),
    @instance_id NVARCHAR(255),
    @ttl_seconds INT,
    @acquired BIT OUT
AS
BEGIN
    SET NOCOUNT ON;
    SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
    
    BEGIN TRANSACTION;
    
    DECLARE @current_expires DATETIME2;
    DECLARE @now DATETIME2 = SYSUTCDATETIME();
    DECLARE @new_expires DATETIME2 = DATEADD(SECOND, @ttl_seconds, @now);
    
    -- Check if lock is available (expired or unowned)
    SELECT @current_expires = lock_expires_at FROM distributed_lock WITH (UPDLOCK)
    WHERE lock_name = @lock_name;
    
    IF @current_expires IS NULL
    BEGIN
        -- Lock record doesn't exist, insert it
        INSERT INTO distributed_lock (lock_name, lock_holder_id, acquired_at, lock_expires_at, last_heartbeat_at)
        VALUES (@lock_name, @instance_id, @now, @new_expires, @now);
        
        SET @acquired = 1;
    END
    ELSE IF @current_expires <= @now
    BEGIN
        -- Lock has expired, acquire it
        UPDATE distributed_lock
        SET lock_holder_id = @instance_id,
            acquired_at = @now,
            lock_expires_at = @new_expires,
            last_heartbeat_at = @now
        WHERE lock_name = @lock_name;
        
        SET @acquired = 1;
    END
    ELSE
    BEGIN
        -- Lock is held by someone else and not expired
        SET @acquired = 0;
    END
    
    COMMIT TRANSACTION;
END;
