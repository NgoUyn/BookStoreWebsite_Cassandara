package com.example.bookstore.dto.backup;

import java.time.LocalDateTime;

/**
 * Thông tin 1 file sao lưu {@code .archive} trong thư mục {@code backups/}.
 *
 * @param fileName  tên file (đã kiểm tra an toàn, không chứa đường dẫn)
 * @param sizeBytes dung lượng file
 * @param createdAt thời điểm tạo (lastModified trên đĩa)
 * @param latest    true nếu là bản mới nhất (JS dùng để tô đậm/bật nút nhanh)
 */
public record BackupInfo(
        String fileName,
        long sizeBytes,
        LocalDateTime createdAt,
        boolean latest) {
}
