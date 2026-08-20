package com.example.bookstore.service;

import com.example.bookstore.model.Coupon;
import com.example.bookstore.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    /**
     * Get coupon by code with validation
     */
    public Optional<Coupon> getCouponByCode(String code) {
        return couponRepository.findByCodeIgnoreCase(code);
    }

    /**
     * Validate coupon for order
     * Returns coupon if valid, throws exception if invalid
     */
    public Coupon validateCoupon(String code, Integer orderAmount) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã giảm giá không tồn tại"));

        // Check if coupon is active
        if (!coupon.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá đã bị vô hiệu hóa");
        }

        // Check if coupon is expired
        if (coupon.getExpiresAt() != null && LocalDateTime.now().isAfter(coupon.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Mã giảm giá đã hết hạn");
        }

        // Check if coupon has exceeded usage limit
        if (coupon.getTotalQuantity() > 0 && coupon.getUsedCount() >= coupon.getTotalQuantity()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Mã giảm giá đã hết lượt sử dụng");
        }

        // Check minimum order amount
        if (coupon.getMinOrderAmount() != null && orderAmount < coupon.getMinOrderAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn hàng tối thiểu " + coupon.getMinOrderAmount() + " VND");
        }

        return coupon;
    }

    /**
     * Calculate discount for order
     */
    public Integer calculateDiscount(String code, Integer orderAmount) {
        Coupon coupon = validateCoupon(code, orderAmount);
        return coupon.calculateDiscount(orderAmount);
    }

    /**
     * Use coupon (increment usedCount)
     */
    @Transactional
    public void useCoupon(String code) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã giảm giá không tồn tại"));

        coupon.setUsedCount(coupon.getUsedCount() + 1);
        coupon.setUpdatedAt(LocalDateTime.now());
        couponRepository.save(coupon);
    }

    /**
     * Get all valid coupons (admin listing)
     */
    public List<Coupon> getAllValidCoupons() {
        return couponRepository.findAllValidCoupons();
    }

    /**
     * Get all active coupons
     */
    public List<Coupon> getAllActiveCoupons() {
        return couponRepository.findByIsActiveTrue();
    }

    /**
     * Get coupons with pagination
     */
    public Page<Coupon> getCouponsPageable(int page, int size, boolean isActive) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return couponRepository.findByIsActive(isActive, pageable);
    }

    /**
     * Search coupons
     */
    public Page<Coupon> searchCoupons(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return couponRepository.searchCoupons(keyword, pageable);
    }

    /**
     * Create new coupon (Admin)
     */
    public Coupon createCoupon(Coupon coupon) {
        // Check code uniqueness
        if (couponRepository.existsByCodeIgnoreCase(coupon.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá đã tồn tại");
        }

        // Validate coupon data
        if (coupon.getAmount() == null || coupon.getAmount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền giảm phải > 0");
        }

        coupon.setCreatedAt(LocalDateTime.now());
        coupon.setUsedCount(0);
        return couponRepository.save(coupon);
    }

    /**
     * Update existing coupon (Admin)
     */
    @Transactional
    public Coupon updateCoupon(Long id, Coupon updates) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã giảm giá không tồn tại"));

        // Only allow updating certain fields
        if (updates.getDescription() != null) {
            coupon.setDescription(updates.getDescription());
        }
        if (updates.getAmount() != null && updates.getAmount() > 0) {
            coupon.setAmount(updates.getAmount());
        }
        if (updates.getMinOrderAmount() != null) {
            coupon.setMinOrderAmount(updates.getMinOrderAmount());
        }
        if (updates.getExpiresAt() != null) {
            coupon.setExpiresAt(updates.getExpiresAt());
        }
        if (updates.getTotalQuantity() != null) {
            coupon.setTotalQuantity(updates.getTotalQuantity());
        }
        coupon.setActive(updates.isActive());
        coupon.setUpdatedAt(LocalDateTime.now());

        return couponRepository.save(coupon);
    }

    /**
     * Delete coupon (Admin) - soft delete by setting isActive=false
     */
    @Transactional
    public void deactivateCoupon(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã giảm giá không tồn tại"));

        coupon.setActive(false);
        coupon.setUpdatedAt(LocalDateTime.now());
        couponRepository.save(coupon);
    }

    /**
     * Permanently delete coupon (Admin)
     */
    public void deleteCoupon(Long id) {
        if (!couponRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã giảm giá không tồn tại");
        }
        couponRepository.deleteById(id);
    }

    /**
     * Get coupon by ID (Admin)
     */
    public Coupon getCouponById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã giảm giá không tồn tại"));
    }

    /**
     * Get all coupons pageable (Admin)
     */
    public Page<Coupon> getAllCouponsPageable(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return couponRepository.findAll(pageable);
    }

    // ========== SELLER-SPECIFIC METHODS ==========

    /**
     * Validate coupon for a specific seller (prevent cross-seller usage)
     * Ensures buyer is using coupon from the seller they're purchasing from
     */
    public Coupon validateCouponForSeller(String code, Long sellerId, Integer orderAmount) {
        Coupon coupon = couponRepository.findValidVoucherForSeller(code, sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "Mã giảm giá không có sẵn cho cửa hàng này"));

        // Check minimum order amount
        if (coupon.getMinOrderAmount() != null && orderAmount < coupon.getMinOrderAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn hàng tối thiểu " + coupon.getMinOrderAmount() + " VND");
        }

        return coupon;
    }

    /**
     * Get seller's coupon by ID (verify ownership)
     */
    public Coupon getSellerVoucher(Long voucherId, Long sellerId) {
        Coupon coupon = couponRepository.findById(voucherId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã giảm giá không tồn tại"));

        // Verify ownership
        if (coupon.getSeller() == null || !coupon.getSeller().getId().equals(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập mã giảm giá này");
        }

        return coupon;
    }

    /**
     * List seller's coupons with pagination
     */
    public Page<Coupon> listSellerCoupons(Long sellerId, int page, int size) {
        // Không thêm Sort vào Pageable vì method name findBySeller_IdOrderByCreatedAtDesc
        // đã tự động thêm ORDER BY created_at DESC. Nếu thêm Sort nữa sẽ bị duplicate
        // ORDER BY trên SQL Server (lỗi: "A column has been specified more than once in the order by list").
        Pageable pageable = PageRequest.of(page, size);
        return couponRepository.findBySeller_IdOrderByCreatedAtDesc(sellerId, pageable);
    }


    /**
     * Search seller's coupons
     */
    public Page<Coupon> searchSellerCoupons(Long sellerId, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return couponRepository.searchSellerCoupons(sellerId, keyword, pageable);
    }

    /**
     * Create coupon for seller
     */
    @Transactional
    public Coupon createSellerCoupon(Coupon coupon, Long sellerId) {
        // Verify code uniqueness within seller's coupons
        if (couponRepository.existsByCodeIgnoreCaseAndSeller_Id(coupon.getCode(), sellerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá đã tồn tại cho cửa hàng của bạn");
        }

        // Validate coupon data
        if (coupon.getAmount() == null || coupon.getAmount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền giảm phải > 0");
        }

        if (coupon.getType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại giảm giá là bắt buộc");
        }

        // Validate date ranges
        if (coupon.getStartDate() != null && coupon.getExpiresAt() != null 
            && coupon.getStartDate().isAfter(coupon.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu phải trước ngày kết thúc");
        }

        coupon.setCreatedAt(LocalDateTime.now());
        coupon.setUsedCount(0);
        // totalQuantity will default to -1 (unlimited) if not set

        return couponRepository.save(coupon);
    }

    /**
     * Update seller's own coupon
     */
    @Transactional
    public Coupon updateSellerCoupon(Long voucherId, Long sellerId, Coupon updates) {
        Coupon coupon = getSellerVoucher(voucherId, sellerId);

        // Only allow certain fields to be updated
        if (updates.getDescription() != null) {
            coupon.setDescription(updates.getDescription());
        }
        // Note: isActive is primitive boolean, no null check needed
        coupon.setActive(updates.isActive());

        // Note: Cannot update code, amount, type after creation (business rule)
        // Can update: description, isActive

        coupon.setUpdatedAt(LocalDateTime.now());
        return couponRepository.save(coupon);
    }

    /**
     * Deactivate (soft delete) seller's coupon
     */
    @Transactional
    public void deactivateSellerCoupon(Long voucherId, Long sellerId) {
        Coupon coupon = getSellerVoucher(voucherId, sellerId);
        coupon.setActive(false);
        coupon.setUpdatedAt(LocalDateTime.now());
        couponRepository.save(coupon);
    }

    /**
     * Get all valid coupons for a seller (for display to buyers)
     */
    public List<Coupon> getValidCouponsForSeller(Long sellerId) {
        return couponRepository.findAllValidVouchersForSeller(sellerId);
    }

    /**
     * Validate coupon against a list of seller IDs (prevent cross-seller usage).
     * If coupon has no seller (global/admin coupon), it's valid for any seller.
     * If coupon belongs to a specific seller, it must be in the sellerIds list.
     */
    public Coupon validateCouponForSellerList(String code, List<Long> sellerIds, Integer orderAmount) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã giảm giá không tồn tại"));

        // Check basic validity
        if (!coupon.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá đã bị vô hiệu hóa");
        }
        if (coupon.getExpiresAt() != null && LocalDateTime.now().isAfter(coupon.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Mã giảm giá đã hết hạn");
        }
        if (coupon.getTotalQuantity() > 0 && coupon.getUsedCount() >= coupon.getTotalQuantity()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Mã giảm giá đã hết lượt sử dụng");
        }
        if (coupon.getMinOrderAmount() != null && orderAmount < coupon.getMinOrderAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn hàng tối thiểu " + coupon.getMinOrderAmount() + " VND");
        }

        // Check start date
        if (coupon.getStartDate() != null && LocalDateTime.now().isBefore(coupon.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá chưa đến thời gian sử dụng");
        }

        // CRITICAL: Prevent cross-seller usage
        // If coupon has a seller, it must be one of the sellers in the cart
        if (coupon.getSeller() != null) {
            Long couponSellerId = coupon.getSeller().getId();
            if (sellerIds == null || sellerIds.isEmpty() || !sellerIds.contains(couponSellerId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Mã giảm giá không áp dụng được cho cửa hàng trong giỏ hàng của bạn");
            }
        }

        return coupon;
    }
}

