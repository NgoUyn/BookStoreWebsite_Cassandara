package com.example.bookstore.dto;

import com.example.bookstore.model.Coupon.CouponType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VoucherCreateDTO {
    private Long sellerId;  // Seller who owns this voucher
    private String code;
    private String name;    // description in DB
    private CouponType discountType;  // FIXED or PERCENT
    private Double discountValue;
    private Double minOrderAmount;
    private Double maxDiscountAmount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer usageLimit;
}
