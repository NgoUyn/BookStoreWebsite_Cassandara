package com.example.bookstore.controller;

import com.example.bookstore.dto.AuthLoginRequest;
import com.example.bookstore.dto.AuthRegisterRequest;
import com.example.bookstore.dto.EmailOtpRequest;
import com.example.bookstore.dto.EmailOtpVerifyRequest;
import com.example.bookstore.dto.FirebaseLoginRequest;
import com.example.bookstore.dto.RefreshTokenRequest;
import com.example.bookstore.dto.TokenRefreshResponse;
import com.example.bookstore.dto.UserProfileResponse;
import com.example.bookstore.dto.UserProfileUpdateRequest;
import com.example.bookstore.model.RefreshToken;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.security.JwtTokenProvider;
import com.example.bookstore.service.AuthOtpService;
import com.example.bookstore.service.AuthService;
import com.example.bookstore.service.RefreshTokenService;
import com.example.bookstore.service.FirebaseAuthService;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController // Vẫn là báo cho Spring Boot biết đây là chỗ tạo API
@CrossOrigin("*") // kiểu cấp thẻ VIP để auth được quyền trỏ vô data của SP vậy á
@RequestMapping("/api/auth") // Đặt địa chỉ gốc là /api/auth
public class AuthController {

    @Autowired
    AuthService authService; //Thêm bộ não xử lý

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    AuthOtpService authOtpService;

    @Autowired
    RefreshTokenService refreshTokenService;

    @Autowired
    FirebaseAuthService firebaseAuthService;

    @Autowired
    UserRepository userRepository;

    @PostMapping("/otp/request")
    public ResponseEntity<?> requestRegisterOtp(@Valid @RequestBody EmailOtpRequest request) {
        String fallbackOtp = authOtpService.requestOtp(request.getEmail());
        if (fallbackOtp != null) {
            // SMTP not configured or email send failed - return OTP for dev/test
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "[DEV MODE] SMTP chua config. OTP trong response (production se gui email)",
                "otp", fallbackOtp
            ));
        }
        // Email sent successfully
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "OTP da duoc gui. Vui long kiem tra email."
        ));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<String> verifyRegisterOtp(@Valid @RequestBody EmailOtpVerifyRequest request) {
        boolean isValid = authOtpService.verifyOtp(request.getEmail(), request.getOtp());
        if (!isValid) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("OTP khong dung hoac da het han");
        }
        return ResponseEntity.ok("Xac thuc OTP thanh cong");
    }

