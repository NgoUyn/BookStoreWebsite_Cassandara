package com.example.bookstore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CheckoutRequest {
    @NotNull
    private Long buyerId;

    @NotBlank
    private String shippingAddress;

    private String couponCode;

    private List<Long> itemIds;
}
