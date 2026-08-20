-- ============================================================================
-- V10: Create sp_refresh_queue_lock stored procedure
-- ============================================================================
-- Usage from Java:
--   EXEC sp_refresh_queue_lock
--     @lock_name = 'NOTIFICATION_QUEUE_WORKER',
--     @instance_id = 'bookom-app-1',
--     @ttl_seconds = 30,
--     @refreshed = @result OUT
--
-- Returns: 1 if successfully refreshed (you still own lock),
--          0 if someone else owns lock (you've lost it, stop processing)
-- ============================================================================

CREATE PROCEDURE sp_refresh_queue_lock
    @lock_name NVARCHAR(100),
    @instance_id NVARCHAR(255),
    @ttl_seconds INT,
    @refreshed BIT OUT
AS
BEGIN
    SET NOCOUNT ON;
    
    DECLARE @now DATETIME2 = SYSUTCDATETIME();
    DECLARE @new_expires DATETIME2 = DATEADD(SECOND, @ttl_seconds, @now);
    
    -- Only refresh if we still own the lock
    UPDATE distributed_lock
    SET lock_expires_at = @new_expires,
        last_heartbeat_at = @now
    WHERE lock_name = @lock_name
      AND lock_holder_id = @instance_id;
    
    -- Check if update succeeded (we own lock)
    IF @@ROWCOUNT > 0
    BEGIN
        SET @refreshed = 1;
    END
    ELSE
    BEGIN
        SET @refreshed = 0;
    END
END;