//    API đăng ký
//    đường dẫn là POST /api/auth/register
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody AuthRegisterRequest request){
        boolean isSuccess = authService.register(
            request.getUsername(),
            request.getPassword(),
            request.getAvatarUrl(),
            request.getFavoriteCategoryIds()
        );
        if (isSuccess){
//            tra ve 200
            return ResponseEntity.ok("Đăng kí thành công");
        }
        else {
//            Trả về 400 nếu trùng tên
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Tên đăng nhập đã tồn tại vui lòng thử lại");
        }
    }

    @PostMapping("/register-seller")
    public ResponseEntity<String> registerSeller(@Valid @RequestBody AuthRegisterRequest request) {
        boolean isSuccess = authService.registerWithRole(
            request.getUsername(),
            request.getPassword(),
            request.getAvatarUrl(),
            request.getFavoriteCategoryIds(),
            UserRole.SELLER
        );

        if (isSuccess){
            return ResponseEntity.ok("Dang ky seller thanh cong");
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Ten dang nhap da ton tai vui long thu lai");
    }

    @PostMapping("/register-admin")
    public ResponseEntity<String> registerAdmin(@Valid @RequestBody AuthRegisterRequest request) {
        boolean isSuccess = authService.registerWithRole(
            request.getUsername(),
            request.getPassword(),
            request.getAvatarUrl(),
            request.getFavoriteCategoryIds(),
            UserRole.ADMIN
        );

        if (isSuccess){
            return ResponseEntity.ok("Dang ky admin thanh cong");
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Ten dang nhap da ton tai vui long thu lai");
    }

//    API đăng nhập
//    /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody AuthLoginRequest request){
        User user = authService.authenticateUser(request.getUsername(), request.getPassword());

        if(user != null){
            if (!user.isActive()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Tài khoản bị từ chối đăng nhập");
            }
            return ResponseEntity.ok("Đăng nhập thành công");
        }
        else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Sai tên đăng nhập hoặc mật khẩu");
        }
    }

    @PostMapping("/login-jwt")
    public ResponseEntity<?> loginJwt(@Valid @RequestBody AuthLoginRequest request) {
        User authenticated = authService.authenticateUser(request.getUsername(), request.getPassword());
        if (authenticated == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Sai tên đăng nhập hoặc mật khẩu");
        }

        if (!authenticated.isActive()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Tài khoản bị từ chối đăng nhập");
        }

        Long sellerId = authenticated.getRole() == UserRole.SELLER
            ? authenticated.getId()
            : null;
        java.util.List<String> roles = java.util.List.of(authenticated.getRole().name());
        String accessToken = jwtTokenProvider.createToken(authenticated.getId(), roles, sellerId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(authenticated.getId());

        java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("tokenType", "Bearer");
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken.getToken());
        response.put("userId", authenticated.getId());
        response.put("role", authenticated.getRole().name());
        response.put("roles", roles);
        response.put("sellerId", sellerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    Long sellerId = user.getRole() == UserRole.SELLER ? user.getId() : null;
                    java.util.List<String> roles = java.util.List.of(user.getRole().name());
                    String newAccessToken = jwtTokenProvider.createToken(user.getId(), roles, sellerId);

                    return ResponseEntity.ok(new TokenRefreshResponse(newAccessToken, requestRefreshToken));
                })
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Refresh token is not in database!"
                ));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout() {
        // Since JWT is stateless, logout on server side doesn't invalidate the token
        // Client should remove token from localStorage/sessionStorage
        return ResponseEntity.ok("Đăng xuất thành công");
    }

    /**
     * DEV-ONLY: Quick login for seeded accounts (no OTP required)
     * Used in development when email cannot receive OTP
     * 
     * Example:
     * POST /api/auth/dev-login
     * {
     *   "username": "shop_nha_nam@gmail.com",
     *   "password": "seller123"
     * }
     * 
     * Response:
     * {
     *   "tokenType": "Bearer",
     *   "accessToken": "eyJ0eXAi...",
     *   "userId": 2,
     *   "role": "SELLER",
     *   "sellerId": 2
     * }
     */
    @PostMapping("/dev-login")
    public ResponseEntity<?> devLogin(@Valid @RequestBody AuthLoginRequest request) {
        // No OTP verification needed - directly authenticate
        User authenticated = authService.authenticateUser(request.getUsername(), request.getPassword());
        if (authenticated == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Sai tên đăng nhập hoặc mật khẩu");
        }

        if (!authenticated.isActive()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Tài khoản bị từ chối đăng nhập");
        }

        Long sellerId = authenticated.getRole() == UserRole.SELLER
            ? authenticated.getId()
            : null;
        java.util.List<String> roles = java.util.List.of(authenticated.getRole().name());
        String accessToken = jwtTokenProvider.createToken(authenticated.getId(), roles, sellerId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(authenticated.getId());

        java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("tokenType", "Bearer");
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken.getToken());
        response.put("userId", authenticated.getId());
        response.put("role", authenticated.getRole().name());
        response.put("roles", roles);
        response.put("sellerId", sellerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Firebase Google Login
     * POST /api/auth/firebase/google
     * 
     * Frontend gửi Firebase ID Token lên, backend xác thực và trả về JWT token
     * 
     * Request body:
     * {
     *   "idToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
     * }
     * 
     * Response:
     * {
     *   "tokenType": "Bearer",
     *   "accessToken": "eyJ0eXAi...",
     *   "userId": 1,
     *   "role": "BUYER",
     *   "roles": ["BUYER"],
     *   "sellerId": null,
     *   "email": "user@gmail.com",
     *   "name": "Nguyen Van A"
     * }
     */
    @PostMapping("/firebase/google")
    public ResponseEntity<?> loginWithGoogle(@Valid @RequestBody FirebaseLoginRequest request) {
        try {
            // 1. Xác thực Firebase token
            User user = firebaseAuthService.authenticateFirebaseToken(request.getIdToken());

            // 2. Tạo JWT token như hệ thống cũ
            Long sellerId = user.getRole() == UserRole.SELLER
                ? user.getId()
                : null;
            java.util.List<String> roles = java.util.List.of(user.getRole().name());
            String accessToken = jwtTokenProvider.createToken(user.getId(), roles, sellerId);
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

            // 3. Trả về response
            java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("tokenType", "Bearer");
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken.getToken());
            response.put("userId", user.getId());
            response.put("role", user.getRole().name());
            response.put("roles", roles);
            response.put("sellerId", sellerId);
            response.put("email", user.getEmail());
            response.put("name", (user.getFirstName() != null ? user.getFirstName() : "") + " " + (user.getLastName() != null ? user.getLastName() : ""));
            response.put("avatarUrl", user.getAvatarUrl());

            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(java.util.Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(java.util.Map.of("error", e.getMessage()));
        } catch (Exception e) {
            // Log đầy đủ stack trace để debug
            System.err.println("=== Firebase Google Login Error ===");
            System.err.println("Exception type: " + e.getClass().getName());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace(System.err);
            System.err.println("===================================");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(java.util.Map.of("error", "Firebase token không hợp lệ hoặc đã hết hạn"));
        }
    }

    @GetMapping("/profile/{userId}")
    public UserProfileResponse getProfile(@PathVariable Long userId) {

        return authService.getProfile(userId);
    }

    @PostMapping("/become-seller")
    @PreAuthorize("hasAuthority('BUYER')")
    public ResponseEntity<?> becomeSeller(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principalUser,
            @RequestBody Map<String, String> body
    ) {
        // principalUser may be null depending on security config; try JwtAuthenticatedPrincipal otherwise
        Long userId = null;
        try {
            // try to extract id from Security Context principal if it's our JwtAuthenticatedPrincipal
            Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof com.example.bookstore.security.JwtAuthenticatedPrincipal jp) {
                userId = jp.userId();
            }
        } catch (Exception ignored) {}

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Không có thông tin xác thực");
        }

        try {
            String shopName = body.getOrDefault("shopName", null);
            String shopAddress = body.getOrDefault("address", null);
            String city = body.getOrDefault("city", null);
            String province = body.getOrDefault("province", null);
            // Submit a seller application instead of immediately upgrading role.
            authService.submitSellerApplication(userId, shopName, shopAddress, city, province);

            // Return 202 Accepted to indicate the request was received and is pending admin approval
            return ResponseEntity.status(HttpStatus.ACCEPTED).body("Yêu cầu trở thành người bán đã được gửi. Vui lòng chờ admin duyệt.");
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PutMapping("/profile/{userId}")
    @PreAuthorize("hasPermission(#userId, 'User', 'update')")
    public ResponseEntity<?> updateProfile(
        @PathVariable Long userId,
        @Valid @RequestBody UserProfileUpdateRequest request
    ) {

        // 2 Chống thêm các thể html 
        if (request.getShopName() != null) {
            request.setShopName(request.getShopName().replaceAll("<", "<").replaceAll(">", ">"));
        }
        // 3. Đẩy xuống Service xử lý
        try {
            UserProfileResponse response = authService.updateProfile(userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Check if a phone number already exists in the system.
     * Used by checkout page to validate phone number uniqueness.
     * 
     * GET /api/auth/check-phone?phone=0123456789
     * 
     * Response:
     * {
     *   "exists": true
     * }
     */
    @GetMapping("/check-phone")
    public ResponseEntity<Map<String, Boolean>> checkPhone(@RequestParam String phone) {
        boolean exists = userRepository.existsByPhone(phone);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

}
