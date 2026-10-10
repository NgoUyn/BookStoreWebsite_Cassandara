package com.example.bookstore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerShopUpsertRequest {
    
    @NotBlank(message = "Tên Shop không được để trống")
    private String shopName;

    @NotBlank(message = "Slug không được để trống")
    @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$",message = "Slug chỉ được chứa chữ thường, số và dấu gạch ngang")
    private String slug;
    private String description;
    private String logoUrl;
    private String bannerUrl;
    @Email(message = "Email không hợp lệ")
    private String contactEmail;
    private String contactPhone;
    private String address;
    private String city;
    private String province;

    // ------------------------------------------------------------------
    // Hồ sơ pháp lý / thuế / định danh (bổ sung cho trang hồ sơ gian hàng).
    // KHÔNG đặt @NotBlank để không phá vỡ dữ liệu cũ chưa có thông tin pháp lý.
    // ------------------------------------------------------------------
    private String businessType;      // INDIVIDUAL | HOUSEHOLD | ENTERPRISE | SPECIAL
    private String taxCode;
    private String identityType;      // CCCD | CMND | PASSPORT
    private String identityNumber;
    private String representativeName;
    private List<String> shippingCarriers;
    private List<LegalDocumentDto> legalDocuments;

    /** Một ảnh tài liệu pháp lý (đã upload qua /api/seller-applications/upload-document). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegalDocumentDto {
        private String type;      // IDENTITY | BUSINESS_REGISTRATION | TAX | SPECIAL_LICENSE | BRAND_DOC
        private String label;
        private String imageUrl;  // /uploads/legal-docs/{uuid}.ext
        private String note;
    }
}
