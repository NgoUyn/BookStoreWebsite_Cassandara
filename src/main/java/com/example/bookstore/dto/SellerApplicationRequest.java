package com.example.bookstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload buyer gửi lên khi bấm "Gửi yêu cầu trở thành người bán".
 *
 * <p>Cố ý KHÔNG dùng @NotBlank cho các field mới để không phá vỡ luồng cũ
 * (application cũ chỉ gửi 6 field cơ bản). Việc kiểm tra/chuẩn hoá được
 * thực hiện trong {@code SellerApplicationService}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerApplicationRequest {

    // --- Thông tin cửa hàng ---
    private String shopName;
    private String slug;
    private String description;
    private String address;
    private String city;
    private String province;
    private String contactEmail;
    private String contactPhone;

    // --- Loại hình kinh doanh / thuế / định danh ---
    private String businessType;      // INDIVIDUAL | HOUSEHOLD | ENTERPRISE | SPECIAL
    private String taxCode;           // MST / ma so doanh nghiep
    private String identityType;      // CCCD | CMND | PASSPORT
    private String identityNumber;    // so giay to tuy than
    private String representativeName;

    private List<String> shippingCarriers;

    private List<LegalDocumentDto> legalDocuments;

    /** Một ảnh tài liệu pháp lý đã upload trước đó (qua /api/seller-applications/upload-document). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegalDocumentDto {
        private String type;      // IDENTITY | BUSINESS_REGISTRATION | TAX | SPECIAL_LICENSE | BRAND_DOC
        private String label;     // ten hien thi, vi du "CCCD mat truoc"
        private String imageUrl;  // /uploads/legal-docs/{uuid}.ext
        private String note;
    }
}
