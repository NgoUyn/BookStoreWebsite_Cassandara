package com.example.bookstore.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for AssociationRule entity
 * Used in API responses to avoid lazy-loading issues and provide controlled view
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssociationRuleDTO {

    private Long ruleId;
    private Long bookIdA;
    private Long bookIdB;
    private BigDecimal support;
    private BigDecimal confidence;
    private BigDecimal lift;
    private LocalDateTime updatedAt;

    /**
     * Interpret confidence as percentage (0-100)
     */
    public Double getConfidencePercentage() {
        return confidence.multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    /**
     * Human-readable summary: "When customer buys bookA, 75% also buy bookB (lift: 1.5x)"
     */
    public String getSummary() {
        return String.format(
            "When customer buys book %d, %.1f%% also buy book %d (lift: %.2fx)",
            bookIdA,
            getConfidencePercentage(),
            bookIdB,
            lift
        );
    }

}
