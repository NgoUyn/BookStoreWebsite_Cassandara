-- ============================================================================
-- V19: Add version column to sub_orders table for Hibernate Optimistic Locking
-- ============================================================================
-- This migration fixes the NullPointerException in Hibernate's Versioning.increment
-- caused by missing version column in the database schema.
--
-- Root cause:
--   - SubOrder.java entity has @Version private Long version
--   - The database schema (V14) did not include a version column
--   - Hibernate's ddl-auto=update added the column but existing rows have NULL
--   - When updating a SubOrder, Hibernate tries to increment NULL → NPE
--
-- Fix:
--   1. Add version column if not exists (idempotent)
--   2. Set version = 0 for all existing rows where version is NULL
-- ============================================================================

-- 1. Thêm cột version nếu chưa tồn tại
IF NOT EXISTS (SELECT * FROM sys.columns 
               WHERE object_id = OBJECT_ID(N'[dbo].[sub_orders]') 
               AND name = 'version')
BEGIN
    ALTER TABLE sub_orders ADD version BIGINT NOT NULL DEFAULT 0;
END
GO

-- 2. Cập nhật các dòng cũ có version = NULL thành 0 (an toàn nếu cột đã tồn tại)
UPDATE sub_orders SET version = 0 WHERE version IS NULL;
GO
