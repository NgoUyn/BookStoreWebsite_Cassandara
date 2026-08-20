-- ============================================================================
-- V30: Đánh Index cho bảng books để tối ưu tốc độ tìm kiếm
-- ============================================================================

-- 1. Index cho các cột hay được tìm kiếm bằng chữ (Search)
CREATE NONCLUSTERED INDEX idx_books_title ON books(title);
CREATE NONCLUSTERED INDEX idx_books_author ON books(author);

-- 2. Index cho các cột hay dùng để lọc (Filter)
CREATE NONCLUSTERED INDEX idx_books_category ON books(category_id);

-- 3. Composite Index (Index kết hợp) rất quan trọng cho trang chủ và người bán
-- Giúp tăng tốc cực nhanh khi tìm sách của 1 người bán cụ thể hoặc sách đang Active/Approved
CREATE NONCLUSTERED INDEX idx_books_seller_status_active
ON books(seller_id, approval_status, is_active);

-- 4. Composite Index cho trang chủ (Lấy sách Approved, Active và sắp xếp theo ID)
CREATE NONCLUSTERED INDEX idx_books_status_active_id
ON books(approval_status, is_active, id DESC);