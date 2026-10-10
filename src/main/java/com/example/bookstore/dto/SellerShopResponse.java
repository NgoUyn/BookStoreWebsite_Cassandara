package com.example.bookstore.dto;
import com.example.bookstore.model.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerShopResponse {
    
    private Long id;
    private Long sellerId; // chi duoc tra ve id cua chu shop
    private String slug;
    private String shopName;
    private String description;
    private String logoUrl;
    private String bannerUrl;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private String city;
    private String province;
    private ApprovalStatus approvalStatus;
    private Integer followerCount;
    private Double rating;
    private Integer ratingCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String rejectionReason;

    // ------------------------------------------------------------------
    // Hồ sơ pháp lý / thuế / định danh (trả về cho chính chủ shop + admin)
    // ------------------------------------------------------------------
    private String businessType;
    private String taxCode;
    private String identityType;
    private String identityNumber;
    private String representativeName;
    private java.util.List<String> shippingCarriers;
    private java.util.List<com.example.bookstore.model.SellerShop.LegalDocument> legalDocuments;

}
