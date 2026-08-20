package com.example.bookstore.controller;

import com.example.bookstore.dto.NotificationCreateRequest;
import com.example.bookstore.dto.SellerVoucherCreateDTO;
import com.example.bookstore.dto.SellerVoucherResponseDTO;
import com.example.bookstore.model.Coupon;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.NotificationPriority;
import com.example.bookstore.model.enums.NotificationType;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.service.CouponService;
import com.example.bookstore.service.NotificationService;
import com.example.bookstore.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/seller/vouchers")
@RequiredArgsConstructor
@Slf4j
public class SellerVoucherController {

    private final CouponService couponService;
    private final UserService userService;
    private final NotificationService notificationService;


    /**
     * Get seller's current user from JwtAuthenticatedPrincipal
     * Uses the userId/sellerId directly from the JWT principal
     * instead of looking up by username (which fails because
     * JwtAuthenticatedPrincipal.toString() is not a username).
     */
    private Long getCurrentSellerId(JwtAuthenticatedPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Vui lòng đăng nhập để truy cập chức năng này"
            );
        }

        // Use sellerId from principal if available, otherwise fall back to userId
        Long sellerId = principal.sellerId() != null ? principal.sellerId() : principal.userId();

        // Verify user exists and has SELLER role
        User seller = userService.getUserById(sellerId);
        if (seller.getRole() != com.example.bookstore.model.enums.UserRole.SELLER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền truy cập chức năng người bán"
            );
        }

        return seller.getId();
    }


    /**
     * List seller's vouchers with pagination
     * GET /api/seller/vouchers?page=0&size=10&search=SAVE
     */
    @GetMapping
    public ResponseEntity<Page<?>> listSellerVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal) {

        Long sellerId = getCurrentSellerId(principal);

        Page<Coupon> result = search != null && !search.isEmpty()
                ? couponService.searchSellerCoupons(sellerId, search, page, size)
                : couponService.listSellerCoupons(sellerId, page, size);

        // Convert to SellerVoucherResponseDTO
        Page<SellerVoucherResponseDTO> response = result.map(this::mapToResponseDTO);
        return ResponseEntity.ok(response);
    }

    /**
     * Get specific voucher details
     * GET /api/seller/vouchers/{voucherId}
     */
    @GetMapping("/{voucherId}")
    public ResponseEntity<SellerVoucherResponseDTO> getVoucher(
            @PathVariable Long voucherId,
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal) {

        Long sellerId = getCurrentSellerId(principal);
        Coupon coupon = couponService.getSellerVoucher(voucherId, sellerId);
        return ResponseEntity.ok(mapToResponseDTO(coupon));
    }

    /**
     * Create new voucher
     * POST /api/seller/vouchers
     */
    @PostMapping
    public ResponseEntity<SellerVoucherResponseDTO> createVoucher(
            @RequestBody SellerVoucherCreateDTO createDTO,
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal) {

        Long sellerId = getCurrentSellerId(principal);
        User seller = userService.getUserById(sellerId);

        // Map DTO to entity
        Coupon coupon = new Coupon();
        coupon.setCode(createDTO.getCode().toUpperCase());
        coupon.setDescription(createDTO.getDescription());
        coupon.setType(createDTO.getDiscountType());
        coupon.setAmount(createDTO.getDiscountValue().intValue());
        coupon.setMinOrderAmount(createDTO.getMinOrderAmount() != null ? createDTO.getMinOrderAmount().intValue() : null);
        coupon.setMaxDiscountAmount(createDTO.getMaxDiscountAmount());
        coupon.setStartDate(createDTO.getStartDate());
        coupon.setExpiresAt(createDTO.getEndDate());
        coupon.setTotalQuantity(createDTO.getUsageLimit() != null ? createDTO.getUsageLimit() : -1);
        coupon.setSeller(seller);
        coupon.setActive(true);

        Coupon created = couponService.createSellerCoupon(coupon, sellerId);

        // Broadcast notification to all buyers about new coupon
        try {
            String shopName = seller.getShopName() != null ? seller.getShopName() : seller.getUsername();
            String discountDesc = created.getType() == Coupon.CouponType.FIXED
                    ? String.format("%,dđ", created.getAmount())
                    : created.getAmount() + "%";
            String maxDesc = created.getMaxDiscountAmount() != null
                    ? String.format(" (tối đa %,.0fđ)", created.getMaxDiscountAmount()) : "";
            String minDesc = created.getMinOrderAmount() != null
                    ? String.format(" - Đơn từ %,dđ", created.getMinOrderAmount()) : "";

            NotificationCreateRequest notifReq = new NotificationCreateRequest();
            notifReq.setUserId(null); // broadcast to all
            notifReq.setType(NotificationType.COUPON_CREATED);
            notifReq.setTitle("🎉 Mã giảm giá mới từ " + shopName);
            notifReq.setMessage(String.format("Mã \"%s\" - Giảm %s%s%s",
                    created.getCode(), discountDesc, maxDesc, minDesc));
            notifReq.setPayloadJson(String.format(
                    "{\"couponId\":%d,\"code\":\"%s\",\"shopName\":\"%s\"}",
                    created.getId(), created.getCode(), shopName));
            notifReq.setPriority(NotificationPriority.NORMAL);

            notificationService.createNotification(sellerId, null, notifReq);
        } catch (Exception e) {
            log.warn("Failed to broadcast coupon notification: {}", e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponseDTO(created));

    }

    /**
     * Update voucher details (only certain fields)
     * PUT /api/seller/vouchers/{voucherId}
     */
    @PutMapping("/{voucherId}")
    public ResponseEntity<SellerVoucherResponseDTO> updateVoucher(
            @PathVariable Long voucherId,
            @RequestBody SellerVoucherCreateDTO updateDTO,
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal) {

        Long sellerId = getCurrentSellerId(principal);

        Coupon updates = new Coupon();
        updates.setDescription(updateDTO.getDescription());

        Coupon updated = couponService.updateSellerCoupon(voucherId, sellerId, updates);
        return ResponseEntity.ok(mapToResponseDTO(updated));
    }

    /**
     * Deactivate voucher (soft delete)
     * DELETE /api/seller/vouchers/{voucherId}
     */
    @DeleteMapping("/{voucherId}")
    public ResponseEntity<Void> deactivateVoucher(
            @PathVariable Long voucherId,
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal) {

        Long sellerId = getCurrentSellerId(principal);
        couponService.deactivateSellerCoupon(voucherId, sellerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Helper method to map Coupon to SellerVoucherResponseDTO with statistics
     */
    private SellerVoucherResponseDTO mapToResponseDTO(Coupon coupon) {
        LocalDateTime now = LocalDateTime.now();
        boolean isExpired = coupon.getExpiresAt() != null && now.isAfter(coupon.getExpiresAt());
        boolean isNotStarted = coupon.getStartDate() != null && now.isBefore(coupon.getStartDate());
        
        String status;
        if (!coupon.isActive()) {
            status = "INACTIVE";
        } else if (isNotStarted) {
            status = "NOT_STARTED";
        } else if (isExpired) {
            status = "EXPIRED";
        } else if (coupon.getTotalQuantity() > 0 && coupon.getUsedCount() >= coupon.getTotalQuantity()) {
            status = "EXHAUSTED";
        } else {
            status = "ACTIVE";
        }

        Integer remainingUsage = null;
        if (coupon.getTotalQuantity() > 0) {
            remainingUsage = coupon.getTotalQuantity() - coupon.getUsedCount();
        }

        return SellerVoucherResponseDTO.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .description(coupon.getDescription())
                .discountType(coupon.getType())
                .discountValue(coupon.getAmount())
                .minOrderAmount(coupon.getMinOrderAmount())
                .maxDiscountAmount(coupon.getMaxDiscountAmount())
                .startDate(coupon.getStartDate())
                .endDate(coupon.getExpiresAt())
                .usageLimit(coupon.getTotalQuantity() > 0 ? coupon.getTotalQuantity() : null)
                .usedCount(coupon.getUsedCount())
                .isActive(coupon.isActive())
                .createdAt(coupon.getCreatedAt())
                .updatedAt(coupon.getUpdatedAt())
                .remainingUsage(remainingUsage)
                .isExpired(isExpired)
                .isNotStarted(isNotStarted)
                .status(status)
                .build();
    }
}
