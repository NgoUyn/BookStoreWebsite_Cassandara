package com.example.bookstore.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentInitResponse {
    private Long transactionId;
    private Long orderId;
    private Long amount;
    private String paymentUrl; // Redirect URL for VNPay
    private String transactionCode;
    private String message;
}
