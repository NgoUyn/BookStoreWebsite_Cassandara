package com.example.bookstore.model;

import com.example.bookstore.model.converter.LoyaltyMemberConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity lưu thông tin khách hàng phục vụ ML churn prediction (mô hình final-gauss-lightgbm).
 * Quan hệ 1-1 với User, tách biệt để không ảnh hưởng bảng users.
 * Input: 12 raw features (đồng bộ với features_config.json)
 * Output: predicted_label (0/1), churn_probability, risk_level (LOW/HIGH)
 */
@Entity
@Table(name = "customer_ml")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // ========================
    // ML Input Features (12 raw features — đồng bộ với features_config.json)
    // ========================

    @Column(nullable = false)
    private Double accountAgeMonths;

    @Column(nullable = false)
    private Double avgOrderValue;

    @Column(nullable = false)
    private Double totalOrders;

    @Column(nullable = false)
    private Double customerSupportTickets;

    @Convert(converter = LoyaltyMemberConverter.class)
    @Column(nullable = false, columnDefinition = "FLOAT")
    private Double loyaltyMember; // 0.0 = No, 1.0 = Yes (float theo features_config.json)

    @Column(nullable = false)
    private Double browsingFrequencyPerWeek;

    @Column(nullable = false)
    private Double cartAbandonmentRate;

    @Column(nullable = false)
    private Double productReviewScoreAvg;

    @Column(nullable = false)
    private Double satisfactionScore;

    @Column(nullable = false)
    private Double priceSensitivityIndex;

    @Column(nullable = false)
    private Double discountUsageRate; // Tỷ lệ sử dụng giảm giá 0.0-1.0

    @Column(nullable = false)
    private Double returnRate; // Tỷ lệ trả hàng 0.0-1.0

    // ========================
    // ML Output Results
    // ========================

    @Column
    private Integer predictedLabel; // 0 = Stay (Ở lại), 1 = Churn (Rời bỏ)

    @Column
    private Double churnProbability;

    @Column(length = 10)
    private String riskLevel; // "LOW" hoặc "HIGH"

    @Column
    private LocalDateTime lastAnalyzedAt;

    // ========================
    // Timestamps
    // ========================

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
