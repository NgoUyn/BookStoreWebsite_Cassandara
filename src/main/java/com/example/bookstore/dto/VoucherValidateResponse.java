package com.example.bookstore.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VoucherValidateResponse {
    private String code;
    private String name;
    private String discountType;
    private Double discountValue;
    private Double discountAmount;
    private Double maxDiscountAmount;
    private Double minOrderAmount;
    private Long sellerId;
    private String sellerShopName;
}
