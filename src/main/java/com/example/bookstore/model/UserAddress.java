package com.example.bookstore.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Dia chi giao hang - KHONG con la bang rieng (SQL: {@code user_addresses}),
 * duoc EMBED trong {@code users.addresses[]} (bounded 1-5 dia chi/nguoi).
 *
 * <p>Doc/ghi bang toan tu mang tren document user: {@code $push} khi them,
 * {@code arrayFilters} khi sua, {@code $pull} khi xoa.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAddress {

    @org.springframework.data.mongodb.core.mapping.Field("addressId")
    private Long id;
    private String addressType;      // HOME, OFFICE, OTHER
    private String recipientName;
    private String recipientPhone;
    private String addressLine;
    private String ward;
    private String district;
    private String province;
    private String postalCode;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
