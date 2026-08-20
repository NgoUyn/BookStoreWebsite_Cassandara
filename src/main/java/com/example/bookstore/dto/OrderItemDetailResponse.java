package com.example.bookstore.dto;

import com.example.bookstore.model.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderItemDetailResponse {
    private Long subOrderId;
    private OrderStatus subOrderStatus;
    private Long sellerId;
    private String sellerName;

    private Long bookId;
    private String title;
    private String author;

    private Double unitPrice;
    private Integer quantity;
    private Double lineTotal;
}
