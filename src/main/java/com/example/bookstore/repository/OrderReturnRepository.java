package com.example.bookstore.repository;

import com.example.bookstore.model.OrderReturn;
import com.example.bookstore.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderReturnRepository extends JpaRepository<OrderReturn, Long> {

    /**
     * Đếm tổng số lượng sản phẩm đã trả của một user (chỉ tính các return đã được APPROVED hoặc REFUNDED).
     */
    @Query("SELECT COALESCE(SUM(r.quantityReturned), 0) FROM OrderReturn r " +
           "WHERE r.user = :user AND r.status IN ('APPROVED', 'REFUNDED')")
    Long sumReturnedQuantityByUser(@Param("user") User user);

    /**
     * Đếm số lượng yêu cầu trả hàng của một user.
     */
    long countByUser(User user);
}
