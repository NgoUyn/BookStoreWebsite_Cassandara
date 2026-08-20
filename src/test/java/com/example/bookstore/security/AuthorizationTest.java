package com.example.bookstore.security;

import com.example.bookstore.model.User;
import com.example.bookstore.model.SellerShop;
import com.example.bookstore.model.SubOrder;
import com.example.bookstore.model.Order;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.model.enums.OrderStatus;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.repository.SubOrderRepository;
import com.example.bookstore.repository.OrderRepository;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.security.CustomPermissionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Authorization tests for seller order management.
 * Verifies that:
 * 1. Sellers with APPROVED shops can access their orders
 * 2. Sellers with UNAPPROVED shops get 403 error
 * 3. Buyers cannot access seller endpoints
 * 4. JWT tokens with correct sellerId allow access
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Authorization Tests - Seller Order Management")
class AuthorizationTest {

    private CustomPermissionEvaluator permissionEvaluator;

    @Mock
    private SellerShopRepository sellerShopRepository;

    @Mock
    private SubOrderRepository subOrderRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private Authentication authentication;

    private JwtAuthenticatedPrincipal jwtPrincipal;
    private User seller;
    private User buyer;
    
    private Collection<? extends GrantedAuthority> sellerAuthorities;
    private Collection<? extends GrantedAuthority> buyerAuthorities;
    private Collection<? extends GrantedAuthority> adminAuthorities;

