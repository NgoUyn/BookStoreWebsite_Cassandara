package com.example.bookstore.service;

import com.example.bookstore.service.storage.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Lưu ảnh tài liệu pháp lý của hồ sơ người bán.
 *
 * <p>Uỷ quyền cho {@link FileStorageService} (kho lưu trữ tập trung):</p>
 * <ul>
 *   <li>{@code app.storage.type=gridfs} (mặc định) → GridFS trong MongoDB, URL
 *       {@code /api/files/{id}} — nằm cùng database nên {@code mongodump} sao lưu
 *       được cả hồ sơ pháp lý (ảnh CCCD/ĐKKD) lẫn dữ liệu.</li>
 *   <li>{@code app.storage.type=local} → {@code uploads/legal-docs/} như trước.</li>
 * </ul>
 *
 * <p>Bảo mật giống luồng ảnh bìa sách: kiểm tra CHỮ KÝ FILE bằng Apache Tika
 * (không tin content-type của client), tên file sinh bằng UUID → tránh path
 * traversal và không đoán được.</p>
 */
@Service
public class LegalDocumentStorageService {

    private final FileStorageService fileStorageService;

    public LegalDocumentStorageService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Lưu 1 ảnh tài liệu pháp lý và trả về URL tương đối để lưu vào DB.
     *
     * @throws IllegalArgumentException khi file rỗng hoặc không phải ảnh hợp lệ
     * @throws IOException khi ghi file thất bại
     */
    public String store(MultipartFile file) throws IOException {
        return fileStorageService.store(file, "legal-docs").url();
    }

    /** Backend lưu trữ hiện hành: gridfs | local (dùng cho log/kiểm tra). */
    public String getBackend() {
        return fileStorageService.backend();
    }
}
