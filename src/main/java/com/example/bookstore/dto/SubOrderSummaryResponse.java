package com.example.bookstore.dto;

import com.example.bookstore.model.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubOrderSummaryResponse {
    private Long subOrderId;
    private Long orderId;
    private Long sellerId;
    private String sellerName;
    private String buyerUsername;
    private String itemSummary;
    private Integer itemCount;
    private OrderStatus status;
    private Double subTotal;
     private Double totalAmount;
}
