package com.example.bookstore.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Goi y "mua kem" - SUBSET PATTERN: chi lay top 10 luat manh nhat tu
 * collection {@code association_rules} va nhung vao {@code books.boughtTogether[]}
 * (materialized view) de doc cuc nhanh, khong phai join.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoughtTogether {

    private Long bookId;
    private Double confidence;
    private Double lift;
    private Double support;
}
