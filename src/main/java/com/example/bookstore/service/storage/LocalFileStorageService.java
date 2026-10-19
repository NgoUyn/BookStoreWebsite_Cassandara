package com.example.bookstore.service.storage;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Ghi file ra ổ đĩa như hành vi gốc của dự án ({@code uploads/...}) — dùng khi
 * {@code app.storage.type=local}.
 *
 * <p>URL trả về là {@code /uploads/{folder}/{file}} và được phục vụ bởi
 * {@code WebConfig} (không đi qua {@code /api/files/**}).</p>
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local")
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private static final Set<String> ALLOWED_MIME = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String DEFAULT_FOLDER = "covers";

    @Value("${app.uploads.covers-dir:uploads/covers}")
    private String coversDir;

    @Value("${app.uploads.shops-dir:uploads/shops}")
    private String shopsDir;

    @Value("${app.uploads.legal-docs-dir:uploads/legal-docs}")
    private String legalDocsDir;

    @Override
    public String backend() {
        return LOCAL;
    }

    @Override
    public StoredFile store(MultipartFile file, String scope) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }

        String mimeType = new Tika().detect(file.getInputStream());
        if (!ALLOWED_MIME.contains(mimeType)) {
            throw new IllegalArgumentException("File tải lên không phải định dạng ảnh hợp lệ (chỉ nhận JPG/PNG/WEBP)!");
        }

        String folder = folderOf(scope);
        Path uploadPath = Paths.get(directoryOf(folder));
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String fileName = folder + "_" + UUID.randomUUID().toString().replace("-", "") + extensionFor(mimeType);
        try (var in = file.getInputStream()) {
            Files.copy(in, uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        }

        String url = "/uploads/" + folder + "/" + fileName;
        log.debug("Local store {} -> {}", scope, url);
        return new StoredFile(fileName, url, fileName, mimeType, file.getSize(), folder, LocalDateTime.now());
    }

    @Override
    public FileDownload open(String fileId) throws IOException {
        if (fileId == null || fileId.isBlank() || fileId.contains("/") || fileId.contains("\\") || fileId.contains("..")) {
            throw new IllegalArgumentException("File id không hợp lệ: " + fileId);
        }
        for (Map.Entry<String, String> entry : directoriesByFolder().entrySet()) {
            Path candidate = Paths.get(entry.getValue()).resolve(fileId);
            if (Files.isRegularFile(candidate)) {
                Resource resource = new FileSystemResource(candidate);
                String contentType = new Tika().detect(candidate);
                return new FileDownload(fileId, contentType, Files.size(candidate),
                        Files.getLastModifiedTime(candidate).toMillis(), resource);
            }
        }
        throw new NoSuchElementException("Không tìm thấy file: " + fileId);
    }

    @Override
    public void delete(String fileId) {
        try {
            for (String directory : directoriesByFolder().values()) {
                Files.deleteIfExists(Paths.get(directory).resolve(fileId));
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Không xoá được file local {}: {}", fileId, e.getMessage());
        }
    }

    private Map<String, String> directoriesByFolder() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("covers", coversDir);
        map.put("shops", shopsDir);
        map.put("legal-docs", legalDocsDir);
        return map;
    }

    private String directoryOf(String folder) {
        String directory = directoriesByFolder().get(folder);
        return directory == null ? coversDir : directory;
    }

    private static String folderOf(String scope) {
        if (scope == null || scope.isBlank()) {
            return DEFAULT_FOLDER;
        }
        String cleaned = scope.trim().toLowerCase();
        return switch (cleaned) {
            case "shops" -> "shops";
            case "legal-docs", "legaldocs" -> "legal-docs";
            case "covers" -> "covers";
            default -> DEFAULT_FOLDER;
        };
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
}
