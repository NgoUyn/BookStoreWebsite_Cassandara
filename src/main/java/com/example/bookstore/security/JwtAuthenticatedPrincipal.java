package com.example.bookstore.security;

import java.util.List;

public record JwtAuthenticatedPrincipal(
    Long userId,
    List<String> roles,
    Long sellerId
) {
    public JwtAuthenticatedPrincipal {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}