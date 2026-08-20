package com.example.bookstore.repository;

import com.example.bookstore.model.Book;
import com.example.bookstore.model.BookReview;
import com.example.bookstore.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.List;

@Repository
public interface BookReviewRepository extends JpaRepository<BookReview, Long> {

    /**
     * Lấy danh sách đánh giá công khai của một cuốn sách (có phân trang)
     */
    Page<BookReview> findByBookAndIsHiddenFalse(Book book, Pageable pageable);

    /**
     * Lấy tất cả đánh giá của một cuốn sách (cả ẩn và hiện)
     */
    Page<BookReview> findAllByBook(Book book, Pageable pageable);

    /**
     * Lọc đánh giá theo số sao và sách (có phân trang)
     */
    Page<BookReview> findByBookAndRatingAndIsHiddenFalse(Book book, Integer rating, Pageable pageable);

    /**
     * Đếm tổng số đánh giá của một cuốn sách
     */
    long countByBookAndIsHiddenFalse(Book book);

    /**
     * Kiểm tra xem người dùng đã đánh giá cuốn sách này chưa
     */
    boolean existsByBookAndUser(Book book, User user);

    /**
     * Tính điểm đánh giá trung bình của một cuốn sách
     */
    @Query("SELECT AVG(r.rating) FROM BookReview r WHERE r.book = :book AND r.isHidden = false")
    Double findAverageRatingByBook(@Param("book") Book book);

    /**
     * Thống kê số lượng đánh giá theo từng mức sao (1-5)
     */
    @Query("SELECT r.rating as rating, COUNT(r) as count FROM BookReview r " +
           "WHERE r.book = :book AND r.isHidden = false " +
           "GROUP BY r.rating")
    List<Map<String, Object>> countRatingDistributionByBook(@Param("book") Book book);

    // ========================
    // ML Feature Computation Queries
    // ========================

    /**
     * Tính điểm đánh giá trung bình của một user (cho tất cả review họ đã viết).
     */
    @Query("SELECT AVG(r.rating) FROM BookReview r WHERE r.user = :user AND r.isHidden = false")
    Double findAverageRatingByUser(@Param("user") User user);

    /**
     * Đếm tổng số review của một user.
     */
    long countByUser(User user);

    /**
     * Tìm đánh giá của người dùng cho một cuốn sách cụ thể
     */
    BookReview findByBookAndUser(Book book, User user);

    /**
     * Lấy tất cả đánh giá của một người dùng (có phân trang)
     */
    Page<BookReview> findByUserAndIsHiddenFalse(User user, Pageable pageable);

    /**
     * Đếm số lượt đánh giá của một người dùng
     */
    long countByUserAndIsHiddenFalse(User user);
}
