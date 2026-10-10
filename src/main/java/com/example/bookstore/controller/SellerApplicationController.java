package com.example.bookstore.controller;

import com.example.bookstore.service.LegalDocumentStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Upload ảnh tài liệu pháp lý cho hồ sơ đăng ký người bán.
 *
 * <p>Dùng chung cho BUYER (gửi yêu cầu mở shop ở Become_Seller) và SELLER
 * (bổ sung hồ sơ ở trang hồ sơ gian hàng). Ảnh được lưu ở
 * {@code uploads/legal-docs/} rồi URL trả về được nhúng vào payload
 * {@code legalDocuments[]} của hồ sơ.</p>
 *
 * <p>Bảo mật: SecurityConfig đã chặn {@code /api/seller-applications/**} cho
 * BUYER/SELLER và ở đây kiểm tra lại bằng @PreAuthorize (defense in depth).</p>
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/api/seller-applications")
public class SellerApplicationController {

    private static final Set<String> DOC_TYPES = Set.of(
            "IDENTITY", "BUSINESS_REGISTRATION", "TAX", "SPECIAL_LICENSE", "BRAND_DOC");

    private final LegalDocumentStorageService storageService;

    public SellerApplicationController(LegalDocumentStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(value = "/upload-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('BUYER','SELLER')")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false, defaultValue = "IDENTITY") String type) {
        try {
            String imageUrl = storageService.store(file);

            String normalizedType = type == null ? "IDENTITY" : type.trim().toUpperCase(Locale.ROOT);
            if (!DOC_TYPES.contains(normalizedType)) {
                normalizedType = "IDENTITY";
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("imageUrl", imageUrl);
            response.put("type", normalizedType);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Upload thất bại: " + e.getMessage());
        }
    }
}
