-- ============================================================================
-- V11: Create sp_release_queue_lock stored procedure
-- ============================================================================
-- Usage from Java during graceful shutdown:
--   EXEC sp_release_queue_lock
--     @lock_name = 'NOTIFICATION_QUEUE_WORKER',
--     @instance_id = 'bookom-app-1'
--
-- Purpose: Release the lock so other instances can acquire it immediately
-- ============================================================================

CREATE PROCEDURE sp_release_queue_lock
    @lock_name NVARCHAR(100),
    @instance_id NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    
    UPDATE distributed_lock
    SET lock_holder_id = 'UNOWNED',
        lock_expires_at = SYSUTCDATETIME()
    WHERE lock_name = @lock_name
      AND lock_holder_id = @instance_id;
END;
