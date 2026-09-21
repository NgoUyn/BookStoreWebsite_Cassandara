package com.example.bookstore.repository;

import com.example.bookstore.model.OrderReturn;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Repository collection order_returns. */
@Repository
public interface OrderReturnRepository extends MongoRepository<OrderReturn, Long> {

    List<OrderReturn> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<OrderReturn> findByOrderId(Long orderId);

    List<OrderReturn> findByStatus(String status);

    long countByUserId(Long userId);

    long countByStatus(String status);

    /** Tong so luong da tra cua user (thay SUM() cua SQL Server). */
    @Aggregation(pipeline = {
            "{ $match: { 'userId': ?0, 'status': { $in: ['APPROVED', 'REFUNDED'] } } }",
            "{ $group: { '_id': null, total: { $sum: '$quantityReturned' } } }"
    })
    Long sumReturnedQuantityByUserId(Long userId);

    // ======================= LOP TUONG THICH (adapter) ======================

    default long countByUser(com.example.bookstore.model.User user) {
        return countByUserId(user.getId());
    }

    default Long sumReturnedQuantityByUser(com.example.bookstore.model.User user) {
        Long total = sumReturnedQuantityByUserId(user.getId());
        return total == null ? 0L : total;
    }
}
