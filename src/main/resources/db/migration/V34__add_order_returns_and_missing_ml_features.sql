-- ============================================================================
-- V34: Tạo bảng order_returns + Thêm lại discount_usage_rate, return_rate
--      vào customer_ml (đã bị xoá ở V33)
-- ============================================================================
-- Lý do: features_config.json mới yêu cầu 12 raw features, trong đó có
--   discount_usage_rate (float) và return_rate (float)
-- ============================================================================

-- ==========================================
-- 1. Tạo bảng order_returns
-- ==========================================
IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[order_returns]') AND type = 'U')
BEGIN
    CREATE TABLE order_returns (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id BIGINT NOT NULL,
        order_id BIGINT NOT NULL,
        sub_order_id BIGINT NOT NULL,
        order_item_id BIGINT NOT NULL,
        quantity_returned INT NOT NULL,
        reason NVARCHAR(50) NOT NULL,          -- DEFECTIVE, WRONG_ITEM, CHANGE_MIND, OTHER
        status NVARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED, REFUNDED
        created_at DATETIME2 NOT NULL,
        processed_at DATETIME2 NULL,

        CONSTRAINT FK_order_returns_user FOREIGN KEY (user_id) REFERENCES users(id),
        CONSTRAINT FK_order_returns_order FOREIGN KEY (order_id) REFERENCES orders_master(id),
        CONSTRAINT FK_order_returns_sub_order FOREIGN KEY (sub_order_id) REFERENCES sub_orders(id),
        CONSTRAINT FK_order_returns_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id)
    );

    CREATE INDEX IX_order_returns_user ON order_returns(user_id);
    CREATE INDEX IX_order_returns_status ON order_returns(status);

    PRINT 'V34: Đã tạo bảng order_returns thành công';
END
ELSE
BEGIN
    PRINT 'V34: Bảng order_returns đã tồn tại, bỏ qua';
END
GO

-- ==========================================
-- 2. Thêm lại cột discount_usage_rate vào customer_ml
-- ==========================================
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customer_ml]') AND name = 'discount_usage_rate')
BEGIN
    ALTER TABLE customer_ml ADD discount_usage_rate FLOAT NOT NULL DEFAULT 0.0;
    PRINT 'V34: Đã thêm cột discount_usage_rate vào customer_ml';
END
ELSE
BEGIN
    PRINT 'V34: Cột discount_usage_rate đã tồn tại trong customer_ml';
END
GO

-- ==========================================
-- 3. Thêm lại cột return_rate vào customer_ml
-- ==========================================
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customer_ml]') AND name = 'return_rate')
BEGIN
    ALTER TABLE customer_ml ADD return_rate FLOAT NOT NULL DEFAULT 0.0;
    PRINT 'V34: Đã thêm cột return_rate vào customer_ml';
END
ELSE
BEGIN
    PRINT 'V34: Cột return_rate đã tồn tại trong customer_ml';
END
GO

-- ==========================================
-- 4. Cập nhật dữ liệu mặc định cho các bản ghi cũ
-- ==========================================
UPDATE customer_ml
SET discount_usage_rate = 0.0
WHERE discount_usage_rate IS NULL;

UPDATE customer_ml
SET return_rate = 0.0
WHERE return_rate IS NULL;

PRINT 'V34: Hoàn tất cập nhật dữ liệu mặc định cho customer_ml';
GO

-- ============================================================================
-- END V34
-- ============================================================================
