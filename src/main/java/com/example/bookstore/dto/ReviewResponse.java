package com.example.bookstore.dto;

import com.example.bookstore.model.BookReview;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for BookReview response
 * Used in API responses to include user information and book title
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private Long id;
    private Long bookId;
    private String bookTitle;
    private UserResponse user;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
    private boolean isHidden;

    public static ReviewResponse from(BookReview review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .bookId(review.getBook().getId())
                .bookTitle(review.getBook().getTitle())
                .user(UserResponse.builder()
                        .id(review.getUser().getId())
                        .username(review.getUser().getUsername())
                        .avatarUrl(review.getUser().getAvatarUrl())
                        .build())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .isHidden(review.isHidden())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserResponse {
        private Long id;
        private String username;
        private String avatarUrl;
    }
}
