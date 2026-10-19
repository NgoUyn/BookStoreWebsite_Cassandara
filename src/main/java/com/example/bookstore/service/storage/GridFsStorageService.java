package com.example.bookstore.service.storage;

import com.mongodb.client.gridfs.model.GridFSFile;
import org.apache.tika.Tika;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Lưu ảnh trong GridFS của MongoDB ({@code fs.files} + {@code fs.chunks}) — Phase 4
 * của {@code docs/mongodb/DESIGN.md}.
 *
 * <p>Lợi ích: file nằm trong CHÍNH database nên {@code mongodump --gzip --archive}
 * sao lưu được cả dữ liệu lẫn ảnh trong 1 archive, và restore ở máy khác là ảnh
 * hiện lại ngay (không phụ thuộc thư mục {@code uploads/}).</p>
 *
 * <p>Bảo mật giữ nguyên như luồng cũ:</p>
 * <ul>
 *   <li>Xác thực bằng CHỮ KÝ FILE (Apache Tika) — không tin {@code Content-Type} của client.</li>
 *   <li>Tên file sinh bằng UUID (chống path traversal, không đoán được).</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "gridfs", matchIfMissing = true)
public class GridFsStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(GridFsStorageService.class);

    private static final Set<String> ALLOWED_MIME = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int MAX_ORIGINAL_NAME_LENGTH = 200;

    private final GridFsTemplate gridFsTemplate;

    public GridFsStorageService(GridFsTemplate gridFsTemplate) {
        this.gridFsTemplate = gridFsTemplate;
    }

    @Override
    public String backend() {
        return GRIDFS;
    }

    @Override
    public StoredFile store(MultipartFile file, String scope) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }

        // Chống RCE: xác thực bằng magic bytes thay vì Content-Type do client khai báo
        String mimeType = new Tika().detect(file.getInputStream());
        if (!ALLOWED_MIME.contains(mimeType)) {
            throw new IllegalArgumentException("File tải lên không phải định dạng ảnh hợp lệ (chỉ nhận JPG/PNG/WEBP)!");
        }

        String cleanedScope = sanitizeScope(scope);
        String filename = cleanedScope + "_" + UUID.randomUUID().toString().replace("-", "") + extensionFor(mimeType);

        Document metadata = new Document()
                .append("scope", cleanedScope)
                .append("originalName", originalName(file))
                .append("uploadedAt", new Date());

        try (InputStream in = file.getInputStream()) {
            ObjectId id = gridFsTemplate.store(in, filename, mimeType, metadata);
            String url = FileUrls.PREFIX + id.toHexString();
            log.debug("GridFS store {} -> {} ({} bytes, {})", cleanedScope, url, file.getSize(), mimeType);
            return new StoredFile(id.toHexString(), url, filename, mimeType, file.getSize(),
                    cleanedScope, LocalDateTime.now());
        }
    }

    @Override
    public FileDownload open(String fileId) {
        ObjectId objectId = parseObjectId(fileId);

        GridFSFile gridFile = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(objectId)));
        if (gridFile == null) {
            throw new NoSuchElementException("Không tìm thấy file trong GridFS: " + fileId);
        }

        String filename = gridFile.getFilename();
        String contentType = null;
        Document metadata = gridFile.getMetadata();
        if (metadata != null) {
            contentType = metadata.getString("contentType");
            if (filename == null) {
                filename = metadata.getString("filename");
            }
        }

        long length = gridFile.getLength();
        long uploadedAt = gridFile.getUploadDate() == null ? 0L : gridFile.getUploadDate().getTime();
        // GridFsResource mở download stream của driver ngay khi được tạo
        Resource resource = gridFsTemplate.getResource(gridFile);

        return new FileDownload(filename, contentType, length, uploadedAt, resource);
    }

    @Override
    public void delete(String fileId) {
        try {
            gridFsTemplate.delete(Query.query(Criteria.where("_id").is(parseObjectId(fileId))));
        } catch (IllegalArgumentException e) {
            log.debug("Bỏ qua xoá GridFS file với id không hợp lệ: {}", fileId);
        }
    }

    private ObjectId parseObjectId(String fileId) {
        if (fileId == null || !ObjectId.isValid(fileId)) {
            throw new IllegalArgumentException("File id không hợp lệ: " + fileId);
        }
        return new ObjectId(fileId);
    }

    private static String extensionFor(String mimeType) {
        if ("image/png".equals(mimeType)) {
            return ".png";
        }
        if ("image/webp".equals(mimeType)) {
            return ".webp";
        }
        return ".jpg";
    }

    private static String sanitizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "files";
        }
        String cleaned = scope.trim().toLowerCase().replaceAll("[^a-z0-9-]", "-");
        return cleaned.isEmpty() ? "files" : cleaned;
    }

    private static String originalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (!StringUtils.hasText(name)) {
            return "unknown";
        }
        String cleaned = name.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        return cleaned.length() > MAX_ORIGINAL_NAME_LENGTH
                ? cleaned.substring(0, MAX_ORIGINAL_NAME_LENGTH)
                : cleaned;
    }
}
