package com.example.bookstore.repository;

import com.example.bookstore.model.AssociationRule;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository collection association_rules (materialized view cua luat ket hop).
 *
 * <p>Thay cac JPQL cua ban cu; bo {@code deleteAllRules()} vi da co
 * {@code deleteAll()} cua MongoRepository.</p>
 */
@Repository
public interface AssociationRuleRepository extends MongoRepository<AssociationRule, Long> {

    /**
     * Goi y "mua kem" cho 1 sach: confidence >= nguong va lift > 1,
     * sap theo confidence/lift giam dan (dung index idx_rules_recommend).
     */
    @Query(value = "{ 'bookAId': ?0, 'confidence': { $gte: ?1 }, 'lift': { $gt: 1.0 } }",
            sort = "{ 'confidence': -1, 'lift': -1 }")
    List<AssociationRule> findBoughtTogetherByBookId(Long bookId, Double minConfidence);

    /** Tra cuu nguoc: sach nao thuong duoc mua kem sach nay. */
    @Query(value = "{ 'bookBId': ?0, 'confidence': { $gte: ?1 } }", sort = "{ 'lift': -1 }")
    List<AssociationRule> findRulesWhereBookIsTarget(Long bookId, Double minConfidence);

    long countByBookAIdAndBookBId(Long bookAId, Long bookBId);

    /** TUONG THICH: code cu goi {@code deleteAllRules()} truoc khi tinh lai. */
    default int deleteAllRules() {
        long total = count();
        deleteAll();
        return (int) total;
    }

    /** TUONG THICH: minConfidence kieu Double (ban cu dung BigDecimal). */
    default List<AssociationRule> findBoughtTogetherByBookId(Long bookId, java.math.BigDecimal minConfidence) {
        return findBoughtTogetherByBookId(bookId, minConfidence.doubleValue());
    }
}
