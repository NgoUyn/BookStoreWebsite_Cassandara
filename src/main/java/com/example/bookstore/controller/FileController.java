package com.example.bookstore.controller;

import com.example.bookstore.service.storage.FileDownload;
import com.example.bookstore.service.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Phục vụ file lưu trong GridFS theo URL công khai {@code /api/files/{objectId}}.
 *
 * <p>URL này KHÔNG lộ đường dẫn vật lý và ObjectId không đoán được. Ảnh không phải
 * dữ liệu nhạy cảm nên endpoint được permitAll (giống {@code /images/**});
 * hồ sơ pháp lý vẫn được bảo vệ ở tầng service khi trả về API shop.</p>
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable("fileId") String fileId) {
        try {
            FileDownload download = fileStorageService.open(fileId);

            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (download.contentType() != null) {
                try {
                    mediaType = MediaType.parseMediaType(download.contentType());
                } catch (InvalidMediaTypeException e) {
                    log.debug("Content-Type không hợp lệ trong GridFS metadata: {}", download.contentType());
                }
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(Math.max(download.size(), 0))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                    .body(download.resource());

        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy file.");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File id không hợp lệ.");
        } catch (IOException e) {
            log.error("Không đọc được file {} từ kho lưu trữ", fileId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không đọc được file.");
        }
    }
}
