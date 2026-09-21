package com.example.bookstore.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Ho so ML cua khach hang - EMBED trong {@code users.ml}.
 *
 * <p>Chua 12 raw feature (dong bo features_config.json) + 3 ket qua du doan
 * (predictedLabel/churnProbability/riskLevel) + 3 chi so RFM duoc tinh bang
 * aggregation (xem db/mongo/05_queries_advanced.js - A8/A9).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlProfile {

    // ---------- 12 raw features ----------
    private Double accountAgeMonths;
    private Double avgOrderValue;
    private Double totalOrders;
    private Double customerSupportTickets;
    private Double loyaltyMember;
    private Double browsingFrequencyPerWeek;
    private Double cartAbandonmentRate;
    private Double productReviewScoreAvg;
    private Double satisfactionScore;
    private Double priceSensitivityIndex;
    private Double discountUsageRate;
    private Double returnRate;

    // ---------- Ket qua du doan ----------
    private Integer predictedLabel;
    private Double churnProbability;
    private String riskLevel;
    private LocalDateTime lastAnalyzedAt;

    // ---------- RFM (tinh bang $merge) ----------
    private Integer rfmFrequency;
    private Double rfmMonetary;
    private LocalDateTime rfmLastOrder;
    private String rfmSegment;
}
