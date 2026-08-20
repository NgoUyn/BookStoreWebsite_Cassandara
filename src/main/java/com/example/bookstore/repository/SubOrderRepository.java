package com.example.bookstore.repository;

import com.example.bookstore.model.SubOrder;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SubOrderRepository extends JpaRepository<SubOrder, Long> {

    /**
     * Find sub-orders by seller with JOIN FETCH to avoid LazyInitializationException.
     * Fetches: seller (User), parentOrder (Order), parentOrder.buyer (User),
     * items (List<OrderItem>), and items.book (Book) in a single query.
     */
    @Query("SELECT DISTINCT so FROM SubOrder so " +
           "JOIN FETCH so.seller " +
           "JOIN FETCH so.parentOrder o " +
           "JOIN FETCH o.buyer " +
           "LEFT JOIN FETCH so.items " +
           "WHERE so.seller = :seller " +
           "ORDER BY so.id DESC")
    List<SubOrder> findBySellerOrderByIdDesc(@Param("seller") User seller);

    /**
     * Find sub-orders by seller with JOIN FETCH to avoid LazyInitializationException.
     */
    @Query("SELECT DISTINCT so FROM SubOrder so " +
           "JOIN FETCH so.seller " +
           "JOIN FETCH so.parentOrder o " +
           "JOIN FETCH o.buyer " +
           "LEFT JOIN FETCH so.items " +
           "WHERE so.seller = :seller")
    List<SubOrder> findBySeller(@Param("seller") User seller);

    /**
     * Find sub-orders by seller and status with JOIN FETCH.
     */
    @Query("SELECT DISTINCT so FROM SubOrder so " +
           "JOIN FETCH so.seller " +
           "JOIN FETCH so.parentOrder o " +
           "JOIN FETCH o.buyer " +
           "LEFT JOIN FETCH so.items " +
           "WHERE so.seller = :seller AND so.status = :status")
    List<SubOrder> findBySellerAndStatus(@Param("seller") User seller,
                                         @Param("status") OrderStatus status);

              @Query("""
                                          SELECT CASE WHEN COUNT(so) > 0 THEN TRUE ELSE FALSE END
                                          FROM SubOrder so
                                          WHERE so.id = :subOrderId
                                                 AND so.seller.id = :sellerId
                                          """)
              boolean existsByIdAndSellerId(@Param("subOrderId") Long subOrderId, @Param("sellerId") Long sellerId);

              @Query("""
                                          SELECT CASE WHEN COUNT(so) > 0 THEN TRUE ELSE FALSE END
                                          FROM SubOrder so
                                          WHERE so.id = :subOrderId
                                                 AND so.parentOrder.buyer.id = :buyerId
                                          """)
              boolean existsByIdAndBuyerId(@Param("subOrderId") Long subOrderId, @Param("buyerId") Long buyerId);

    /**
     * Find sub-orders by seller and status (ordered) with JOIN FETCH.
     */
    @Query("SELECT DISTINCT so FROM SubOrder so " +
           "JOIN FETCH so.seller " +
           "JOIN FETCH so.parentOrder o " +
           "JOIN FETCH o.buyer " +
           "LEFT JOIN FETCH so.items " +
           "WHERE so.seller = :seller AND so.status = :status " +
           "ORDER BY so.id DESC")
    List<SubOrder> findBySellerAndStatusOrdered(@Param("seller") User seller,
                                                @Param("status") OrderStatus status);

    /**
     * Find sub-orders by seller with date range filter with JOIN FETCH.
     */
    @Query("SELECT DISTINCT so FROM SubOrder so " +
           "JOIN FETCH so.seller " +
           "JOIN FETCH so.parentOrder o " +
           "JOIN FETCH o.buyer " +
           "LEFT JOIN FETCH so.items " +
           "WHERE so.seller = :seller " +
           "AND o.createdAt >= :createdFrom AND o.createdAt <= :createdTo " +
           "ORDER BY o.createdAt DESC")
    List<SubOrder> findBySellerAndDateRange(@Param("seller") User seller,
                                            @Param("createdFrom") LocalDateTime createdFrom,
                                            @Param("createdTo") LocalDateTime createdTo);

    /**
     * Find sub-orders by seller with price range filter with JOIN FETCH.
     */
    @Query("SELECT DISTINCT so FROM SubOrder so " +
           "JOIN FETCH so.seller " +
           "JOIN FETCH so.parentOrder o " +
           "JOIN FETCH o.buyer " +
           "LEFT JOIN FETCH so.items " +
           "WHERE so.seller = :seller " +
           "AND so.subTotal >= :minPrice AND so.subTotal <= :maxPrice " +
           "ORDER BY so.id DESC")
    List<SubOrder> findBySellerAndPriceRange(@Param("seller") User seller,
                                             @Param("minPrice") Double minPrice,
                                             @Param("maxPrice") Double maxPrice);

    /**
     * Find sub-orders with combined filters for seller with JOIN FETCH.
     * Note: For paginated queries with JOIN FETCH on collections, we use DISTINCT
     * and count query separately to avoid issues with pagination.
     */
    @Query(value = "SELECT DISTINCT so FROM SubOrder so " +
           "JOIN FETCH so.seller " +
           "JOIN FETCH so.parentOrder o " +
           "JOIN FETCH o.buyer " +
           "LEFT JOIN FETCH so.items " +
           "WHERE so.seller = :seller " +
           "AND (:status IS NULL OR so.status = :status) " +
           "AND (:createdFrom IS NULL OR o.createdAt >= :createdFrom) " +
           "AND (:createdTo IS NULL OR o.createdAt <= :createdTo) " +
           "AND (:minPrice IS NULL OR so.subTotal >= :minPrice) " +
           "AND (:maxPrice IS NULL OR so.subTotal <= :maxPrice)",
           countQuery = "SELECT COUNT(DISTINCT so) FROM SubOrder so " +
                        "WHERE so.seller = :seller " +
                        "AND (:status IS NULL OR so.status = :status) " +
                        "AND (:createdFrom IS NULL OR so.parentOrder.createdAt >= :createdFrom) " +
                        "AND (:createdTo IS NULL OR so.parentOrder.createdAt <= :createdTo) " +
                        "AND (:minPrice IS NULL OR so.subTotal >= :minPrice) " +
                        "AND (:maxPrice IS NULL OR so.subTotal <= :maxPrice)")
    Page<SubOrder> findBySellerWithFilters(@Param("seller") User seller,
                                           @Param("status") OrderStatus status,
                                           @Param("createdFrom") LocalDateTime createdFrom,
                                           @Param("createdTo") LocalDateTime createdTo,
                                           @Param("minPrice") Double minPrice,
                                           @Param("maxPrice") Double maxPrice,
                                           Pageable pageable);

    /**
     * Find sub-orders by seller and buyer name pattern with JOIN FETCH.
     */
    @Query("SELECT DISTINCT so FROM SubOrder so " +
           "JOIN FETCH so.seller " +
           "JOIN FETCH so.parentOrder o " +
           "JOIN FETCH o.buyer b " +
           "LEFT JOIN FETCH so.items " +
           "WHERE so.seller = :seller " +
           "AND LOWER(b.username) LIKE LOWER(CONCAT('%', :buyerName, '%')) " +
           "ORDER BY so.id DESC")
    List<SubOrder> findBySellerAndBuyerNameContaining(@Param("seller") User seller,
                                                      @Param("buyerName") String buyerName);

    // ========================
    // ML Feature Computation Queries
    // ========================

    /**
     * Đếm số sub_orders của buyer có trạng thái CANCELLED (dùng cho return rate).
     */
    @Query("SELECT COUNT(so) FROM SubOrder so " +
           "WHERE so.parentOrder.buyer = :buyer AND so.status = 'CANCELLED'")
    long countCancelledByBuyer(@Param("buyer") User buyer);

    /**
     * Đếm tổng số sub_orders của buyer.
     */
    @Query("SELECT COUNT(so) FROM SubOrder so WHERE so.parentOrder.buyer = :buyer")
    long countByBuyer(@Param("buyer") User buyer);

    /**
     * Lấy danh sách buyer IDs đã từng mua hàng từ seller (distinct).
     * Không JOIN FETCH items để tránh Cartesian product gây lỗi 500.
     */
    @Query("SELECT DISTINCT so.parentOrder.buyer.id FROM SubOrder so WHERE so.seller = :seller")
    List<Long> findDistinctBuyerIdsBySeller(@Param("seller") User seller);
}
