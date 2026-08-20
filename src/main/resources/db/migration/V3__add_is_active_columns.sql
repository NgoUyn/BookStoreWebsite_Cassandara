-- V3 Migration: Thêm cột is_active cho lock/unlock user và book
-- Tương ứng với feature A02: Khóa/Mở User

-- 1. Thêm cột is_active cho bảng users (default = 1 = active)
IF NOT EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'users' AND COLUMN_NAME = 'is_active'
)
BEGIN
    ALTER TABLE users ADD is_active BIT NOT NULL DEFAULT 1;
    PRINT 'Đã thêm cột is_active vào bảng users';
END;

-- 2. Thêm cột is_active cho bảng books (default = 1 = active, cho feature A03)
IF NOT EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'books' AND COLUMN_NAME = 'is_active'
)
BEGIN
    ALTER TABLE books ADD is_active BIT NOT NULL DEFAULT 1;
    PRINT 'Đã thêm cột is_active vào bảng books';
END;

-- 3. Tạo index trên is_active để tối ưu hóa query
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IDX_users_is_active')
BEGIN
    CREATE INDEX IDX_users_is_active ON users(is_active);
    PRINT 'Đã tạo index trên users.is_active';
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IDX_books_is_active')
BEGIN
    CREATE INDEX IDX_books_is_active ON books(is_active);
    PRINT 'Đã tạo index trên books.is_active';
END;
