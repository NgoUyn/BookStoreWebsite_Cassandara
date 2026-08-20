-- ============================================================================
-- V23: Thêm cột created_at vào bảng users
-- ============================================================================
-- Cần cho ML feature: account_age_months
-- ============================================================================

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'[dbo].[users]')
    AND name = 'created_at'
)
BEGIN
    ALTER TABLE users ADD created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME();
    PRINT 'Đã thêm cột created_at vào bảng users';
END
ELSE
BEGIN
    PRINT 'Cột created_at đã tồn tại, bỏ qua';
END
GO

-- ============================================================================
-- END V23
-- ============================================================================
