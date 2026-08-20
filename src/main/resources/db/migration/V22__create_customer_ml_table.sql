-- ============================================================================
-- V22: Tạo bảng customer_ml cho mô hình gau-gbt6000
-- ============================================================================
-- Bảng này lưu thông tin khách hàng phục vụ ML churn prediction.
-- Quan hệ 1-1 với users, tách biệt để không ảnh hưởng bảng users.
--
-- Input: 14 raw features (Python tự động engineering thêm 4 derived features)
-- Output: predicted_class (0/1/2), churn_probability, risk_level
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[customer_ml]') AND type = 'U')
BEGIN
    CREATE TABLE customer_ml (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id BIGINT NOT NULL UNIQUE,

        -- ========================
        -- ML Input Features (14 raw features)
        -- ========================
        account_age_months FLOAT NOT NULL,
        avg_order_value FLOAT NOT NULL,
        total_orders FLOAT NOT NULL,
        days_since_last_purchase FLOAT NOT NULL,
        discount_usage_rate FLOAT NULL,
        return_rate FLOAT NOT NULL,
        customer_support_tickets FLOAT NOT NULL,
        loyalty_member NVARCHAR(10) NOT NULL,           -- "Yes" / "No"
        browsing_frequency_per_week FLOAT NOT NULL,
        cart_abandonment_rate FLOAT NOT NULL,
        product_review_score_avg FLOAT NOT NULL,
        engagement_score FLOAT NOT NULL,
        satisfaction_score FLOAT NOT NULL,
        price_sensitivity_index FLOAT NOT NULL,

        -- ========================
        -- ML Output Results
        -- ========================
        predicted_class INT NULL,                        -- 0 = An toàn, 1 = Trung bình, 2 = Cao
        churn_probability FLOAT NULL,
        risk_level NVARCHAR(30) NULL,                    -- "LOW (An toàn)" / "MEDIUM (Trung bình)" / "HIGH (Nguy cơ cao)"
        last_analyzed_at DATETIME2 NULL,

        -- ========================
        -- Timestamps
        -- ========================
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NOT NULL,

        CONSTRAINT FK_customer_ml_user FOREIGN KEY (user_id) REFERENCES users(id)
    );

    -- Index cho truy vấn nhanh theo user
    CREATE INDEX IX_customer_ml_user_id ON customer_ml(user_id);

    -- Index cho phân tích theo mức độ rủi ro
    CREATE INDEX IX_customer_ml_risk_level ON customer_ml(risk_level) WHERE risk_level IS NOT NULL;

    PRINT 'Đã tạo bảng customer_ml thành công';
END
ELSE
BEGIN
    PRINT 'Bảng customer_ml đã tồn tại, bỏ qua';
END
GO

-- ============================================================================
-- END V22
-- ============================================================================
