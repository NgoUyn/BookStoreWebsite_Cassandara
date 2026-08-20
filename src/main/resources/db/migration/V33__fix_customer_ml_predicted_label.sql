-- ============================================================================
-- V33: Khắc phục lỗi V32 - Cập nhật bảng customer_ml cho model final-gauss-lightgbm
-- ============================================================================
-- V32 bị lỗi "Invalid column name 'predicted_label'" vì SQL Server compile
-- toàn bộ batch cùng lúc. Dòng UPDATE predicted_label được compile trước khi
-- sp_rename kịp tạo cột.
--
-- Cách fix: Dùng GO để tách thành 2 batch riêng biệt:
--   BATCH 1: Xoá 4 cột cũ + Đổi tên/Thêm cột predicted_label
--   BATCH 2: UPDATE dữ liệu (lúc này predicted_label đã tồn tại)
-- ============================================================================

-- ==========================================
-- BATCH 1: Schema changes
-- ==========================================

-- 1. Xoá 4 cột input không còn dùng trong model mới
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customer_ml]') AND name = 'days_since_last_purchase')
BEGIN
    ALTER TABLE customer_ml DROP COLUMN days_since_last_purchase;
    PRINT 'V33: Đã xoá cột days_since_last_purchase';
END

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customer_ml]') AND name = 'discount_usage_rate')
BEGIN
    ALTER TABLE customer_ml DROP COLUMN discount_usage_rate;
    PRINT 'V33: Đã xoá cột discount_usage_rate';
END

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customer_ml]') AND name = 'return_rate')
BEGIN
    ALTER TABLE customer_ml DROP COLUMN return_rate;
    PRINT 'V33: Đã xoá cột return_rate';
END

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customer_ml]') AND name = 'engagement_score')
BEGIN
    ALTER TABLE customer_ml DROP COLUMN engagement_score;
    PRINT 'V33: Đã xoá cột engagement_score';
END

-- 2. Đảm bảo cột predicted_label tồn tại
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customer_ml]') AND name = 'predicted_label')
BEGIN
    -- Nếu còn cột predicted_class thì đổi tên
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customer_ml]') AND name = 'predicted_class')
    BEGIN
        EXEC sp_rename 'customer_ml.predicted_class', 'predicted_label', 'COLUMN';
        PRINT 'V33: Đã đổi tên predicted_class → predicted_label';
    END
    ELSE
    BEGIN
        -- Nếu không có cả 2 cột, thêm mới cột predicted_label
        ALTER TABLE customer_ml ADD predicted_label INT DEFAULT NULL;
        PRINT 'V33: Đã thêm mới cột predicted_label';
    END
END
ELSE
BEGIN
    PRINT 'V33: Cột predicted_label đã tồn tại';
END

GO
-- ========== KẾT THÚC BATCH 1 ==========

-- ==========================================
-- BATCH 2: Data migration
-- (Lúc này predicted_label đã tồn tại trong schema)
-- ==========================================

-- 3. Chuẩn hoá predicted_label: model cũ có 3 classes (0,1,2), model mới chỉ có 2 (0,1)
-- Class 2 (Cao) cũ → label 1 (Churn) mới
UPDATE customer_ml
SET predicted_label = 1
WHERE predicted_label = 2;

-- Class NULL → mặc định 0 (Stay)
UPDATE customer_ml
SET predicted_label = 0
WHERE predicted_label IS NULL;

PRINT 'V33: Đã chuẩn hoá dữ liệu predicted_label';

-- 4. Chuẩn hoá risk_level (chỉ LOW/HIGH)
UPDATE customer_ml
SET risk_level = 'LOW'
WHERE risk_level IN ('MEDIUM', 'CRITICAL') OR risk_level IS NULL;

UPDATE customer_ml
SET risk_level = 'LOW'
WHERE risk_level NOT IN ('LOW', 'HIGH');

PRINT 'V33: Hoàn tất cập nhật bảng customer_ml cho model final-gauss-lightgbm';

GO
-- ========== KẾT THÚC BATCH 2 ==========

-- ============================================================================
-- END V33
-- ============================================================================
