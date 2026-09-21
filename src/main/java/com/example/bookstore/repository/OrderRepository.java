package com.example.bookstore.repository;

import com.example.bookstore.model.Order;
import com.example.bookstore.model.enums.OrderStatus;
import com.example.bookstore.repository.aggregation.OrderSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository AGGREGATE orders.
 *
 * <p>Thay the 3 repository cua ban SQL ({@code OrderRepository} +
 * {@code SubOrderRepository} + {@code OrderItemRepository}) vi nay ca 3 bang
 * da gop vao 1 document - truy vấn "theo seller" dung {@code $elemMatch}
 * tren mang {@code subOrders} (multikey index).</p>
 */
@Repository
public interface OrderRepository extends MongoRepository<Order, Long>, OrderSearchRepository {

    List<Order> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    Page<Order> findByBuyerId(Long buyerId, Pageable pageable);

    @Query(value = "{ '_id': ?0, 'buyerId': ?1 }", exists = true)
    boolean existsByIdAndBuyerId(Long orderId, Long buyerId);

    List<Order> findByBuyerIdAndCreatedAtBetween(Long buyerId, LocalDateTime from, LocalDateTime to);

    @Query("{ 'buyerId': ?0, 'totalAmount': { $gte: ?1, $lte: ?2 } }")
    List<Order> findByBuyerIdAndTotalAmountBetween(Long buyerId, Double minAmount, Double maxAmount);

    Page<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    long countByBuyerId(Long buyerId);

    long countByStatus(OrderStatus status);

    /** Don hang co dung voucher cua buyer. */
    @Query(value = "{ 'buyerId': ?0, 'couponCode': { $ne: null } }", count = true)
    long countDiscountedOrdersByBuyerId(Long buyerId);

    /** Don hang moi nhat cua buyer (dung thay cho MAX(createdAt)). */
    java.util.Optional<Order> findFirstByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    // ======================================================================
    // AGGREGATION (thay SUM/COUNT cua SQL)
    // ======================================================================

    @Aggregation(pipeline = {
            "{ $match: { 'buyerId': ?0, 'isDeleted': { $ne: true } } }",
            "{ $group: { '_id': null, total: { $sum: '$totalAmount' } } }"
    })
    Double sumTotalAmountByBuyerId(Long buyerId);

    @Aggregation(pipeline = {
            "{ $match: { 'buyerId': ?0, 'isDeleted': { $ne: true } } }",
            "{ $group: { '_id': null, total: { $sum: '$discountAmount' } } }"
    })
    Double sumDiscountAmountByBuyerId(Long buyerId);

    // ======================================================================
    // TRUY VAN THEO SUB-ORDER (thay SubOrderRepository cua ban SQL)
    // ======================================================================

    @Query("{ 'subOrders': { $elemMatch: { 'sellerId': ?0 } } }")
    List<Order> findBySubOrdersSellerId(Long sellerId);

    @Query("{ 'subOrders': { $elemMatch: { 'sellerId': ?0, 'status': ?1 } } }")
    List<Order> findBySubOrdersSellerIdAndStatus(Long sellerId, OrderStatus status);

    @Query(value = "{ 'subOrders': { $elemMatch: { 'sellerId': ?0 } } }", count = true)
    long countBySubOrdersSellerId(Long sellerId);

    @Query(value = "{ 'subOrders': { $elemMatch: { 'sellerId': ?0, 'id': ?1 } } }", exists = true)
    boolean existsBySubOrdersSellerIdAndSubOrderId(Long sellerId, Long subOrderId);

    @Query(value = "{ 'subOrders': { $elemMatch: { 'id': ?0 } }, 'buyerId': ?1 }", exists = true)
    boolean existsBySubOrderIdAndBuyerId(Long subOrderId, Long buyerId);

    /** Danh sach buyer da tung mua hang cua seller (thay SELECT DISTINCT). */
    @Aggregation(pipeline = {
            "{ $match: { 'subOrders.sellerId': ?0 } }",
            "{ $unwind: '$subOrders' }",
            "{ $match: { 'subOrders.sellerId': ?0 } }",
            "{ $group: { '_id': '$buyerId' } }"
    })
    List<Long> findDistinctBuyerIdsBySellerId(Long sellerId);

    /** Tim order theo id cua subOrder (dung cho SubOrderRepository facade). */
    java.util.Optional<Order> findFirstBySubOrdersId(Long subOrderId);

    List<Order> findBySubOrdersId(Long subOrderId);

    /** Tim kiem don hang (admin) theo ma don hoac ten nguoi mua. */
    @Query("{ $or: [ { 'orderCode': { $regex: ?0, $options: 'i' } }, "
            + "{ 'buyer.username': { $regex: ?0, $options: 'i' } }, "
            + "{ 'buyer.fullName': { $regex: ?0, $options: 'i' } } ] }")
    Page<Order> searchOrders(String keyword, Pageable pageable);
}
