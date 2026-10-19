package com.example.bookstore.service.storage;

import org.bson.types.ObjectId;

/**
 * Tiện ích xử lý URL file do {@link GridFsStorageService} sinh ra.
 *
 * <p>URL dạng {@code /api/files/{objectIdHex}}: vì URL đã chứa sẵn ObjectId nên
 * các service (SellerShopService, SellerApplicationService) có thể suy ra
 * {@code logoFileId}/{@code bannerFileId}/{@code LegalDocument.fileId} mà
 * KHÔNG cần thêm field vào DTO request.</p>
 */
public final class FileUrls {

    /** Tiền tố URL công khai của file lưu trong GridFS. */
    public static final String PREFIX = "/api/files/";

    private FileUrls() {
    }

    /**
     * Lấy ObjectId hex từ URL {@code /api/files/{id}}.
     *
     * @return ObjectId hex, hoặc {@code null} nếu URL không phải file GridFS
     *         (ví dụ URL legacy {@code /uploads/covers/...}, {@code /images/covers/...})
     */
    public static String gridFsIdOf(String url) {
        if (url == null) {
            return null;
        }
        String value = url.trim();
        if (!value.startsWith(PREFIX)) {
            return null;
        }
        String id = value.substring(PREFIX.length());
        if (id.isEmpty() || id.indexOf('/') >= 0 || id.indexOf('?') >= 0 || id.indexOf('#') >= 0) {
            return null;
        }
        return ObjectId.isValid(id) ? id : null;
    }
}
