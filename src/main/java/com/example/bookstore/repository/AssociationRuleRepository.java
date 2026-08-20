package com.example.bookstore.repository;

import com.example.bookstore.model.AssociationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface AssociationRuleRepository extends JpaRepository<AssociationRule, Long> {

    /**
     * Find top N "bought together" recommendations for a given book.
     * Returns rules where bookA = given bookId, sorted by confidence DESC then lift DESC.
     * Filters out low-confidence rules (< 0.3 or 30%) and rules with lift <= 1.0
     */
    @Query(value = """
            SELECT ar FROM AssociationRule ar
            WHERE ar.bookA.id = :bookId
            AND ar.confidence >= :minConfidence
            AND ar.lift > 1.0
            ORDER BY ar.confidence DESC, ar.lift DESC
            """)
    List<AssociationRule> findBoughtTogetherByBookId(
            @Param("bookId") Long bookId,
            @Param("minConfidence") BigDecimal minConfidence
    );

    /**
     * Reverse lookup: Find all rules where given book is the target (bookB).
     * Useful for analytics or alternative recommendation strategies.
     */
    @Query(value = """
            SELECT ar FROM AssociationRule ar
            WHERE ar.bookB.id = :bookId
            AND ar.confidence >= :minConfidence
            ORDER BY ar.lift DESC
            """)
    List<AssociationRule> findRulesWhereBookIsTarget(
            @Param("bookId") Long bookId,
            @Param("minConfidence") BigDecimal minConfidence
    );

    /**
     * Count total rules in database for monitoring/analytics
     */
    @Query("SELECT COUNT(ar) FROM AssociationRule ar")
    long countTotalRules();

    /**
     * Check if rule already exists (used to avoid duplicates during batch insert)
     */
    @Query(value = """
            SELECT COUNT(ar) FROM AssociationRule ar
            WHERE ar.bookA.id = :bookIdA
            AND ar.bookB.id = :bookIdB
            """)
    long countByBookPair(@Param("bookIdA") Long bookIdA, @Param("bookIdB") Long bookIdB);

    /**
     * Delete all association rules (used before full refresh)
     * Returns number of rows deleted
     */
    @Modifying
    @Query("DELETE FROM AssociationRule")
    int deleteAllRules();

}
