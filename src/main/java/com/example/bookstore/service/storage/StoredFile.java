package com.example.bookstore.service.storage;

import java.time.LocalDateTime;

/**
 * Kết quả lưu 1 file vào kho lưu trữ (GridFS hoặc thư mục {@code uploads/}).
 *
 * <p><b>id</b>: khoá nội bộ của kho — ObjectId hex (GridFS) hoặc tên file trên đĩa.</p>
 * <p><b>url</b>: đường dẫn công khai được lưu vào DB và trả về frontend
 * ({@code /api/files/{id}} với GridFS, {@code /uploads/{folder}/{name}} với local).</p>
 *
 * <p>Nhờ lớp trừu tượng này, hợp đồng REST ({@code imageUrl} là String) không đổi
 * → toàn bộ template/JS hiện có vẫn chạy nguyên.</p>
 */
public record StoredFile(
        String id,
        String url,
        String filename,
        String contentType,
        long size,
        String scope,
        LocalDateTime uploadedAt) {
}
