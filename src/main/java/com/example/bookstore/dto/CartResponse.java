package com.example.bookstore.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CartResponse {
    private Long cartId;
    private Long buyerId;
    private Integer totalItems;
    private Double totalAmount;
    private List<CartItemResponse> items;
}
