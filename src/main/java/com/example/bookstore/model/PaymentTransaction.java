package com.example.bookstore.model;

import com.example.bookstore.model.enums.PaymentMethod;
import com.example.bookstore.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long amount; // Amount in VND (e.g., 1,000,000)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "NVARCHAR(30)")
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "NVARCHAR(30)")
    private PaymentStatus status;

    @Column(length = 100)
    private String transactionCode; // VNPay transaction code or reference

    @Column(length = 500)
    private String paymentUrl; // VNPay payment redirect URL

    @Column(length = 1000)
    private String responseCode; // VNPay response code

    @Column(length = 1000)
    private String responseMessage; // VNPay response message

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt; // When payment was completed

    @Column(name = "expired_at")
    private LocalDateTime expiredAt; // Payment link expiry

    @Column(length = 500)
    private String failureReason; // Reason if payment failed

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = PaymentStatus.PENDING;
        }
        if (expiredAt == null) {
            // Payment link valid for 15 minutes
            expiredAt = createdAt.plusMinutes(15);
        }
    }
}
