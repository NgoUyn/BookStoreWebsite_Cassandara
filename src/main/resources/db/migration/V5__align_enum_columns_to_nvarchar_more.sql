-- Align other enum-backed columns to NVARCHAR for SQL Server

DECLARE @sql NVARCHAR(MAX);

-- ==========================================
-- 1. BẢNG USERS - Cột role
-- ==========================================
IF OBJECT_ID('users', 'U') IS NOT NULL
BEGIN
  SET @sql = NULL;

  -- Tự động tìm và xóa mọi Check Constraint đang bám vào cột 'role'
  -- (Giải quyết triệt để lỗi CK__users__role...)
SELECT @sql = STRING_AGG('ALTER TABLE users DROP CONSTRAINT [' + REPLACE(cc.name, ']', ']]') + ']', '; ')
FROM sys.check_constraints cc
WHERE cc.parent_object_id = OBJECT_ID('users')
  AND cc.definition LIKE '%role%';

IF @sql IS NOT NULL
      EXEC(@sql);

  -- Xóa Default Constraint
  IF OBJECT_ID('DF_users_role', 'D') IS NOT NULL
ALTER TABLE users DROP CONSTRAINT DF_users_role;

-- Thay đổi kiểu dữ liệu cột
ALTER TABLE users ALTER COLUMN role NVARCHAR(20) NOT NULL;

-- Phục hồi Default Constraint
IF OBJECT_ID('DF_users_role', 'D') IS NULL
ALTER TABLE users
    ADD CONSTRAINT DF_users_role DEFAULT 'BUYER' FOR role;
END;


-- ==========================================
-- 2. BẢNG SUB_ORDERS - Cột status
-- ==========================================
IF OBJECT_ID('sub_orders', 'U') IS NOT NULL
BEGIN
  SET @sql = NULL;

  -- Tự động tìm và xóa mọi Check Constraint đang bám vào cột 'status'
SELECT @sql = STRING_AGG('ALTER TABLE sub_orders DROP CONSTRAINT [' + REPLACE(cc.name, ']', ']]') + ']', '; ')
FROM sys.check_constraints cc
WHERE cc.parent_object_id = OBJECT_ID('sub_orders')
  AND cc.definition LIKE '%status%';

IF @sql IS NOT NULL
      EXEC(@sql);

  -- Xóa Default Constraint
  IF OBJECT_ID('DF_sub_orders_status', 'D') IS NOT NULL
ALTER TABLE sub_orders DROP CONSTRAINT DF_sub_orders_status;

-- Thay đổi kiểu dữ liệu cột
ALTER TABLE sub_orders ALTER COLUMN status NVARCHAR(30) NOT NULL;

-- Phục hồi Default Constraint
IF OBJECT_ID('DF_sub_orders_status', 'D') IS NULL
ALTER TABLE sub_orders
    ADD CONSTRAINT DF_sub_orders_status DEFAULT 'PENDING_PAYMENT' FOR status;
END;