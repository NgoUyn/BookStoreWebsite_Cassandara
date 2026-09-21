package com.example.bookstore.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tong hop danh gia cua sach - READ MODEL duoc tinh san bang {@code $merge}
 * tu collection {@code reviews} (xem db/mongo/05_queries_advanced.js).
 *
 * <p>Nho vay trang chi tiet sach chi doc 1 field thay vi
 * {@code AVG()/GROUP BY} tren hang nghin review nhu ban SQL.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingSummary {

    private Double avg;
    private Integer count;

    /** So luong theo tung muc sao: {"1":0,"2":1,"3":2,"4":5,"5":9}. */
    @Builder.Default
    private Map<String, Integer> distribution = new LinkedHashMap<>();

    private LocalDateTime lastReviewAt;
}
