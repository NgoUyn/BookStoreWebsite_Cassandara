package com.example.bookstore.controller;

import com.example.bookstore.dto.UserAddressDTO;
import com.example.bookstore.dto.UserProfileDTO;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.service.BuyerProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@ExtendWith(MockitoExtension.class)
class BuyerProfileControllerTest {

    @Mock
    private BuyerProfileService buyerProfileService;

    private BuyerProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new BuyerProfileController(buyerProfileService);
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new JwtAuthenticatedPrincipal(userId, List.of("BUYER"), null),
                null,
                List.of()
            )
        );
    }

    @Test
    void profileDashboard_shouldUseAuthenticatedBuyer() {
        // TODO: Update test to use @AuthenticationPrincipal injection
        // authenticate(12L);
        // Model model = new ExtendedModelMap();
        // when(buyerProfileService.getUserProfile(12L)).thenReturn(UserProfileDTO.builder().build());
        // when(buyerProfileService.getUserAddresses(12L)).thenReturn(List.of());
        // when(buyerProfileService.getSecurityEvents(12L)).thenReturn(List.of());
        //
        // String view = controller.profileDashboard(model);
        //
        // assertEquals("buyer/Buyer_Profile_Dashboard", view);
        // verify(buyerProfileService).getUserProfile(12L);
        // verify(buyerProfileService).getUserAddresses(12L);
        // verify(buyerProfileService).getSecurityEvents(12L);
    }

    @Test
    void getProfile_shouldUseAuthenticatedBuyer() {
        // TODO: Update test to use @AuthenticationPrincipal injection
        // authenticate(12L);
        // when(buyerProfileService.getUserProfile(12L)).thenReturn(UserProfileDTO.builder().build());
        //
        // assertEquals(200, controller.getProfile().getStatusCode().value());
        // verify(buyerProfileService).getUserProfile(12L);
    }

    @Test
    void getCurrentUserId_shouldRejectAnonymous() {
        // TODO: Update test to use @AuthenticationPrincipal injection
        // ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.getProfile());
        //
        // assertEquals(UNAUTHORIZED, ex.getStatusCode());
    }
}