package com.example.bookstore.controller;

import com.example.bookstore.dto.CategoryWithCount;
import com.example.bookstore.dto.SellerShopResponse;
import com.example.bookstore.dto.SellerShopUpsertRequest;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.service.SellerShopService;
import com.example.bookstore.service.storage.FileStorageService;
import com.example.bookstore.service.storage.StoredFile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

 

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SellerShopController {

    private final SellerShopService shopService;
    private final BookRepository bookRepository;
    private final FileStorageService fileStorageService;

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

        try {
            // FileStorageService tự xác thực CHỮ KÝ FILE (Tika, chỉ nhận JPG/PNG/WEBP),
            // sinh tên file chống path traversal và lưu vào kho (GridFS hoặc uploads/shops/).
            // URL trả về được frontend lưu vào logoUrl/bannerUrl của shop.
            StoredFile stored = fileStorageService.store(file, "shops");
            return ResponseEntity.ok(stored.url());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
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