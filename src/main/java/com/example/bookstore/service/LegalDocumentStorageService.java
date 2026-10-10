package com.example.bookstore.service;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Lưu ảnh tài liệu pháp lý của hồ sơ người bán vào {@code uploads/legal-docs/}
 * (ngoài JAR, phục vụ bởi WebConfig qua URL {@code /uploads/legal-docs/**}).
 *
 * <p>Bảo mật giống luồng ảnh bìa sách ({@code BookService.uploadAndVerifyCoverImage}):</p>
 * <ul>
 *   <li>Kiểm tra CHỮ KÝ FILE bằng Apache Tika (không tin content-type của client).</li>
 *   <li>Tên file sinh bằng UUID → tránh path traversal và không đoán được.</li>
 * </ul>
 */
@Service
public class LegalDocumentStorageService {

    private static final Set<String> ALLOWED_MIME = Set.of("image/jpeg", "image/png", "image/webp");

    @Value("${app.uploads.legal-docs-dir:uploads/legal-docs}")
    private String legalDocsDir;

    /**
     * Lưu 1 ảnh tài liệu pháp lý và trả về URL tương đối để lưu vào DB.
     *
     * @throws IllegalArgumentException khi file rỗng hoặc không phải ảnh hợp lệ
     * @throws IOException khi ghi file thất bại
     */
    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }

        // Chống RCE: xác thực bằng magic bytes thay vì content-type do client khai báo
        String mimeType = new Tika().detect(file.getInputStream());
        if (!ALLOWED_MIME.contains(mimeType)) {
            throw new IllegalArgumentException("File tải lên không phải định dạng ảnh hợp lệ (chỉ nhận JPG/PNG/WEBP)!");
        }

        String extension;
        if ("image/png".equals(mimeType)) {
            extension = ".png";
        } else if ("image/webp".equals(mimeType)) {
            extension = ".webp";
        } else {
            extension = ".jpg";
        }

        Path uploadPath = Paths.get(legalDocsDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        Files.copy(file.getInputStream(), uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/legal-docs/" + fileName;
    }

    /** Thư mục lưu trữ hiện hành (dùng cho log/kiểm tra). */
    public String getLegalDocsDir() {
        return legalDocsDir;
    }
}
