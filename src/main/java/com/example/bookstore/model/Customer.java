package com.example.bookstore.model;

import com.example.bookstore.model.document.AuditableDocument;
import com.example.bookstore.model.document.SequencedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * Collection {@code customer_ml} - ho so ML day du (input 12 feature + output).
 *
 * <p>Quan he 1-1 voi {@link User} qua {@code userId} (unique). Giu la collection
 * RIENG (thay vi embed hoan toan vao users) vi day la du lieu do pipeline ML
 * ghi theo lo va duoc admin/seller doc doc lap - tranh ghi de len document
 * nguoi dung dang hoat dong. {@code users.ml} van giu ban tom tat (RFM) cho
 * truy van nhanh.</p>
 */
@Document(collection = "customer_ml")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer implements SequencedDocument, AuditableDocument {

    @Id
    private Long id;

    @Indexed(unique = true)
    private Long userId;

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

    @Field("createdAt")
    private LocalDateTime createdAt;

    @Field("updatedAt")
    private LocalDateTime updatedAt;

    private Integer schemaVersion;
}
