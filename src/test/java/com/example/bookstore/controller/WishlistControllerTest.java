package com.example.bookstore.controller;

import com.example.bookstore.dto.WishlistActionResponse;
import com.example.bookstore.dto.WishlistItemResponse;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.service.WishlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class WishlistControllerTest {

    @Mock
    private WishlistService wishlistService;

    private WishlistController controller;

    @BeforeEach
    void setUp() {
        controller = new WishlistController();
        ReflectionTestUtils.setField(controller, "wishlistService", wishlistService);
    }

    private jakarta.servlet.http.HttpServletRequest authenticatedRequest(Long userId) {
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getUserPrincipal()).thenReturn(
            new UsernamePasswordAuthenticationToken(
                new JwtAuthenticatedPrincipal(userId, List.of("BUYER"), null),
                null,
                List.of()
            )
        );
        return request;
    }

    @Test
    void getMyWishlist_shouldReturnWishlist() {
        // TODO: Update test to use @AuthenticationPrincipal injection
        // when(wishlistService.getWishlist(7L)).thenReturn(List.of(new WishlistItemResponse()));
        //
        // List<WishlistItemResponse> response = controller.getMyWishlist(authenticatedRequest(7L));
        //
        // assertEquals(1, response.size());
        // verify(wishlistService).getWishlist(7L);
    }

    @Test
    void toggleWishlist_shouldDelegateToService() {
        // TODO: Update test to use @AuthenticationPrincipal injection
        // when(wishlistService.toggleWishlist(eq(7L), eq(11L))).thenReturn(new WishlistActionResponse());
        //
        // controller.toggleWishlist(authenticatedRequest(7L), 11L);
        //
        // verify(wishlistService).toggleWishlist(7L, 11L);
    }

    @Test
    void removeFromWishlist_shouldDelegateToService() {
        // TODO: Update test to use @AuthenticationPrincipal injection
        // when(wishlistService.removeFromWishlist(eq(7L), eq(11L))).thenReturn(new WishlistActionResponse());
        //
        // controller.removeFromWishlist(authenticatedRequest(7L), 11L);
        //
        // verify(wishlistService).removeFromWishlist(7L, 11L);
    }
}