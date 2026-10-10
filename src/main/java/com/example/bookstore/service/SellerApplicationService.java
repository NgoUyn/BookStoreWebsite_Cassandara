package com.example.bookstore.service;

import com.example.bookstore.dto.SellerApplicationRequest;
import com.example.bookstore.model.SellerShop;
import com.example.bookstore.model.embedded.UserSnapshot;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.model.User;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * NGHIỆP VỤ HỒ SƠ ĐĂNG KÝ NGƯỜI BÁN (buyer gửi yêu cầu mở cửa hàng).
 *
 * <p>Tách khỏi AuthService cho dễ bảo trì: chỉ tạo document {@code seller_shops}
 * ở trạng thái PENDING với đầy đủ thông tin shop, thông tin pháp lý/thuế/định danh
 * và ảnh tài liệu (legalDocuments[]).</p>
 *
 * <p>LUÔN chuẩn hoá + whitelist dữ liệu từ client vì đây là dữ liệu nhạy cảm và
 * được hiển thị lại trên trang admin.</p>
 */
@Service
public class SellerApplicationService {

    private static final Set<String> BUSINESS_TYPES = Set.of("INDIVIDUAL", "HOUSEHOLD", "ENTERPRISE", "SPECIAL");
    private static final Set<String> IDENTITY_TYPES = Set.of("CCCD", "CMND", "PASSPORT");
    private static final Set<String> LEGAL_DOC_TYPES = Set.of(
            "IDENTITY", "BUSINESS_REGISTRATION", "TAX", "SPECIAL_LICENSE", "BRAND_DOC");
    private static final Set<String> ALLOWED_URL_PREFIXES = Set.of("/uploads/legal-docs/", "/uploads/shops/", "/images/");

    @Autowired
    private SellerShopRepository sellerShopRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SellerShopService sellerShopService;

    /**
     * Tạo hồ sơ đăng ký (SellerShop PENDING) từ dữ liệu buyer gửi lên.
     * Không đổi role — admin duyệt mới nâng lên SELLER.
     */
    public SellerShop submitApplication(Long userId, SellerApplicationRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (sellerShopRepository.findBySellerId(userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn đã gửi yêu cầu hoặc đã có cửa hàng.");
        }

        SellerApplicationRequest req = request == null ? new SellerApplicationRequest() : request;
        String shopName = text(req.getShopName(), user.getUsername(), 150);
        String slug = text(req.getSlug(), null, 150);
        String description = text(req.getDescription(), null, 2000);
        if (description == null) {
            description = "Gian hàng của " + shopName;
        }

        SellerShop newShop = SellerShop.builder()
                .sellerId(user.getId())
                .seller(UserSnapshot.of(user))
                .slug(sellerShopService.generateUniqueSlug(slug != null ? slug : shopName))
                .shopName(shopName)
                .description(description)
                .contactEmail(text(req.getContactEmail(), user.getEmail(), 190))
                .contactPhone(text(req.getContactPhone(), user.getPhone(), 30))
                .address(text(req.getAddress(), "Chưa cập nhật", 400))
                .city(text(req.getCity(), "Chưa cập nhật", 120))
                .province(text(req.getProvince(), null, 120))
                .businessType(normalizeEnum(req.getBusinessType(), BUSINESS_TYPES))
                .taxCode(text(req.getTaxCode(), null, 30))
                .identityType(normalizeEnum(req.getIdentityType(), IDENTITY_TYPES))
                .identityNumber(text(req.getIdentityNumber(), null, 30))
                .representativeName(text(req.getRepresentativeName(), null, 150))
                .shippingCarriers(sanitizeCarriers(req.getShippingCarriers()))
                .legalDocuments(sanitizeLegalDocuments(req.getLegalDocuments()))
                .approvalStatus(ApprovalStatus.PENDING)
                .followerCount(0)
                .rating(0.0)
                .ratingCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .schemaVersion(1)
                .build();

        return sellerShopRepository.save(newShop);
    }

    // ------------------------------------------------------------------
    // Helpers chuẩn hoá dữ liệu vào
    // ------------------------------------------------------------------

    private static String text(String value, String fallback, int maxLength) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return fallback;
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static String normalizeEnum(String value, Set<String> allowed) {
        if (value == null) return null;
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(upper) ? upper : null;
    }

    private static List<String> sanitizeCarriers(List<String> carriers) {
        List<String> result = new ArrayList<>();
        if (carriers == null || carriers.isEmpty()) return result;
        for (String carrier : carriers) {
            String value = text(carrier, null, 60);
            if (value != null && !result.contains(value)) result.add(value);
            if (result.size() >= 12) break;
        }
        return result;
    }

    private static List<SellerShop.LegalDocument> sanitizeLegalDocuments(
            List<SellerApplicationRequest.LegalDocumentDto> documents) {
        List<SellerShop.LegalDocument> result = new ArrayList<>();
        if (documents == null) return result;
        for (SellerApplicationRequest.LegalDocumentDto doc : documents) {
            if (doc == null) continue;
            String imageUrl = sanitizeImageUrl(doc.getImageUrl());
            if (imageUrl == null) continue; // bo qua tai lieu khong co anh hop le
            result.add(SellerShop.LegalDocument.builder()
                    .type(normalizeEnum(doc.getType(), LEGAL_DOC_TYPES))
                    .label(text(doc.getLabel(), null, 150))
                    .imageUrl(imageUrl)
                    .note(text(doc.getNote(), null, 300))
                    .uploadedAt(LocalDateTime.now())
                    .build());
            if (result.size() >= 10) break;
        }
        return result;
    }

    /** Chỉ chấp nhận URL nội bộ do hệ thống sinh (chống javascript:, data:, http ngoài). */
    private static String sanitizeImageUrl(String url) {
        String value = text(url, null, 400);
        if (value == null) return null;
        for (String prefix : ALLOWED_URL_PREFIXES) {
            if (value.startsWith(prefix) && value.length() > prefix.length()) {
                return value;
            }
        }
        return null;
    }
}
