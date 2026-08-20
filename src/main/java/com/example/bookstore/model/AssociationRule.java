package com.example.bookstore.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AssociationRule Entity
 * Represents mined association rules from customer purchase patterns.
 * Example: If customer buys bookA (book_id_a), 75% probability they also buy bookB (book_id_b)
 * with support=0.05 (5% of all transactions), lift=1.5 (1.5x more likely than random)
 */
@Entity
@Table(name = "association_rules")
@Data
@ToString(exclude = {"bookA", "bookB"})
@EqualsAndHashCode(of = "ruleId")
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
public class AssociationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long ruleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id_a", nullable = false)
    private Book bookA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id_b", nullable = false)
    private Book bookB;

    /**
     * Support: Percentage of all transactions containing both bookA and bookB
     * Range: 0.0000 to 1.0000 (0% to 100%)
     * Example: 0.05 = 5% of all orders contain both books
     */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal support;

    /**
     * Confidence: P(bookB | bookA) = % of transactions with bookA that also have bookB
     * Range: 0.0000 to 1.0000 (0% to 100%)
     * Example: 0.75 = 75% of customers who buy bookA also buy bookB
     * Use this for filtering: Only recommend if confidence >= 0.3 (30%)
     */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    /**
     * Lift: Ratio of observed frequency to expected frequency
     * Calculated as: confidence / P(bookB) = support / (P(bookA) * P(bookB))
     * Range: > 0.0
     * Interpretation:
     *   - lift = 1.0 → no correlation (events independent)
     *   - lift > 1.0 → positive correlation (strong rule)
     *   - lift < 1.0 → negative correlation (weak/inverse rule)
     * Use this for ranking: Sort by lift DESC to get strongest rules first
     */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal lift;

    /**
     * When this rule was last computed/updated
     * Used for tracking staleness and cleanup
     */
    @Column(nullable = false, columnDefinition = "DATETIME2")
    private LocalDateTime updatedAt;

}
