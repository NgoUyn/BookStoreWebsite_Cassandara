-- ============================================================================
-- V26: Add seller_id to coupons table (multi-seller voucher support)
-- ============================================================================
-- This migration enables seller-specific vouchers:
-- 1. Each coupon can be owned by a specific seller
-- 2. Buyers can only use vouchers from the seller they're purchasing from
-- 3. NULL seller_id = global/admin coupon (usable by all)
-- ============================================================================

-- 1. Add seller_id column if not exists
IF NOT EXISTS (SELECT * FROM sys.columns 
               WHERE object_id = OBJECT_ID(N'[dbo].[coupons]') 
               AND name = 'seller_id')
BEGIN
    ALTER TABLE coupons ADD seller_id BIGINT NULL;
END
GO

-- 2. Add FOREIGN KEY constraint
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
               WHERE TABLE_NAME = 'coupons' 
               AND CONSTRAINT_NAME = 'FK_coupons_seller')
BEGIN
    ALTER TABLE coupons 
    ADD CONSTRAINT FK_coupons_seller 
    FOREIGN KEY (seller_id) REFERENCES users(id);
END
GO

-- 3. Add indexes for seller queries
IF NOT EXISTS (SELECT * FROM sys.indexes 
               WHERE name = 'IX_coupons_seller_id' 
               AND object_id = OBJECT_ID('dbo.coupons'))
BEGIN
    CREATE INDEX IX_coupons_seller_id ON coupons(seller_id);
END
GO

-- 4. Add composite index for seller + code lookup (fast validation)
IF NOT EXISTS (SELECT * FROM sys.indexes 
               WHERE name = 'IX_coupons_seller_code' 
               AND object_id = OBJECT_ID('dbo.coupons'))
BEGIN
    CREATE INDEX IX_coupons_seller_code ON coupons(seller_id, code);
END
GO

-- 5. Add index for active seller coupons listing
IF NOT EXISTS (SELECT * FROM sys.indexes 
               WHERE name = 'IX_coupons_seller_active' 
               AND object_id = OBJECT_ID('dbo.coupons'))
BEGIN
    CREATE INDEX IX_coupons_seller_active ON coupons(seller_id, is_active) 
    WHERE is_active = 1;
END
GO

-- ============================================================================
-- END V26
-- ============================================================================
