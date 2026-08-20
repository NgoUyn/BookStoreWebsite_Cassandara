package com.example.bookstore.repository;

import com.example.bookstore.model.OrderItem;
import com.example.bookstore.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import com.example.bookstore.model.enums.OrderStatus;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Lấy danh sách các cặp (OrderId, BookId) để xây dựng Baskets cho thuật toán pair-mining (frequent pairs).
     * Mỗi Order có thể có nhiều SubOrder, mỗi SubOrder có nhiều OrderItem (cuốn sách).
     * Bằng cách join này, ta gộp tất cả sách mua trong cùng 1 lần thanh toán thành 1 giỏ hàng (basket).
     */
    @Query("SELECT o.subOrder.parentOrder.id, o.book.id FROM OrderItem o WHERE o.subOrder.parentOrder IS NOT NULL")
    List<Object[]> findAllOrderBookPairs();

    /**
     * Only include order items from sub-orders with provided statuses (e.g., COMPLETED)
     */
    @Query("SELECT o.subOrder.parentOrder.id, o.book.id FROM OrderItem o WHERE o.subOrder.parentOrder IS NOT NULL AND o.subOrder.status IN :statuses")
    List<Object[]> findAllOrderBookPairsByStatuses(@Param("statuses") List<OrderStatus> statuses);

    /**
     * SLIDING WINDOW: Fetch order book pairs created within the last N days
     * with specific statuses. This prevents loading all-time data and causing OOM.
     * 
     * @param statuses Order statuses to include (e.g., PROCESSING, COMPLETED)
     * @param fromDate Lower bound of date range (typically: now - 30 days)
     * @return List of (OrderId, BookId) pairs for pair mining
     */
    @Query("""
            SELECT o.subOrder.parentOrder.id, o.book.id 
            FROM OrderItem o 
            WHERE o.subOrder.parentOrder IS NOT NULL 
            AND o.subOrder.status IN :statuses 
            AND o.subOrder.parentOrder.createdAt >= :fromDate
            """)
    List<Object[]> findOrderBookPairsByStatusesAndDateRange(
            @Param("statuses") List<OrderStatus> statuses,
            @Param("fromDate") LocalDateTime fromDate
    );

    /**
     * Check if a user has purchased a book and the order is COMPLETED.
     */
    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi " +
           "WHERE oi.subOrder.parentOrder.buyer = :user " +
           "AND oi.book.id = :bookId " +
           "AND oi.subOrder.status = com.example.bookstore.model.enums.OrderStatus.COMPLETED")
    boolean hasUserPurchasedBook(@Param("user") User user, @Param("bookId") Long bookId);

}
