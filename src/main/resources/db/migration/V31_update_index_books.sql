-- ============================================================================
-- V30: Đánh Index tối ưu hóa tốc độ tìm kiếm cho bảng books (Bản chuẩn hóa)
-- ============================================================================

-- 1. Tạo Index bao phủ (Covering Index) siêu tốc cho luồng Public Search
-- Thay thế hoàn toàn cho các index đơn lẻ cũ để tránh lãng phí tài nguyên
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_books_search_covering' AND object_id = OBJECT_ID('books'))
BEGIN
    CREATE NONCLUSTERED INDEX idx_books_search_covering
    ON books(approval_status, is_active)
    INCLUDE (id, title, author, publisher);
END

-- 2. Index cho cột Danh mục phục vụ bộ lọc tìm kiếm nâng cao
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_books_category' AND object_id = OBJECT_ID('books'))
BEGIN
    CREATE NONCLUSTERED INDEX idx_books_category
    ON books(category_id);
END

-- 3. Composite Index phục vụ bộ lọc tìm kiếm trong kho của Người bán (Seller Dashboard)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_books_seller_status_active' AND object_id = OBJECT_ID('books'))
BEGIN
    CREATE NONCLUSTERED INDEX idx_books_seller_status_active
    ON books(seller_id, approval_status, is_active);
END

-- 4. Composite Index phục vụ sắp xếp hàng mới lên đầu ở Trang chủ
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_books_status_active_id' AND object_id = OBJECT_ID('books'))
BEGIN
    CREATE NONCLUSTERED INDEX idx_books_status_active_id
    ON books(approval_status, is_active, id DESC);
END