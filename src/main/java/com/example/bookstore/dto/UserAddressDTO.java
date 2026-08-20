package com.example.bookstore.dto;

import com.example.bookstore.model.UserAddress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddressDTO {
    private Long id;
    private String addressType;
    private String recipientName;
    private String recipientPhone;
    private String addressLine;
    private String ward;
    private String district;
    private String province;
    private String postalCode;
    private Boolean isDefault;

    public static UserAddressDTO fromEntity(UserAddress address) {
        return UserAddressDTO.builder()
                .id(address.getId())
                .addressType(address.getAddressType())
                .recipientName(address.getRecipientName())
                .recipientPhone(address.getRecipientPhone())
                .addressLine(address.getAddressLine())
                .ward(address.getWard())
                .district(address.getDistrict())
                .province(address.getProvince())
                .postalCode(address.getPostalCode())
                .isDefault(address.getIsDefault())
                .build();
    }

    public UserAddress toEntity() {
        return UserAddress.builder()
                .addressType(this.addressType)
                .recipientName(this.recipientName)
                .recipientPhone(this.recipientPhone)
                .addressLine(this.addressLine)
                .ward(this.ward)
                .district(this.district)
                .province(this.province)
                .postalCode(this.postalCode)
                .isDefault(this.isDefault != null ? this.isDefault : false)
                .build();
    }
}
