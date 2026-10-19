package com.example.bookstore.controller.admin;

import com.example.bookstore.dto.backup.BackupOperationResult;
import com.example.bookstore.dto.backup.BackupOverview;
import com.example.bookstore.service.mongo.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Admin: sao lưu / phục hồi TOÀN BỘ hệ thống.
 *
 * <p>Vì ảnh nằm trong GridFS của database {@code bookom}, một bản sao lưu
 * {@code .archive} chứa cả dữ liệu lẫn ảnh (xem {@link BackupService}).</p>
 *
 * <ul>
 *   <li>{@code GET  /api/admin/backup} — tình trạng + danh sách bản sao lưu</li>
 *   <li>{@code POST /api/admin/backup} — tạo bản sao lưu mới</li>
 *   <li>{@code POST /api/admin/backup/{fileName}/restore} — phục hồi (GHI ĐÈ dữ liệu)</li>
 *   <li>{@code GET  /api/admin/backup/{fileName}/download} — tải file về máy</li>
 *   <li>{@code DELETE /api/admin/backup/{fileName}} — xoá file sao lưu</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/backup")
@PreAuthorize("hasAuthority('ADMIN')")
@RequiredArgsConstructor
public class AdminBackupController {

    private final BackupService backupService;

    @GetMapping
    public ResponseEntity<BackupOverview> overview() {
        return ResponseEntity.ok(backupService.overview());
    }

    @PostMapping
    public ResponseEntity<BackupOperationResult> create() {
        return ResponseEntity.ok(backupService.createBackup());
    }

    @PostMapping("/{fileName}/restore")
    public ResponseEntity<BackupOperationResult> restore(@PathVariable("fileName") String fileName) {
        return ResponseEntity.ok(backupService.restoreBackup(fileName));
    }

    @DeleteMapping("/{fileName}")
    public ResponseEntity<BackupOperationResult> delete(@PathVariable("fileName") String fileName) {
        return ResponseEntity.ok(backupService.deleteBackup(fileName));
    }

    /** Tải file .archive về máy (để cất giữ hoặc mang sang máy khác restore). */
    @GetMapping("/{fileName}/download")
    public ResponseEntity<Resource> download(@PathVariable("fileName") String fileName) {
        try {
            Path archive = backupService.resolveBackupPath(fileName);
            if (!Files.isRegularFile(archive)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy file sao lưu.");
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(archive))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + archive.getFileName().toString() + "\"")
                    .body(new FileSystemResource(archive));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không đọc được file sao lưu.");
        }
    }
}
