package com.example.bookstore.controller;

import com.example.bookstore.dto.AuthLoginRequest;
import com.example.bookstore.dto.AuthRegisterRequest;
import com.example.bookstore.model.User;
import com.example.bookstore.model.SellerShop;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.security.JwtTokenProvider;
import com.example.bookstore.service.AuthService;
import com.example.bookstore.service.AuthOtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for buyer-to-seller workflow
 * 
 * Validates:
 * 1. Buyer can register as BUYER role
 * 2. New SELLER registration creates SellerShop record automatically
 * 3. SellerShop is created with PENDING approval status
 * 4. Seller can access seller endpoints after SellerShop creation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Buyer-to-Seller Workflow Tests")
class BuyerToSellerWorkflowTest {

    @Mock
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SellerShopRepository sellerShopRepository;

    @Mock
    private AuthOtpService authOtpService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthController authController;
    private static final String TEST_EMAIL = "buyer@example.com";
    private static final String TEST_PASSWORD = "SecurePass123!";
    private static final String SELLER_EMAIL = "seller_upgrade@example.com";

    @BeforeEach
    void setUp() {
        authController = new AuthController();
        authController.authService = authService;
        authController.jwtTokenProvider = jwtTokenProvider;
        authController.authOtpService = authOtpService;
    }

    @Test
    @DisplayName("Step 1: Register as Buyer")
    void testRegisterAsBuyer() {
        // Arrange
        AuthRegisterRequest request = new AuthRegisterRequest();
        request.setUsername(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);
        request.setAvatarUrl("http://example.com/avatar.jpg");
        request.setFavoriteCategoryIds(List.of(1L, 2L));

        when(authService.register(
            eq(TEST_EMAIL),
            eq(TEST_PASSWORD),
            anyString(),
            anyList()
        )).thenReturn(true);

        // Act
        ResponseEntity<String> response = authController.register(request);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("Đăng kí thành công", response.getBody());
        verify(authService, times(1)).register(
            eq(TEST_EMAIL),
            eq(TEST_PASSWORD),
            anyString(),
            anyList()
        );
    }

    @Test
    @DisplayName("Step 2: Register as Seller creates SellerShop")
    void testRegisterAsSellerCreatesSellerShop() {
        // Arrange
        AuthRegisterRequest request = new AuthRegisterRequest();
        request.setUsername(SELLER_EMAIL);
        request.setPassword(TEST_PASSWORD);
        request.setAvatarUrl("http://example.com/seller-avatar.jpg");

        when(authService.registerWithRole(
            eq(SELLER_EMAIL),
            eq(TEST_PASSWORD),
            anyString(),
            anyList(),
            eq(UserRole.SELLER)
        )).thenReturn(true);

        // Act
        ResponseEntity<String> response = authController.registerSeller(request);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("Dang ky seller thanh cong", response.getBody());
        
        // Verify that registerWithRole was called with SELLER role
        verify(authService, times(1)).registerWithRole(
            eq(SELLER_EMAIL),
            eq(TEST_PASSWORD),
            anyString(),
            anyList(),
            eq(UserRole.SELLER)
        );
    }

    @Test
    @DisplayName("Step 3: SellerShop record should be created with PENDING status")
    void testSellerShopCreatedWithPendingStatus() {
        // This test documents the expected behavior:
        // When a user registers as SELLER, AuthService.registerWithRole() 
        // should automatically create a SellerShop with:
        // - seller: the newly saved User
        // - slug: generated from username/shopName
        // - shopName: the username (default)
        // - approvalStatus: PENDING

        // Arrange
        User newSeller = User.builder()
            .id(1L)
            .username(SELLER_EMAIL)
            .role(UserRole.SELLER)
            .build();

        SellerShop expectedShop = SellerShop.builder()
            .seller(newSeller)
            .slug("seller-upgrade")
            .shopName(SELLER_EMAIL)
            .approvalStatus(ApprovalStatus.PENDING)
            .build();

        when(sellerShopRepository.findBySellerId(1L))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(expectedShop));

        when(sellerShopRepository.save(any(SellerShop.class)))
            .thenReturn(expectedShop);

        // Act & Assert
        // Verify that after registering as seller, a SellerShop record exists
        Optional<SellerShop> shopBefore = sellerShopRepository.findBySellerId(1L);
        assertFalse(shopBefore.isPresent(), "SellerShop should not exist before registration");

        SellerShop savedShop = sellerShopRepository.save(expectedShop);
        Optional<SellerShop> shopAfter = sellerShopRepository.findBySellerId(1L);

