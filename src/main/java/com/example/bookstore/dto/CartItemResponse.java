package com.example.bookstore.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CartItemResponse {
    private Long itemId;
    private Long bookId;
    private String title;
    private String author;
    private Double unitPrice;
    private Integer quantity;
    private Double lineTotal;
    private Long sellerId;
    private String sellerName;
    private String imageUrl;
}
