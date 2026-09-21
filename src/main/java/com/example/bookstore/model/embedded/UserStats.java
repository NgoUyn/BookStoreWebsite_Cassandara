package com.example.bookstore.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Thong ke tich luy cua nguoi dung - EMBED trong {@code users.stats}.
 *
 * <p>Duoc tinh san (materialized) bang aggregation + {@code $merge} thay vi
 * dem/SUM moi lan hien thi (xem db/mongo/03_seed_reference.js).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStats {

    private Integer orderCount;
    private Double totalSpent;
    private Double avgOrderValue;
    private Integer reviewCount;
    private Integer wishlistCount;
    private LocalDateTime lastOrderAt;
}
