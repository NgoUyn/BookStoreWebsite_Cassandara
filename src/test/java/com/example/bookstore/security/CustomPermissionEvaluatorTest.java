package com.example.bookstore.security;

import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.OrderRepository;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.repository.SubOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomPermissionEvaluatorTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SubOrderRepository subOrderRepository;

    @Mock
    private SellerShopRepository sellerShopRepository;

    private CustomPermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new CustomPermissionEvaluator(bookRepository, orderRepository, subOrderRepository, sellerShopRepository);
    }

    @Test
    void hasPermission_shouldAllowSellerToUpdateOwnBook() {
        User seller = User.builder()
            .id(10L)
            .username("seller")
            .passwordHash("x")
            .role(UserRole.SELLER)
            .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
            seller,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_SELLER"))
        );

        when(bookRepository.existsByIdAndSellerId(99L, 10L)).thenReturn(true);

        assertTrue(evaluator.hasPermission(authentication, 99L, "Book", "update"));
    }

    @Test
    void hasPermission_shouldDenyBuyerReadingAnotherUsersOrder() {
        User buyer = User.builder()
            .id(20L)
            .username("buyer")
            .passwordHash("x")
            .role(UserRole.BUYER)
            .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
            buyer,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_BUYER"))
        );

        when(orderRepository.existsByIdAndBuyerId(55L, 20L)).thenReturn(false);

        assertFalse(evaluator.hasPermission(authentication, 55L, "Order", "read"));
    }

    @Test
    void hasPermission_shouldAllowAdminRegardlessOfResourceOwnership() {
        User admin = User.builder()
            .id(1L)
            .username("admin")
            .passwordHash("x")
            .role(UserRole.ADMIN)
            .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
            admin,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertTrue(evaluator.hasPermission(authentication, 12345L, "Book", "delete"));
    }

    @Test
    void hasPermission_shouldAllowReadForApprovedBook() {
        User buyer = User.builder()
            .id(30L)
            .username("buyer2")
            .passwordHash("x")
            .role(UserRole.BUYER)
            .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
            buyer,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_BUYER"))
        );

        when(bookRepository.findApprovalStatusById(77L)).thenReturn(ApprovalStatus.APPROVED);

        assertTrue(evaluator.hasPermission(authentication, 77L, "Book", "read"));
    }
}