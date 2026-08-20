package com.example.bookstore.service;

import com.example.bookstore.model.Book;
import com.example.bookstore.model.BookReview;
import com.example.bookstore.model.User;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.BookReviewRepository;
import com.example.bookstore.repository.OrderItemRepository;
import com.example.bookstore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BookReviewService {

    @Autowired
    private BookReviewRepository reviewRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Thêm đánh giá mới với kiểm tra điều kiện khắt khe (Dùng userId)
     */
    @Transactional
    public BookReview addReview(Long userId, Long bookId, Integer rating, String comment) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        return addReview(user, bookId, rating, comment);
    }

    /**
     * Thêm đánh giá mới với kiểm tra điều kiện khắt khe
     */
    @Transactional
    public BookReview addReview(User user, Long bookId, Integer rating, String comment) {
        // Validation: Rating phải từ 1-5
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Đánh giá phải từ 1 đến 5 sao");
        }

        // Validation: Comment tối đa 2000 ký tự
        if (comment != null && comment.length() > 2000) {
            throw new IllegalArgumentException("Bình luận tối đa 2000 ký tự");
        }

        // 1. Kiểm tra sách có tồn tại không
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sách với ID: " + bookId));

        // 2. Kiểm tra điều kiện: Đã mua và đơn hàng COMPLETED
        boolean hasPurchased = orderItemRepository.hasUserPurchasedBook(user, bookId);
        if (!hasPurchased) {
            throw new IllegalStateException("Bạn chỉ có thể đánh giá những cuốn sách đã mua và giao hàng thành công.");
        }

        // 3. Kiểm tra xem đã đánh giá cuốn này chưa (tránh spam)
        if (reviewRepository.existsByBookAndUser(book, user)) {
            throw new IllegalStateException("Bạn đã đánh giá cuốn sách này rồi.");
        }

        // 4. Tạo và lưu đánh giá
        BookReview review = BookReview.builder()
                .book(book)
                .user(user)
                .rating(rating)
                .comment(comment != null ? comment.trim() : "")
                .createdAt(LocalDateTime.now())
                .isHidden(false)
                .build();

        return reviewRepository.save(review);
    }

    /**
     * Cập nhật đánh giá của người dùng
     */
    @Transactional
    public BookReview updateReview(Long reviewId, Long userId, Integer rating, String comment) {
        // Validation: Rating phải từ 1-5
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Đánh giá phải từ 1 đến 5 sao");
        }

        // Validation: Comment tối đa 2000 ký tự
        if (comment != null && comment.length() > 2000) {
            throw new IllegalArgumentException("Bình luận tối đa 2000 ký tự");
        }

        BookReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đánh giá"));

        // Kiểm tra quyền sở hữu
        if (!review.getUser().getId().equals(userId)) {
            throw new IllegalStateException("Bạn không có quyền chỉnh sửa đánh giá này");
        }

        // Cập nhật
        review.setRating(rating);
        review.setComment(comment != null ? comment.trim() : "");

        return reviewRepository.save(review);
    }

    /**
     * Xóa đánh giá của người dùng
     */
    @Transactional
    public void deleteReview(Long reviewId, Long userId) {
        BookReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đánh giá"));

        // Kiểm tra quyền sở hữu
        if (!review.getUser().getId().equals(userId)) {
            throw new IllegalStateException("Bạn không có quyền xóa đánh giá này");
        }

        reviewRepository.deleteById(reviewId);
    }

    /**
     * Lấy danh sách đánh giá của một cuốn sách (có phân trang)
     */
    public Page<BookReview> getBookReviews(Long bookId, Pageable pageable) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sách"));
        return reviewRepository.findByBookAndIsHiddenFalse(book, pageable);
    }

    /**
     * Lọc đánh giá theo số sao
     */
    public Page<BookReview> getBookReviewsByRating(Long bookId, Integer rating, Pageable pageable) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Số sao phải từ 1 đến 5");
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sách"));
        return reviewRepository.findByBookAndRatingAndIsHiddenFalse(book, rating, pageable);
    }

    /**
     * Tính điểm trung bình sao cho sách
     * Nếu không có đánh giá nào, trả về 0.0
     */
    public Double getAverageRating(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sách"));

        Double avg = reviewRepository.findAverageRatingByBook(book);
        return (avg != null) ? Math.round(avg * 10.0) / 10.0 : 0.0; // Làm tròn 1 chữ số thập phân
    }

    /**
     * Lấy tổng số lượt đánh giá
     */
    public long getTotalReviews(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sách"));
        return reviewRepository.countByBookAndIsHiddenFalse(book);
    }

    /**
     * Lấy thống kê phân bố đánh giá theo từng mức sao (1-5 sao)
     */
    public Map<Integer, Long> getRatingDistribution(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sách"));

        List<Map<String, Object>> results = reviewRepository.countRatingDistributionByBook(book);
        
        // Khởi tạo map với tất cả rating từ 1-5 có giá trị mặc định là 0
        Map<Integer, Long> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }

        // Điền dữ liệu từ database
        for (Map<String, Object> result : results) {
            Integer rating = (Integer) result.get("rating");
            Long count = ((Number) result.get("count")).longValue();
            distribution.put(rating, count);
        }

        return distribution;
    }

    /**
     * Lấy tất cả đánh giá của một người dùng (có phân trang)
     */
    public Page<BookReview> getUserReviews(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        return reviewRepository.findByUserAndIsHiddenFalse(user, pageable);
    }

    /**
     * Kiểm tra xem người dùng đã đánh giá sách hay chưa
     */
    public boolean hasUserReviewedBook(Long userId, Long bookId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sách"));
        
        return reviewRepository.existsByBookAndUser(book, user);
    }

    /**
     * Lấy đánh giá của người dùng cho sách cụ thể
     */
    public BookReview getUserReviewForBook(Long userId, Long bookId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sách"));
        
        return reviewRepository.findByBookAndUser(book, user);
    }

    /**
     * Ẩn hoặc hiện một đánh giá (Dành cho Admin kiểm duyệt)
     */
    @Transactional
    public BookReview toggleReviewVisibility(Long reviewId) {
        BookReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đánh giá với ID: " + reviewId));
        
        review.setHidden(!review.isHidden());
        return reviewRepository.save(review);
    }

    /**
     * Lấy tất cả đánh giá của một cuốn sách (bao gồm cả bị ẩn - dành cho Admin)
     */
    public Page<BookReview> getAllBookReviewsForAdmin(Long bookId, Pageable pageable) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sách"));
        return reviewRepository.findAllByBook(book, pageable);
    }
}