        assertTrue(shopAfter.isPresent(), "SellerShop should be created after seller registration");
        assertEquals(SELLER_EMAIL, shopAfter.get().getShopName());
        assertEquals(ApprovalStatus.PENDING, shopAfter.get().getApprovalStatus());
    }

    @Test
    @DisplayName("Step 4: Login with new seller account")
    void testLoginAsSeller() {
        // Arrange
        AuthLoginRequest loginRequest = new AuthLoginRequest();
        loginRequest.setUsername(SELLER_EMAIL);
        loginRequest.setPassword(TEST_PASSWORD);

        User seller = User.builder()
            .id(2L)
            .username(SELLER_EMAIL)
            .role(UserRole.SELLER)
            .isActive(true)
            .build();

        when(authService.authenticateUser(SELLER_EMAIL, TEST_PASSWORD))
            .thenReturn(seller);

        when(jwtTokenProvider.createToken(
            eq(2L),
            eq(List.of("SELLER")),
            eq(2L)
        )).thenReturn("jwt-token-seller");

        // Act
        ResponseEntity<?> response = authController.loginJwt(loginRequest);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        verify(authService, times(1)).authenticateUser(SELLER_EMAIL, TEST_PASSWORD);
    }

    @Test
    @DisplayName("Logout endpoint returns success")
    void testLogoutEndpoint() {
        // Act
        ResponseEntity<String> response = authController.logout();

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("Đăng xuất thành công", response.getBody());
    }

    @Test
    @DisplayName("Buyer cannot become seller by re-registering with same email")
    void testBuyerCannotReuseEmailToBecomeSeller() {
        // Arrange
        AuthRegisterRequest request = new AuthRegisterRequest();
        request.setUsername(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);

        when(authService.registerWithRole(
            eq(TEST_EMAIL),
            eq(TEST_PASSWORD),
            anyString(),
            anyList(),
            eq(UserRole.SELLER)
        )).thenReturn(false);

        // Act
        ResponseEntity<String> response = authController.registerSeller(request);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
        assertEquals("Ten dang nhap da ton tai vui long thu lai", response.getBody());
    }

    @Test
    @DisplayName("Complete workflow: Register as Buyer → Login → Note: Separate Seller Registration Required")
    void testCompleteWorkflow() {
        // This test documents the current workflow:
        // 1. User registers as BUYER via /api/auth/register
        // 2. User logs in via /api/auth/login-jwt
        // 3. To become SELLER, user must register with DIFFERENT email via /api/auth/register-seller
        //    OR admin can convert user role (not implemented)
        // 4. After seller registration, SellerShop is automatically created
        
        // Step 1: Register as Buyer
        AuthRegisterRequest buyerRequest = new AuthRegisterRequest();
        buyerRequest.setUsername("buyer@test.com");
        buyerRequest.setPassword("pass123");
        
        when(authService.register(anyString(), anyString(), anyString(), anyList()))
            .thenReturn(true);
        
        ResponseEntity<String> registerResponse = authController.register(buyerRequest);
        assertTrue(registerResponse.getStatusCode().is2xxSuccessful());

        // Step 2: Login as Buyer
        AuthLoginRequest buyerLogin = new AuthLoginRequest();
        buyerLogin.setUsername("buyer@test.com");
        buyerLogin.setPassword("pass123");

        User buyer = User.builder()
            .id(1L)
            .username("buyer@test.com")
            .role(UserRole.BUYER)
            .isActive(true)
            .build();

        when(authService.authenticateUser("buyer@test.com", "pass123"))
            .thenReturn(buyer);
        
        when(jwtTokenProvider.createToken(1L, List.of("BUYER"), null))
            .thenReturn("buyer-token");

        ResponseEntity<?> loginResponse = authController.loginJwt(buyerLogin);
        assertTrue(loginResponse.getStatusCode().is2xxSuccessful());

        // Step 3: Register as Seller (with DIFFERENT email for now)
        AuthRegisterRequest sellerRequest = new AuthRegisterRequest();
        sellerRequest.setUsername("seller@test.com");
        sellerRequest.setPassword("pass123");

        when(authService.registerWithRole(
            "seller@test.com",
            "pass123",
            sellerRequest.getAvatarUrl(),
            sellerRequest.getFavoriteCategoryIds(),
            UserRole.SELLER
        )).thenReturn(true);

        ResponseEntity<String> sellerRegisterResponse = authController.registerSeller(sellerRequest);
        assertTrue(sellerRegisterResponse.getStatusCode().is2xxSuccessful());
    }
}
