-- ============================================================
-- Migration V15: Remove PENDING_PAYMENT status, rename PROCESSING
-- ============================================================
-- Quy trình mới: PROCESSING (Đang xác nhận) -> COMFIRMED (Đã xác nhận)
--                 -> SHIPPING (Đang giao) -> COMPLETED (Hoàn thành)
--                 -> CANCELLED (Đã hủy)
-- ============================================================

-- 1. Cập nhật các sub_orders đang ở PENDING_PAYMENT thành PROCESSING
UPDATE sub_orders
SET status = 'PROCESSING'
WHERE status = 'PENDING_PAYMENT';

-- 2. Thay đổi DEFAULT constraint cho cột status
DECLARE @defaultConstraintName NVARCHAR(200);
SELECT @defaultConstraintName = name
FROM sys.default_constraints
WHERE parent_object_id = OBJECT_ID('sub_orders')
  AND parent_column_id = COLUMNPROPERTY(OBJECT_ID('sub_orders'), 'status', 'ColumnId');

IF @defaultConstraintName IS NOT NULL
BEGIN
    EXEC('ALTER TABLE sub_orders DROP CONSTRAINT ' + @defaultConstraintName);
END

-- 3. Thêm DEFAULT mới
ALTER TABLE sub_orders
ADD CONSTRAINT DF_sub_orders_status DEFAULT 'PROCESSING' FOR status;
