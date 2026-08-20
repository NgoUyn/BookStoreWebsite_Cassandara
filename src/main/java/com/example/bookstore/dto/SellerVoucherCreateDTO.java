package com.example.bookstore.dto;

import com.example.bookstore.model.Coupon.CouponType;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.bookstore.config.CouponTypeDeserializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Seller to create their own vouchers
 * sellerId is automatically set from authenticated user
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerVoucherCreateDTO {
    private String code;                    // Voucher code (e.g., "SELLER_SAVE10")
    private String description;             // Description of the voucher
    @JsonDeserialize(using = CouponTypeDeserializer.class)
    private CouponType discountType;        // FIXED or PERCENT
    private Integer discountValue;          // Discount amount/percentage
    private Integer minOrderAmount;         // Minimum order value to apply
    private Double maxDiscountAmount;       // Max discount cap (for PERCENT type)
    private LocalDateTime startDate;        // When voucher becomes active
    private LocalDateTime endDate;          // When voucher expires
    private Integer usageLimit;             // Usage limit (-1 = unlimited, NULL = unlimited)
}
