package com.example.bookstore.controller;

import com.example.bookstore.dto.CartItemUpsertRequest;
import com.example.bookstore.dto.CartResponse;
import com.example.bookstore.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/buyer/{buyerId}")
    public CartResponse getBuyerCart(@PathVariable Long buyerId) {
        return cartService.getBuyerCart(buyerId);
    }

    @PostMapping("/buyer/{buyerId}/items")
    public CartResponse addItem(
            @PathVariable Long buyerId,
            @Valid @RequestBody CartItemUpsertRequest request
    ) {
        return cartService.addItem(buyerId, request);
    }

    @PatchMapping("/buyer/{buyerId}/items/{itemId}")
    public CartResponse updateItemQuantity(
            @PathVariable Long buyerId,
            @PathVariable Long itemId,
            @RequestParam Integer quantity
    ) {
        return cartService.updateItemQuantity(buyerId, itemId, quantity);
    }

    @DeleteMapping("/buyer/{buyerId}/items/{itemId}")
    public CartResponse removeItem(
            @PathVariable Long buyerId,
            @PathVariable Long itemId
    ) {
        return cartService.removeItem(buyerId, itemId);
    }
}
