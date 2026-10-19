package com.example.bookstore.service;

import com.example.bookstore.dto.SellerShopResponse;
import com.example.bookstore.dto.SellerShopUpsertRequest;
import com.example.bookstore.model.SellerShop;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.model.User;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SellerShopService {

    private static final Set<String> BUSINESS_TYPES = Set.of("INDIVIDUAL", "HOUSEHOLD", "ENTERPRISE", "SPECIAL");
    private static final Set<String> IDENTITY_TYPES = Set.of("CCCD", "CMND", "PASSPORT");
    private static final Set<String> LEGAL_DOC_TYPES = Set.of(
            "IDENTITY", "BUSINESS_REGISTRATION", "TAX", "SPECIAL_LICENSE", "BRAND_DOC");
    private static final Set<String> ALLOWED_IMAGE_PREFIXES = Set.of(
            "/uploads/legal-docs/", "/uploads/shops/", "/images/",
            com.example.bookstore.service.storage.FileUrls.PREFIX);

    private final SellerShopRepository shopRepository;
    private final UserRepository userRepository;

    /**
     * Lấy thông tin shop của chính Seller đang đăng nhập
     */
    public SellerShopResponse getMyShop(Long sellerId) {
        SellerShop shop = shopRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bạn chưa tạo cửa hàng nào."));
        return mapToResponse(shop);
    }

    /**
     * Tạo mới Shop
     */
    @Transactional
    public SellerShopResponse createMyShop(Long sellerId, SellerShopUpsertRequest request) {
        // 1. Lấy thông tin User và kiểm tra Role SELLER
        User seller = validateAndGetSeller(sellerId);

        // 2. Kiểm tra xem Seller này đã có shop chưa (Rule: 1 seller - 1 shop)
        if (shopRepository.findBySellerId(sellerId).isPresent()) {
            throw new RuntimeException("Bạn đã có một cửa hàng. Không thể tạo thêm.");
        }

        // 3. Kiểm tra Slug unique
        if (shopRepository.existsBySlug(request.getSlug())) {
            throw new RuntimeException("Đường dẫn (Slug) này đã tồn tại. Vui lòng chọn đường dẫn khác.");
        }

        // 4. Tạo entity và lưu
        SellerShop newShop = SellerShop.builder()
                .sellerId(sellerId)
                .seller(com.example.bookstore.model.embedded.UserSnapshot.of(seller))
                .slug(request.getSlug())
                .shopName(request.getShopName())
                .description(request.getDescription())
                .logoUrl(request.getLogoUrl())
                .bannerUrl(request.getBannerUrl())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .province(request.getProvince())
                .approvalStatus(ApprovalStatus.PENDING) // Mặc định khi mới tạo là PENDING chờ duyệt
                .build();

        // Hồ sơ pháp lý / thuế / định danh (nếu request có gửi lên)
        applyLegalProfile(newShop, request);
        // Truy vết file ảnh trong GridFS (logo/banner) — Phase 4 DESIGN.md
        applyFileIds(newShop);

        SellerShop savedShop = shopRepository.save(newShop);
        return mapToResponse(savedShop);
    }

    /**
     * Cập nhật thông tin Shop
     * Rule: Chỉ sửa shop của chính họ -> được đảm bảo bằng việc tìm shop qua sellerId
     */
    @Transactional
    public SellerShopResponse updateMyShop(Long sellerId, SellerShopUpsertRequest request) {
        // Lấy shop dựa trên sellerId (Đảm bảo tính sở hữu)
        SellerShop shop = shopRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng của bạn."));

        // Kiểm tra Slug có bị trùng với shop khác không (nếu họ thay đổi slug)
        if (!shop.getSlug().equals(request.getSlug()) && shopRepository.existsBySlug(request.getSlug())) {
            throw new RuntimeException("Đường dẫn (Slug) này đã tồn tại. Vui lòng chọn đường dẫn khác.");
        }

        // Cập nhật thông tin
        shop.setSlug(request.getSlug());
        shop.setShopName(request.getShopName());
        shop.setDescription(request.getDescription());
        shop.setLogoUrl(request.getLogoUrl());
        shop.setBannerUrl(request.getBannerUrl());
        shop.setContactEmail(request.getContactEmail());
        shop.setContactPhone(request.getContactPhone());
        shop.setAddress(request.getAddress());
        shop.setCity(request.getCity());
        shop.setProvince(request.getProvince());

        // Hồ sơ pháp lý / thuế / định danh (chỉ ghi đè field nào request có gửi)
        applyLegalProfile(shop, request);
        // Truy vết file ảnh trong GridFS (logo/banner) — Phase 4 DESIGN.md
        applyFileIds(shop);

        SellerShop updatedShop = shopRepository.save(shop);
        return mapToResponse(updatedShop);
    }

    /**
     * Tạm dừng hoặc kích hoạt lại Shop (Đổi status)
     */
    @Transactional
    public SellerShopResponse changeStatus(Long sellerId, ApprovalStatus status) {
        SellerShop shop = shopRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng của bạn."));

        if (status == ApprovalStatus.APPROVED && shop.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new RuntimeException("Chỉ admin mới có quyền duyệt cửa hàng.");
        }
        
        shop.setApprovalStatus(status);
        SellerShop updatedShop = shopRepository.save(shop);
        return mapToResponse(updatedShop);
    }

    /**
     * Xóa shop của chính Seller (hard delete)
     */
    @Transactional
    public void deleteMyShop(Long sellerId) {
        SellerShop shop = shopRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng của bạn."));
        shopRepository.delete(shop);
    }

    /**
     * Lấy thông tin Shop Public qua Slug (Dành cho khách hàng xem)
     */
    public SellerShopResponse getPublicShopBySlug(String slug) {
        SellerShop shop = shopRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng."));
        
        // Chỉ chặn các shop bị từ chối (REJECTED), cho phép xem cả APPROVED và PENDING
        if (shop.getApprovalStatus() == ApprovalStatus.REJECTED) {
            throw new RuntimeException("Cửa hàng này hiện không hoạt động.");
        }

        SellerShopResponse response = mapToResponse(shop);

        // BẢO MẬT: KHÔNG lộ hồ sơ pháp lý/thuế/định danh ra API công khai.
        // Chỉ chủ shop (GET /api/seller/me/shop) và ADMIN mới được thấy dữ liệu này.
        response.setIdentityNumber(null);
        response.setIdentityType(null);
        response.setTaxCode(null);
        response.setRepresentativeName(null);
        response.setLegalDocuments(null);

        return response;
    }

    /**
     * Admin approve a seller - creates SellerShop if it doesn't exist and sets approval status to APPROVED
     * This ensures that when admin approves a seller, they can immediately start selling
     */
    @Transactional
    public SellerShopResponse adminApproveSeller(Long sellerId) {
        // 0. Chặn sớm sellerId null: trước đây findById(null) ném
        // IllegalArgumentException -> HTTP 500 cho hồ sơ legacy thiếu snapshot seller.id
        if (sellerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thiếu thông tin người bán (sellerId) để duyệt cửa hàng.");
        }

        // 1. Validate seller exists and has SELLER role
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người bán."));
        
        // If user is not yet marked as SELLER, promote them when admin approves
        if (seller.getRole() != UserRole.SELLER) {
            seller.setRole(UserRole.SELLER);
            userRepository.save(seller);
        }

        // 2. Get or create SellerShop
        SellerShop shop = shopRepository.findBySellerId(sellerId)
                .orElseGet(() -> {
                    // Auto-create SellerShop if not exists
                    String slug = generateUniqueSlug(seller.getShopName() != null ? seller.getShopName() : seller.getUsername());
                    SellerShop newShop = SellerShop.builder()
                            .sellerId(sellerId)
                            .seller(com.example.bookstore.model.embedded.UserSnapshot.of(seller))
                            .slug(slug)
                            .shopName(seller.getShopName() != null ? seller.getShopName() : seller.getUsername())
                            .address(seller.getShopAddress() != null ? seller.getShopAddress() : "Chưa cập nhật")
                            .approvalStatus(ApprovalStatus.PENDING)
                            .build();
                    return shopRepository.save(newShop);
                });

        // 3. Backfill dữ liệu người bán còn thiếu (hồ sơ legacy chỉ có sellerId ở cấp document)
        shop.setSellerId(sellerId);
        if (shop.getSeller() == null) {
            shop.setSeller(com.example.bookstore.model.embedded.UserSnapshot.of(seller));
        } else if (shop.getSeller().getId() == null) {
            shop.getSeller().setId(sellerId);
        }

        // 4. Approve the shop
        shop.setApprovalStatus(ApprovalStatus.APPROVED);
        SellerShop approvedShop = shopRepository.save(shop);
        
        return mapToResponse(approvedShop);
    }

    /**
     * Admin duyệt hồ sơ gian hàng theo shopId (dùng cho endpoint PUT /{shopId}/approve).
     *
     * <p>An toàn với dữ liệu legacy: có document shop cũ chỉ lưu {@code sellerId} ở
     * cấp document mà thiếu {@code seller.id} trong snapshot. Trước đây controller gọi
     * {@code shop.getSeller().getId()} → {@code null} rồi {@code userRepository.findById(null)}
     * làm phát sinh {@link IllegalArgumentException} → HTTP 500.</p>
     *
     * <p>Hàm này resolve sellerId từ {@code shop.getSellerId()} trước, tự backfill
     * {@code sellerId}/{@code seller.id} khi lưu, và trả về mã lỗi rõ ràng thay vì 500:</p>
     * <ul>
     *   <li>404 — không tìm thấy hồ sơ gian hàng</li>
     *   <li>409 — hồ sơ thiếu hoàn toàn thông tin người bán</li>
     * </ul>
     */
    @Transactional
    public SellerShopResponse adminApproveSellerByShop(Long shopId) {
        SellerShop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ gian hàng."));

        Long sellerId = resolveSellerId(shop);
        if (sellerId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hồ sơ gian hàng thiếu thông tin người bán (sellerId) — không thể duyệt.");
        }

        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người bán."));

        // Promote user lên SELLER nếu chưa phải (giữ nguyên hành vi cũ)
        if (seller.getRole() != UserRole.SELLER) {
            seller.setRole(UserRole.SELLER);
            userRepository.save(seller);
        }

        // Backfill dữ liệu người bán còn thiếu trước khi duyệt
        shop.setSellerId(sellerId);
        if (shop.getSeller() == null) {
            shop.setSeller(com.example.bookstore.model.embedded.UserSnapshot.of(seller));
        } else if (shop.getSeller().getId() == null) {
            shop.getSeller().setId(sellerId);
        }

        shop.setApprovalStatus(ApprovalStatus.APPROVED);
        return mapToResponse(shopRepository.save(shop));
    }

    /**
     * Lấy sellerId bền vững của shop: ưu tiên field top-level {@code sellerId} của document,
     * chỉ fallback sang snapshot {@code seller.id} khi field top-level bị thiếu.
     */
    public static Long resolveSellerId(SellerShop shop) {
        if (shop == null) return null;
        if (shop.getSellerId() != null) return shop.getSellerId();
        return shop.getSeller() != null ? shop.getSeller().getId() : null;
    }

    /**
     * Generate unique slug from shop name
     * Made public to be used during seller registration and seeding
     */
    public String generateUniqueSlug(String shopName) {
        // Convert to lowercase and replace spaces with hyphens
        String baseSlug = shopName.toLowerCase().replaceAll("\\s+", "-").replaceAll("[^a-z0-9-]", "");
        
        // Check if slug exists, if yes append timestamp
        String slug = baseSlug;
        int counter = 1;
        while (shopRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + System.currentTimeMillis() % 10000;
            counter++;
        }
        
        return slug;
    }

    // --- CÁC HÀM TIỆN ÍCH (HELPER METHODS) ---

    /**
     * Lấy User và validate Role
     */
    private User validateAndGetSeller(Long sellerId) {
        User user = userRepository.findById(sellerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng."));
        
        // Giả sử Entity User của bạn có hàm getRole() hoặc Enum Role
        // Thay đổi phần này cho khớp với cấu trúc Entity User của bạn
        if (user.getRole() != UserRole.SELLER) {
            throw new RuntimeException("Bạn không có quyền đăng ký cửa hàng (Yêu cầu Role SELLER).");
        }
        return user;
    }

    /**
     * Map từ Entity sang Response DTO
     */
    private SellerShopResponse mapToResponse(SellerShop shop) {
        return SellerShopResponse.builder()
                .id(shop.getId())
                .sellerId(resolveSellerId(shop)) // Ưu tiên sellerId cấp document (dữ liệu legacy có thể thiếu seller.id)
                .slug(shop.getSlug())
                .shopName(shop.getShopName())
                .description(shop.getDescription())
                .logoUrl(shop.getLogoUrl())
                .bannerUrl(shop.getBannerUrl())
                .contactEmail(shop.getContactEmail())
                .contactPhone(shop.getContactPhone())
                .address(shop.getAddress())
                .city(shop.getCity())
                .province(shop.getProvince())
                .approvalStatus(shop.getApprovalStatus())
                .followerCount(shop.getFollowerCount())
                .rating(shop.getRating())
                .ratingCount(shop.getRatingCount())
                .createdAt(shop.getCreatedAt())
                .updatedAt(shop.getUpdatedAt())
                .businessType(shop.getBusinessType())
                .taxCode(shop.getTaxCode())
                .identityType(shop.getIdentityType())
                .identityNumber(shop.getIdentityNumber())
                .representativeName(shop.getRepresentativeName())
                .shippingCarriers(shop.getShippingCarriers())
                .legalDocuments(shop.getLegalDocuments())
                .build();
    }

    // ======================================================================
    // HỒ SƠ PHÁP LÝ / THUẾ / ĐỊNH DANH
    // ======================================================================

    /**
     * Áp hồ sơ pháp lý từ request vào entity.
     *
     * <p>CHỈ ghi đè field mà request có gửi (khác null) → client cũ/trường cũ
     * không gửi thì dữ liệu cũ vẫn được giữ nguyên.</p>
     */
    private void applyLegalProfile(SellerShop shop, SellerShopUpsertRequest request) {
        if (shop == null || request == null) return;

        if (request.getBusinessType() != null) {
            shop.setBusinessType(normalizeEnum(request.getBusinessType(), BUSINESS_TYPES));
        }
        if (request.getTaxCode() != null) {
            shop.setTaxCode(trimTo(request.getTaxCode(), 30));
        }
        if (request.getIdentityType() != null) {
            shop.setIdentityType(normalizeEnum(request.getIdentityType(), IDENTITY_TYPES));
        }
        if (request.getIdentityNumber() != null) {
            shop.setIdentityNumber(trimTo(request.getIdentityNumber(), 30));
        }
        if (request.getRepresentativeName() != null) {
            shop.setRepresentativeName(trimTo(request.getRepresentativeName(), 150));
        }

        if (request.getShippingCarriers() != null) {
            List<String> carriers = new ArrayList<>();
            for (String carrier : request.getShippingCarriers()) {
                String value = trimTo(carrier, 60);
                if (value != null && !carriers.contains(value)) carriers.add(value);
                if (carriers.size() >= 12) break;
            }
            shop.setShippingCarriers(carriers);
        }

        if (request.getLegalDocuments() != null) {
            shop.setLegalDocuments(sanitizeLegalDocuments(shop.getLegalDocuments(), request.getLegalDocuments()));
        }
    }

    /** Chuẩn hoá ảnh tài liệu; giữ lại uploadedAt cũ nếu URL không đổi. */
    private List<SellerShop.LegalDocument> sanitizeLegalDocuments(
            List<SellerShop.LegalDocument> existing,
            List<SellerShopUpsertRequest.LegalDocumentDto> requested) {

        Map<String, LocalDateTime> uploadedAtByUrl = new HashMap<>();
        if (existing != null) {
            for (SellerShop.LegalDocument doc : existing) {
                if (doc != null && doc.getImageUrl() != null && doc.getUploadedAt() != null) {
                    uploadedAtByUrl.put(doc.getImageUrl(), doc.getUploadedAt());
                }
            }
        }

        List<SellerShop.LegalDocument> result = new ArrayList<>();
        for (SellerShopUpsertRequest.LegalDocumentDto dto : requested) {
            if (dto == null) continue;
            String imageUrl = sanitizeImageUrl(dto.getImageUrl());
            if (imageUrl == null) continue; // bo qua tai lieu khong co anh hop le
            result.add(SellerShop.LegalDocument.builder()
                    .type(normalizeEnum(dto.getType(), LEGAL_DOC_TYPES))
                    .label(trimTo(dto.getLabel(), 150))
                    .imageUrl(imageUrl)
                    .fileId(com.example.bookstore.service.storage.FileUrls.gridFsIdOf(imageUrl))
                    .note(trimTo(dto.getNote(), 300))
                    .uploadedAt(uploadedAtByUrl.getOrDefault(imageUrl, LocalDateTime.now()))
                    .build());
            if (result.size() >= 10) break;
        }
        return result;
    }

    private static String trimTo(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static String normalizeEnum(String value, Set<String> allowed) {
        if (value == null) return null;
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(upper) ? upper : null;
    }

    /** Chỉ chấp nhận URL nội bộ do hệ thống sinh (chống javascript:, data:, http ngoài). */
    private static String sanitizeImageUrl(String url) {
        String value = trimTo(url, 400);
        if (value == null) return null;
        for (String prefix : ALLOWED_IMAGE_PREFIXES) {
            if (value.startsWith(prefix) && value.length() > prefix.length()) return value;
        }
        return null;
    }

    /**
     * Suy ra ObjectId GridFS từ URL ảnh ({@code /api/files/{id}}) để lưu kèm
     * {@code logoFileId}/{@code bannerFileId} — nhờ vậy file trong GridFS truy vết
     * được ngay từ document shop. URL legacy (/uploads/..., /images/...) → null.
     */
    private static void applyFileIds(SellerShop shop) {
        shop.setLogoFileId(com.example.bookstore.service.storage.FileUrls.gridFsIdOf(shop.getLogoUrl()));
        shop.setBannerFileId(com.example.bookstore.service.storage.FileUrls.gridFsIdOf(shop.getBannerUrl()));
    }


}