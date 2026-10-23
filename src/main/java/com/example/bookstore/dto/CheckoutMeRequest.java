package com.example.bookstore.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CheckoutMeRequest {
    @NotBlank
    private String shippingAddress;

    private String couponCode;

    private List<Long> itemIds;
}
