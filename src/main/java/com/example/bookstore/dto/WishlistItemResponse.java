package com.example.bookstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemResponse {
    private Long id;
    private String title;
    private String author;
    private Double price;
    private Integer stockQuantity;
    private String imageUrl;
    private String categoryName;
    private String shopName;
}
