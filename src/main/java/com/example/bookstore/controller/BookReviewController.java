package com.example.bookstore.controller;

import com.example.bookstore.dto.ReviewRequest;
import com.example.bookstore.model.BookReview;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.service.BookReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/reviews")
@CrossOrigin("*")
public class BookReviewController {

    @Autowired
    private BookReviewService reviewService;

    /**
     * Lấy danh sách đánh giá của một cuốn sách (Public)
     */
    @GetMapping("/book/{bookId}")
    public ResponseEntity<?> getBookReviews(
            @PathVariable Long bookId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<BookReview> reviews = reviewService.getBookReviews(bookId, pageable);
        return ResponseEntity.ok(reviews);
    }

    /**
     * Lọc đánh giá theo số sao (Public)
     */
    @GetMapping("/book/{bookId}/by-rating/{rating}")
    public ResponseEntity<?> getBookReviewsByRating(
            @PathVariable Long bookId,
            @PathVariable Integer rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<BookReview> reviews = reviewService.getBookReviewsByRating(bookId, rating, pageable);
            return ResponseEntity.ok(reviews);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Lấy thống kê đánh giá của một cuốn sách (Public)
     */
    @GetMapping("/book/{bookId}/stats")
    public ResponseEntity<?> getBookReviewStats(@PathVariable Long bookId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("averageRating", reviewService.getAverageRating(bookId));
        stats.put("totalReviews", reviewService.getTotalReviews(bookId));
        return ResponseEntity.ok(stats);
    }

    /**
     * Lấy phân bố đánh giá theo từng mức sao (Public)
     */
    @GetMapping("/book/{bookId}/distribution")
    public ResponseEntity<?> getRatingDistribution(@PathVariable Long bookId) {
        try {
            Map<Integer, Long> distribution = reviewService.getRatingDistribution(bookId);
            return ResponseEntity.ok(distribution);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Kiểm tra xem người dùng đã đánh giá sách hay chưa (Yêu cầu đăng nhập)
     */
    @GetMapping("/book/{bookId}/user-review")
    @PreAuthorize("hasAuthority('BUYER')")
    public ResponseEntity<?> getUserReviewStatus(
            @PathVariable Long bookId,
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Vui lòng đăng nhập");
        }

        try {
            boolean hasReviewed = reviewService.hasUserReviewedBook(principal.userId(), bookId);
            Map<String, Object> response = new HashMap<>();
            response.put("hasReviewed", hasReviewed);

            if (hasReviewed) {
                BookReview review = reviewService.getUserReviewForBook(principal.userId(), bookId);
                response.put("review", review);
            }

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Lấy tất cả đánh giá của người dùng hiện tại (Yêu cầu đăng nhập)
     */
    @GetMapping("/my-reviews")
    @PreAuthorize("hasAuthority('BUYER')")
    public ResponseEntity<?> getUserReviews(
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Vui lòng đăng nhập");
        }

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<BookReview> reviews = reviewService.getUserReviews(principal.userId(), pageable);
            return ResponseEntity.ok(reviews);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Thêm đánh giá mới (Yêu cầu đăng nhập và đã mua hàng)
     */
    @PostMapping
    @PreAuthorize("hasAuthority('BUYER')")
    public ResponseEntity<?> addReview(
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal,
            @Valid @RequestBody ReviewRequest request
    ) {
        try {
            if (principal == null) {
                return ResponseEntity.status(401).body("Vui lòng đăng nhập để đánh giá");
            }

            // Validation
            if (request.getRating() == null || request.getBookId() == null) {
                return ResponseEntity.badRequest().body("Sách và đánh giá là bắt buộc");
            }
            
            BookReview review = reviewService.addReview(
                    principal.userId(),
                    request.getBookId(),
                    request.getRating(),
                    request.getComment()
            );
            return ResponseEntity.ok(review);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Cập nhật đánh giá (Yêu cầu quyền sở hữu)
     */
    @PutMapping("/{reviewId}")
    @PreAuthorize("hasAuthority('BUYER')")
    public ResponseEntity<?> updateReview(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal,
            @Valid @RequestBody ReviewRequest request
    ) {
        try {
            if (principal == null) {
                return ResponseEntity.status(401).body("Vui lòng đăng nhập");
            }

            // Validation
            if (request.getRating() == null) {
                return ResponseEntity.badRequest().body("Đánh giá là bắt buộc");
            }

            BookReview review = reviewService.updateReview(
                    reviewId,
                    principal.userId(),
                    request.getRating(),
                    request.getComment()
            );
            return ResponseEntity.ok(review);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Xóa đánh giá (Yêu cầu quyền sở hữu)
     */
    @DeleteMapping("/{reviewId}")
    @PreAuthorize("hasAuthority('BUYER')")
    public ResponseEntity<?> deleteReview(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal
    ) {
        try {
            if (principal == null) {
                return ResponseEntity.status(401).body("Vui lòng đăng nhập");
            }

            reviewService.deleteReview(reviewId, principal.userId());
            return ResponseEntity.ok("Xóa đánh giá thành công");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
