package com.example.bookstore.controller;

import com.example.bookstore.model.Coupon;
import com.example.bookstore.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /**
     * Validate coupon for checkout (Public endpoint)
     * GET /api/coupons/{code}/validate?orderAmount=500000&sellerIds=1,2,3
     * sellerIds is optional - if provided, validates coupon belongs to one of the sellers in cart
     */
    @GetMapping("/{code}/validate")
    public ResponseEntity<?> validateCoupon(
            @PathVariable String code,
            @RequestParam Integer orderAmount,
            @RequestParam(required = false) String sellerIds
    ) {
        try {
            Coupon coupon;
            if (sellerIds != null && !sellerIds.isEmpty()) {
                // Parse seller IDs and validate against seller list (prevent cross-seller)
                List<Long> sellerIdList = Arrays.stream(sellerIds.split(","))
                        .map(String::trim)
                        .map(Long::parseLong)
                        .collect(Collectors.toList());
                coupon = couponService.validateCouponForSellerList(code, sellerIdList, orderAmount);
            } else {
                // Fallback to basic validation (no seller check)
                coupon = couponService.validateCoupon(code, orderAmount);
            }

            // Calculate discount
            Integer discount = coupon.calculateDiscount(orderAmount);
            Integer finalAmount = orderAmount - discount;

            Map<String, Object> successBody = new HashMap<>();
            successBody.put("valid", true);
            successBody.put("code", coupon.getCode());
            successBody.put("description", coupon.getDescription());
            successBody.put("type", coupon.getType() != null ? coupon.getType().toString() : null);
            successBody.put("originalAmount", orderAmount);
            successBody.put("discount", discount);
            successBody.put("finalAmount", finalAmount);
            successBody.put("message", "Mã giảm giá hợp lệ");
            return ResponseEntity.ok(successBody);
        } catch (Exception e) {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("valid", false);
            errorBody.put("code", code);
            errorBody.put("error", e.getMessage() != null ? e.getMessage() : "Lỗi không xác định");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
        }
    }

    /**
     * Get valid coupons for a specific seller (Public endpoint)
     * GET /api/coupons/seller/{sellerId}
     */
    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<List<Coupon>> getSellerCoupons(@PathVariable Long sellerId) {
        return ResponseEntity.ok(couponService.getValidCouponsForSeller(sellerId));
    }


    /**
     * Get coupon details (Public endpoint)
     * GET /api/coupons/{code}
     */
    @GetMapping("/{code}")
    public ResponseEntity<?> getCouponByCode(@PathVariable String code) {
        try {
            Optional<Coupon> coupon = couponService.getCouponByCode(code);
            if (coupon.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "Mã giảm giá không tồn tại"
                ));
            }

            Coupon c = coupon.get();
            Map<String, Object> detailBody = new HashMap<>();
            detailBody.put("code", c.getCode());
            detailBody.put("description", c.getDescription());
            detailBody.put("type", c.getType() != null ? c.getType().toString() : null);
            detailBody.put("amount", c.getAmount());
            detailBody.put("minOrderAmount", c.getMinOrderAmount());
            detailBody.put("isValid", c.isValid());
            return ResponseEntity.ok(detailBody);
        } catch (Exception e) {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Lỗi: " + (e.getMessage() != null ? e.getMessage() : "không xác định"));
            return ResponseEntity.badRequest().body(errorBody);
        }
    }

    /**
     * Apply coupon to order (use coupon)
     * POST /api/coupons/{code}/apply
     */
    @PostMapping("/{code}/apply")
    public ResponseEntity<?> applyCoupon(@PathVariable String code) {
        try {
            couponService.useCoupon(code);
            return ResponseEntity.ok(Map.of(
                    "message", "Mã giảm giá đã được sử dụng"
            ));
        } catch (Exception e) {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", e.getMessage() != null ? e.getMessage() : "Lỗi không xác định");
            return ResponseEntity.badRequest().body(errorBody);
        }
    }
}