    @BeforeEach
    void setUp() {
        permissionEvaluator = new CustomPermissionEvaluator(
            null, // BookRepository not needed for these tests
            orderRepository,
            subOrderRepository,
            sellerShopRepository
        );

        // Create test sellers and buyers
        seller = User.builder()
            .id(100L)
            .username("seller123")
            .shopName("My Shop")
            .role(UserRole.SELLER)
            .isActive(true)
            .build();

        buyer = User.builder()
            .id(200L)
            .username("buyer123")
            .role(UserRole.BUYER)
            .isActive(true)
            .build();

        // Create JWT principal for seller
        jwtPrincipal = new JwtAuthenticatedPrincipal(100L, Arrays.asList("SELLER"), 100L);
        
        // Initialize authority collections
        sellerAuthorities = new ArrayList<>(Arrays.asList(new SimpleGrantedAuthority("ROLE_SELLER")));
        buyerAuthorities = new ArrayList<>(Arrays.asList(new SimpleGrantedAuthority("ROLE_BUYER")));
        adminAuthorities = new ArrayList<>(Arrays.asList(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("SELLER with APPROVED shop can update SubOrder status")
    void testSellerWithApprovedShopCanUpdateSubOrder() {
        // Arrange
        Long subOrderId = 1L;
        doReturn(jwtPrincipal).when(authentication).getPrincipal();
        doReturn(sellerAuthorities).when(authentication).getAuthorities();
        
        // Mock repository calls
        doReturn(true).when(subOrderRepository).existsByIdAndSellerId(subOrderId, 100L);
        doReturn(true).when(sellerShopRepository).existsBySellerIdAndApprovalStatus(100L, ApprovalStatus.APPROVED);

        // Act
        boolean result = permissionEvaluator.hasPermission(
            authentication,
            subOrderId,
            "SubOrder",
            "status"
        );

        // Assert
        assertTrue(result, "SELLER with approved shop should be able to update SubOrder status");
        verify(sellerShopRepository).existsBySellerIdAndApprovalStatus(100L, ApprovalStatus.APPROVED);
    }

    @Test
    @DisplayName("SELLER with UNAPPROVED shop cannot update SubOrder status - 403 Forbidden")
    void testSellerWithUnapprovedShopCannotUpdateSubOrder() {
        // Arrange
        Long subOrderId = 1L;
        doReturn(jwtPrincipal).when(authentication).getPrincipal();
        doReturn(sellerAuthorities).when(authentication).getAuthorities();
        
        // Mock repository calls
        doReturn(true).when(subOrderRepository).existsByIdAndSellerId(subOrderId, 100L);
        // Shop is NOT approved
        doReturn(false).when(sellerShopRepository).existsBySellerIdAndApprovalStatus(100L, ApprovalStatus.APPROVED);

        // Act
        boolean result = permissionEvaluator.hasPermission(
            authentication,
            subOrderId,
            "SubOrder",
            "status"
        );

        // Assert
        assertFalse(result, "SELLER with unapproved shop should NOT be able to update SubOrder status");
    }

    @Test
    @DisplayName("SELLER cannot access SubOrder from another seller")
    void testSellerCannotAccessAnotherSellerSubOrder() {
        // Arrange
        Long subOrderId = 1L;
        Long anotherSellerId = 999L;
        
        doReturn(jwtPrincipal).when(authentication).getPrincipal();
        doReturn(sellerAuthorities).when(authentication).getAuthorities();
        
        // SubOrder belongs to different seller
        doReturn(false).when(subOrderRepository).existsByIdAndSellerId(subOrderId, 100L);

        // Act
        boolean result = permissionEvaluator.hasPermission(
            authentication,
            subOrderId,
            "SubOrder",
            "status"
        );

        // Assert
        assertFalse(result, "SELLER should not be able to access another seller's SubOrder");
    }

    @Test
    @DisplayName("BUYER cannot access seller endpoints")
    void testBuyerCannotAccessSellerEndpoints() {
        // Arrange
        Long subOrderId = 1L;
        JwtAuthenticatedPrincipal buyerPrincipal = new JwtAuthenticatedPrincipal(200L, Arrays.asList("BUYER"), null);
        
        doReturn(buyerPrincipal).when(authentication).getPrincipal();
        doReturn(buyerAuthorities).when(authentication).getAuthorities();

        // Act
        boolean result = permissionEvaluator.hasPermission(
            authentication,
            subOrderId,
            "SubOrder",
            "status"
        );

        // Assert
        assertFalse(result, "BUYER should not be able to update SubOrder status");
    }

    @Test
    @DisplayName("JWT token with null sellerId triggers fallback to userId for SELLER role")
    void testJwtTokenWithNullSellerIdFallsbackToUserId() {
        // Arrange - JWT principal with null sellerId but SELLER role
        JwtAuthenticatedPrincipal principalWithoutSellerId = 
            new JwtAuthenticatedPrincipal(100L, Arrays.asList("SELLER"), null);
        
        Long subOrderId = 1L;
        doReturn(principalWithoutSellerId).when(authentication).getPrincipal();
        doReturn(sellerAuthorities).when(authentication).getAuthorities();
        
        // Mock repository calls - fallback uses userId (100L) as sellerId
        doReturn(true).when(subOrderRepository).existsByIdAndSellerId(subOrderId, 100L);
        doReturn(true).when(sellerShopRepository).existsBySellerIdAndApprovalStatus(100L, ApprovalStatus.APPROVED);

        // Act
        boolean result = permissionEvaluator.hasPermission(
            authentication,
            subOrderId,
            "SubOrder",
            "status"
        );

        // Assert
        assertTrue(result, "Should fallback to userId when sellerId is null for SELLER role");
    }

    @Test
    @DisplayName("Admin can access all SubOrders regardless of seller")
    void testAdminCanAccessAllSubOrders() {
        // Arrange
        Long subOrderId = 1L;
        doReturn(jwtPrincipal).when(authentication).getPrincipal();
        doReturn(adminAuthorities).when(authentication).getAuthorities();

        // Act
        boolean result = permissionEvaluator.hasPermission(
            authentication,
            subOrderId,
            "SubOrder",
            "status"
        );

        // Assert
        assertTrue(result, "ADMIN should have access to all SubOrders");
        // Admin bypasses all checks
        verify(subOrderRepository, never()).existsByIdAndSellerId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("SELLER can READ SubOrder (their own)")
    void testSellerCanReadOwnSubOrder() {
        // Arrange
        Long subOrderId = 1L;
        doReturn(jwtPrincipal).when(authentication).getPrincipal();
        doReturn(sellerAuthorities).when(authentication).getAuthorities();
        
        doReturn(true).when(subOrderRepository).existsByIdAndSellerId(subOrderId, 100L);

        // Act
        boolean result = permissionEvaluator.hasPermission(
            authentication,
            subOrderId,
            "SubOrder",
            "read"
        );

        // Assert
        assertTrue(result, "SELLER should be able to read their own SubOrder");
    }

    @Test
    @DisplayName("Unauthenticated request cannot access any resource")
    void testUnauthenticatedRequestFails() {
        // Arrange - null authentication
        Long subOrderId = 1L;

        // Act
        boolean result = permissionEvaluator.hasPermission(
            null,
            subOrderId,
            "SubOrder",
            "status"
        );

        // Assert
        assertFalse(result, "Unauthenticated request should be denied");
    }
}
