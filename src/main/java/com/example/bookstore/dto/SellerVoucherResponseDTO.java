package com.example.bookstore.dto;

import com.example.bookstore.model.Coupon.CouponType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Seller to view their own vouchers with usage statistics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerVoucherResponseDTO {
    private Long id;
    private String code;
    private String description;
    private CouponType discountType;        // FIXED or PERCENT
    private Integer discountValue;
    private Integer minOrderAmount;
    private Double maxDiscountAmount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer usageLimit;
    private Integer usedCount;              // How many times used
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Usage statistics
    private Integer remainingUsage;         // usageLimit - usedCount (NULL if unlimited)
    private Boolean isExpired;              // Whether coupon has expired
    private Boolean isNotStarted;           // Whether coupon hasn't started yet
    private String status;                  // ACTIVE, EXPIRED, NOT_STARTED, INACTIVE
}
