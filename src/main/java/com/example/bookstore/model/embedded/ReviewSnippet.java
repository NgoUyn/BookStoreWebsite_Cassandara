package com.example.bookstore.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Trich doan danh gia moi nhat - SUBSET PATTERN trong {@code books.topReviews[]}
 * (chi 3 phan tu). Danh sach day du nam o collection {@code reviews}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSnippet {

    private Long reviewId;
    private Long userId;
    private String userName;
    private Integer rating;
    private String comment;
    private Integer helpfulCount;
    private LocalDateTime createdAt;
}
