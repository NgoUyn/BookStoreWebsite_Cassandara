package com.example.bookstore.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * Collection {@code daily_stats} - MATERIALIZED VIEW doanh thu theo ngay/seller,
 * sinh bang aggregation + {@code $merge} (xem db/mongo/03_seed_reference.js).
 * Dashboard doc collection nay thay vi $unwind toan bo orders moi lan tai trang.
 */
@Document(collection = "daily_stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyStat {

    @Id
    private String id;              // "<dateKey>:<sellerId>"

    private String dateKey;         // yyyy-MM-dd
    private Long sellerId;
    private Double revenue;
    private Long orderCount;
    private Long unitsSold;

    @Field("updatedAt")
    private LocalDateTime updatedAt;
}
