package com.example.bookstore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
