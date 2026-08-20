-- ============================================================================
-- V22: Add instance_id to distributed_lock
-- ============================================================================
-- Purpose:
--   Align the database schema with the current DistributedLock entity, which
--   maps instance_id as a required column.
--
-- Notes:
--   - Safe for existing databases that were created before instance_id existed.
--   - Backfills existing rows from lock_holder_id so historical records remain
--     meaningful on SQL Server.
--   - Keeps the migration idempotent where practical for drifted environments.
-- ============================================================================

-- [1] TẠO CỘT lock_holder_id TRƯỚC ĐỂ FIX LỖI "INVALID COLUMN NAME"
IF COL_LENGTH('dbo.distributed_lock', 'lock_holder_id') IS NULL
BEGIN
ALTER TABLE distributed_lock
    ADD lock_holder_id NVARCHAR(255) NULL;
END
GO

-- [2] TẠO CỘT instance_id (Giữ nguyên code của bro)
IF COL_LENGTH('dbo.distributed_lock', 'instance_id') IS NULL
BEGIN
ALTER TABLE distributed_lock
    ADD instance_id NVARCHAR(255) NULL;
END
GO

-- [3] UPDATE DATA (Lúc này cả 2 cột đều đã chắc chắn tồn tại)
UPDATE distributed_lock
SET instance_id = COALESCE(NULLIF(LTRIM(RTRIM(lock_holder_id)), ''), 'UNOWNED')
WHERE instance_id IS NULL OR LTRIM(RTRIM(instance_id)) = '';
GO

-- [4] ĐỔI THÀNH NOT NULL (Giữ nguyên code của bro)
IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.distributed_lock')
      AND name = 'instance_id'
      AND is_nullable = 1
)
BEGIN
ALTER TABLE distributed_lock
ALTER COLUMN instance_id NVARCHAR(255) NOT NULL;
END
GO

-- [5] ĐÁNH INDEX (Giữ nguyên code của bro)
IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_distributed_lock_instance_id'
      AND object_id = OBJECT_ID(N'dbo.distributed_lock')
)
BEGIN
CREATE INDEX IX_distributed_lock_instance_id
    ON distributed_lock(instance_id);
END
GO

-- ============================================================================
-- END V22
-- ============================================================================