-- ============================================================================
-- V24: Tạo bảng support_tickets
-- ============================================================================
-- Cần cho ML feature: customer_support_tickets
-- Mỗi ticket là một yêu cầu hỗ trợ từ khách hàng
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[support_tickets]') AND type = 'U')
BEGIN
    CREATE TABLE support_tickets (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id BIGINT NOT NULL,
        subject NVARCHAR(255) NOT NULL,
        description NVARCHAR(MAX) NULL,
        status NVARCHAR(20) NOT NULL DEFAULT 'OPEN',  -- OPEN, IN_PROGRESS, RESOLVED, CLOSED
        priority NVARCHAR(20) NOT NULL DEFAULT 'NORMAL', -- LOW, NORMAL, HIGH, URGENT
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        resolved_at DATETIME2 NULL,

        CONSTRAINT FK_support_tickets_user FOREIGN KEY (user_id) REFERENCES users(id)
    );

    CREATE INDEX IX_support_tickets_user ON support_tickets(user_id);
    CREATE INDEX IX_support_tickets_status ON support_tickets(status);

    PRINT 'Đã tạo bảng support_tickets thành công';
END
ELSE
BEGIN
    PRINT 'Bảng support_tickets đã tồn tại, bỏ qua';
END
GO

-- ============================================================================
-- END V24
-- ============================================================================
