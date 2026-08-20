package com.example.bookstore.repository;

import com.example.bookstore.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Repository cho bảng support_tickets (V24 migration).
 * Dùng để tính ML feature: customer_support_tickets
 */
@Repository
public interface SupportTicketRepository extends JpaRepository<com.example.bookstore.model.SupportTicket, Long> {

    /**
     * Đếm số lượng support tickets của một user trong khoảng thời gian.
     */
    @Query("SELECT COUNT(t) FROM SupportTicket t WHERE t.user = :user AND t.createdAt >= :since")
    Long countTicketsSince(@Param("user") User user, @Param("since") LocalDateTime since);

    /**
     * Đếm tổng số support tickets của một user.
     */
    long countByUser(User user);
}
