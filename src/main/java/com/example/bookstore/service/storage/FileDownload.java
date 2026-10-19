package com.example.bookstore.service.storage;

import org.springframework.core.io.Resource;

/**
 * Nội dung 1 file trong kho lưu trữ (dùng cho endpoint {@code GET /api/files/{id}}).
 *
 * @param resource stream nội dung — controller trả thẳng cho client, không đọc hết vào RAM
 */
public record FileDownload(
        String filename,
        String contentType,
        long size,
        long lastModified,
        Resource resource) {
}
