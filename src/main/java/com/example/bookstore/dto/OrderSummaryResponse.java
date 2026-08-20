package com.example.bookstore.dto;

import com.example.bookstore.model.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Summary of an order for listing/filtering
 */
@Data
@Builder
public class OrderSummaryResponse {
    private Long orderId;
    private Long buyerId;
    private String buyerUsername;
    private Double totalAmount;
    private LocalDateTime createdAt;
    private Integer subOrderCount;
    private OrderStatus overallStatus; // Determined by sub-orders
    private String shippingAddress;
}
