package com.example.bookstore.controller;

import com.example.bookstore.dto.CategoryWithCount;
import com.example.bookstore.dto.SellerShopResponse;
import com.example.bookstore.dto.SellerShopUpsertRequest;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.service.SellerShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

 

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SellerShopController {

    private final SellerShopService shopService;
    private final BookRepository bookRepository;

    // ==========================================
    // ENDPOINTS DÀNH CHO SELLER QUẢN LÝ SHOP
    // ==========================================


    @GetMapping("/seller/me/shop")
    @PreAuthorize("hasAuthority('SELLER')")
    public ResponseEntity<SellerShopResponse> getMyShop(@org.springframework.security.core.annotation.AuthenticationPrincipal JwtAuthenticatedPrincipal principal) {
        Long sellerId = getCurrentSellerId(principal);
        SellerShopResponse response = shopService.getMyShop(sellerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/seller/me/shop")
    @PreAuthorize("hasAuthority('SELLER')")
    public ResponseEntity<SellerShopResponse> createMyShop(
            @Valid @RequestBody SellerShopUpsertRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal JwtAuthenticatedPrincipal principal
    ) {
        Long sellerId = getCurrentSellerId(principal);
        SellerShopResponse response = shopService.createMyShop(sellerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/seller/me/shop")
    @PreAuthorize("hasAuthority('SELLER')")
    public ResponseEntity<SellerShopResponse> updateMyShop(
            @Valid @RequestBody SellerShopUpsertRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal JwtAuthenticatedPrincipal principal
    ) {
        Long sellerId = getCurrentSellerId(principal);
        SellerShopResponse response = shopService.updateMyShop(sellerId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/seller/me/shop/status")
    @PreAuthorize("hasAuthority('SELLER')")
    public ResponseEntity<SellerShopResponse> changeStatus(
            @RequestParam ApprovalStatus status,
            @org.springframework.security.core.annotation.AuthenticationPrincipal JwtAuthenticatedPrincipal principal
    ) {
        Long sellerId = getCurrentSellerId(principal);
        SellerShopResponse response = shopService.changeStatus(sellerId, status);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/seller/me/shop")
    @PreAuthorize("hasAuthority('SELLER')")
    public ResponseEntity<Void> deleteMyShop(@org.springframework.security.core.annotation.AuthenticationPrincipal JwtAuthenticatedPrincipal principal) {
        Long sellerId = getCurrentSellerId(principal);
        shopService.deleteMyShop(sellerId);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // UPLOAD ẢNH CHO SHOP (LOGO / BANNER)
    // ==========================================

    @Value("${app.uploads.shops-dir:uploads/shops}")
    private String shopsUploadDir;

    @PostMapping(value = "/seller/me/shop/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SELLER')")
    public ResponseEntity<String> uploadShopImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            @org.springframework.security.core.annotation.AuthenticationPrincipal JwtAuthenticatedPrincipal principal
    ) {
        Long sellerId = getCurrentSellerId(principal);

        // Validate type
        if (!"logo".equals(type) && !"banner".equals(type)) {
            return ResponseEntity.badRequest().body("type phải là 'logo' hoặc 'banner'");
        }

        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File không được để trống");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body("Chỉ chấp nhận file ảnh");
        }

        try {
            // Ensure upload directory exists
            Path uploadPath = Paths.get(shopsUploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            String safeFileName = type + "_" + sellerId + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
            Path filePath = uploadPath.resolve(safeFileName);

            // Save file
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Return URL path (relative, frontend will prepend base URL)
            String imageUrl = "/uploads/shops/" + safeFileName;
            return ResponseEntity.ok(imageUrl);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Upload thất bại: " + e.getMessage());
        }
    }

    // ==========================================
    // PUBLIC ENDPOINTS DÀNH CHO KHÁCH HÀNG
    // ==========================================

    @GetMapping("/shops/{slug}")
    public ResponseEntity<SellerShopResponse> getPublicShopBySlug(@PathVariable String slug) {
        SellerShopResponse response = shopService.getPublicShopBySlug(slug);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy danh sách danh mục kèm số lượng sách đã duyệt của shop.
     * Dùng cho sidebar lọc danh mục ở trang shop public.
     * GET /api/shops/{slug}/categories
     */
    @GetMapping("/shops/{slug}/categories")
    public ResponseEntity<List<CategoryWithCount>> getShopCategories(@PathVariable String slug) {
        // Lấy sellerId từ slug
        SellerShopResponse shop = shopService.getPublicShopBySlug(slug);
        Long sellerId = shop.getSellerId();

        // Query categories với count
        List<Object[]> results = bookRepository.countBooksByCategoryAndSeller(sellerId, ApprovalStatus.APPROVED.name());
        List<CategoryWithCount> categories = new ArrayList<>();
        for (Object[] row : results) {
            Long id = ((Number) row[0]).longValue();
            String name = (String) row[1];
            long count = ((Number) row[2]).longValue();
            categories.add(new CategoryWithCount(id, name, count));
        }
        return ResponseEntity.ok(categories);
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================


    private Long getCurrentSellerId(JwtAuthenticatedPrincipal principal) {
        if (principal != null) {
            if (principal.sellerId() != null) return principal.sellerId();
            return principal.userId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
}