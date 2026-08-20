package com.example.bookstore.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtTokenProviderTest {

    @Test
    void tokenShouldCarryUserRolesAndSellerId() {
        JwtTokenProvider provider = new JwtTokenProvider("test-secret", 3600);

        String token = provider.createToken(42L, List.of("SELLER", "BUYER"), 42L);

        assertEquals(42L, provider.extractUserId(token));
        assertEquals(List.of("SELLER", "BUYER"), provider.extractRoles(token));
        assertEquals(42L, provider.extractSellerId(token));
    }

    @Test
    void sellerIdShouldBeNullForBuyerToken() {
        JwtTokenProvider provider = new JwtTokenProvider("test-secret", 3600);

        String token = provider.createToken(99L, List.of("BUYER"), null);

        assertEquals(99L, provider.extractUserId(token));
        assertEquals(List.of("BUYER"), provider.extractRoles(token));
        assertNull(provider.extractSellerId(token));
    }

    @Test
    void refreshingTokenShouldGenerateNewAccessToken() {
        JwtTokenProvider provider = new JwtTokenProvider("test-secret", 3600);

        // 1. Initial login: create an access token
        String initialToken = provider.createToken(123L, List.of("BUYER"), null);
        assertEquals(123L, provider.extractUserId(initialToken));

        // 2. Simulate a refresh request later. The refresh logic (not in this class)
        // would validate a refresh token and get the user ID (123L).
        // Then it would call createToken again to issue a new access token.
        Long userIdFromRefreshToken = 123L;
        List<String> rolesFromUser = List.of("BUYER");
        Long sellerIdFromUser = null;

        String newAccessToken = provider.createToken(userIdFromRefreshToken, rolesFromUser, sellerIdFromUser);

        // 3. Verify the new access token is valid and for the correct user
        assertEquals(123L, provider.extractUserId(newAccessToken));
        assertEquals(List.of("BUYER"), provider.extractRoles(newAccessToken));
        assertNull(provider.extractSellerId(newAccessToken));
    }
}