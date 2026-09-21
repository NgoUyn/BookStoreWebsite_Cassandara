package com.example.bookstore.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thong tin nha ban (@code users.seller) - EMBED trong users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerProfile {

    private String shopName;
    private Boolean approved;
    private java.time.LocalDateTime approvedAt;
}
