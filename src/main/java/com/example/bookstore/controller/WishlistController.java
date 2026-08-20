package com.example.bookstore.controller;

import com.example.bookstore.dto.WishlistActionResponse;
import com.example.bookstore.dto.WishlistItemResponse;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.service.WishlistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
 

import java.util.List;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/wishlist")
public class WishlistController {

    @Autowired
    private WishlistService wishlistService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public List<WishlistItemResponse> getMyWishlist(@org.springframework.security.core.annotation.AuthenticationPrincipal JwtAuthenticatedPrincipal principal) {
        return wishlistService.getWishlist(getCurrentUserId(principal));
    }

    @PostMapping("/me/{bookId}")
    @PreAuthorize("isAuthenticated()")
    public WishlistActionResponse toggleWishlist(@org.springframework.security.core.annotation.AuthenticationPrincipal JwtAuthenticatedPrincipal principal, @PathVariable Long bookId) {
        return wishlistService.toggleWishlist(getCurrentUserId(principal), bookId);
    }

    @DeleteMapping("/me/{bookId}")
    @PreAuthorize("isAuthenticated()")
    public WishlistActionResponse removeFromWishlist(@org.springframework.security.core.annotation.AuthenticationPrincipal JwtAuthenticatedPrincipal principal, @PathVariable Long bookId) {
        return wishlistService.removeFromWishlist(getCurrentUserId(principal), bookId);
    }

    private Long getCurrentUserId(JwtAuthenticatedPrincipal principal) {
        if (principal != null) return principal.userId();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui long dang nhap");
    }
}
