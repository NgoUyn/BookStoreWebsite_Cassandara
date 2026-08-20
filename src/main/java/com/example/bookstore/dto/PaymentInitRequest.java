package com.example.bookstore.dto;

import com.example.bookstore.model.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentInitRequest {
    @NotNull
    private Long orderId;

    @NotNull
    private PaymentMethod paymentMethod;

    private String returnUrl; // Where to redirect after payment
}
