package com.example.bookstore.dto;

import com.example.bookstore.model.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PaymentStatusResponse {
    private Long transactionId;
    private Long orderId;
    private Long amount;
    private String method;
    private PaymentStatus status;
    private String transactionCode;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private String message;
    private String failureReason;

    public static PaymentStatusResponse fromPaymentTransaction(
            com.example.bookstore.model.PaymentTransaction transaction) {
        return PaymentStatusResponse.builder()
                .transactionId(transaction.getId())
                .orderId(transaction.getOrder().getId())
                .amount(transaction.getAmount())
                .method(transaction.getMethod().getDisplayName())
                .status(transaction.getStatus())
                .transactionCode(transaction.getTransactionCode())
                .paidAt(transaction.getPaidAt())
                .createdAt(transaction.getCreatedAt())
                .message(transaction.getResponseMessage())
                .failureReason(transaction.getFailureReason())
                .build();
    }
}
