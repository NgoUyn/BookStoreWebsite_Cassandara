package com.example.bookstore.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckoutMeRequest {
    @NotBlank
    private String shippingAddress;

    private String couponCode;
}
