package com.example.bookstore.service;

import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.CategoryRepository;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.service.cluster.CustomerAnalysisService;
import com.example.bookstore.service.cluster.CustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Regression test cho luong dang nhap bang email + mat khau
 * (loi "POST /api/auth/login-jwt 400 (Bad Request)" va loi 500 khi hash rong).
 *
 * Bao ve 4 hanh vi:
 *   1. Tra theo username TRUOC, khong thay moi tra tiep theo email (khong phan biet hoa/thuong)
 *      -> tai khoan cu co username = "admin" VAN dang nhap duoc bang email "admin@bookom.vn".
 *   2. username duoc trim (khoang trang dau/cuoi lam @Email validation that bai -> 400).
 *   3. passwordHash rong (tai khoan Google) hoac hash seed gia "$2a$10$seedHash<id>"
 *      -> tra null (HTTP 400) thay vi de jbcrypt nem ra ngoai thanh HTTP 500.
 *   4. Mat khau dung -> tra ve user.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceAuthenticationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuthOtpService authOtpService;

    @Mock
    private SellerShopRepository sellerShopRepository;

    @Mock
    private SellerShopService sellerShopService;

    @Mock
    private CustomerAnalysisService customerAnalysisService;

    @Mock
    private CustomerService customerService;

    @InjectMocks
    private AuthService authService;

    private User user(Long id, String username, String passwordHash) {
        return User.builder()
            .id(id)
            .username(username)
            .passwordHash(passwordHash)
            .role(UserRole.BUYER)
            .isActive(true)
            .build();
    }

    @Test
    void login_shouldFallbackToEmail_whenUsernameNotFound() {
        User legacyAdmin = user(1L, "admin", BCrypt.hashpw("Admin123@", BCrypt.gensalt(10)));
        when(userRepository.findByUsername("admin@bookom.vn")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("admin@bookom.vn")).thenReturn(Optional.of(legacyAdmin));

        User authenticated = authService.authenticateUser("admin@bookom.vn", "Admin123@");

        assertNotNull(authenticated);
        assertEquals(1L, authenticated.getId());
    }

    @Test
    void login_shouldTrimUsernameBeforeLookup() {
        User admin = user(1509L, "admin@gmail.com", BCrypt.hashpw("Admin123@", BCrypt.gensalt(10)));
        when(userRepository.findByUsername("admin@gmail.com")).thenReturn(Optional.of(admin));

        User authenticated = authService.authenticateUser("  admin@gmail.com  ", "Admin123@");

        assertNotNull(authenticated);
        assertEquals(1509L, authenticated.getId());
    }

    @Test
    void login_shouldReturnNull_withEmptyPasswordHash() {
        // Tai khoan tao bang Google (Firebase) khong co passwordHash
        User googleUser = user(1507L, "uyengo1234@gmail.com", "");
        when(userRepository.findByUsername("uyengo1234@gmail.com")).thenReturn(Optional.of(googleUser));

        assertNull(authService.authenticateUser("uyengo1234@gmail.com", "Whatever123@"));
    }

    @Test
    void login_shouldReturnNull_withFakeSeedHash() {
        // 03_seed_reference.js sinh hash gia "$2a$10$seedHash<id>" (16 ky tu, khong phai bcrypt that)
        User fakeSeller = user(2L, "seller2@bookom.vn", "$2a$10$seedHash2");
        when(userRepository.findByUsername("seller2@bookom.vn")).thenReturn(Optional.of(fakeSeller));

        assertNull(authService.authenticateUser("seller2@bookom.vn", "seller123"));
    }

    @Test
    void login_shouldReturnNull_withWrongPassword() {
        User admin = user(1509L, "admin@gmail.com", BCrypt.hashpw("Admin123@", BCrypt.gensalt(10)));
        when(userRepository.findByUsername("admin@gmail.com")).thenReturn(Optional.of(admin));

        assertNull(authService.authenticateUser("admin@gmail.com", "sai-mat-khau"));
    }

    @Test
    void login_shouldReturnNull_withUnknownUser() {
        when(userRepository.findByUsername("khong-ton-tai@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("khong-ton-tai@gmail.com")).thenReturn(Optional.empty());

        assertNull(authService.authenticateUser("khong-ton-tai@gmail.com", "Batky123@"));
    }
}
