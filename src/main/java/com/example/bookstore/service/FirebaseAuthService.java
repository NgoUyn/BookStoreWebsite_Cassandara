package com.example.bookstore.service;

import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class FirebaseAuthService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthService.class);

    @Autowired
    private UserRepository userRepository;

    /**
     * Xác thực Firebase ID Token và trả về User (tìm trong DB hoặc tạo mới)
     */
    @Transactional
    public User authenticateFirebaseToken(String idToken) throws Exception {
        // 1. Xác thực token với Firebase Admin SDK
        FirebaseToken decodedToken;
        try {
            decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            log.error("Firebase Auth exception khi verify token: {}", e.getMessage());
            log.error("AuthErrorCode: {}", e.getAuthErrorCode());
            log.error("ErrorCode: {}", e.getErrorCode());
            throw new IllegalArgumentException("Firebase token không hợp lệ: " + e.getMessage(), e);
        } catch (IllegalStateException e) {
            log.error("FirebaseApp chưa được khởi tạo đúng cách: {}", e.getMessage());
            throw new IllegalStateException("Firebase chưa được cấu hình trên server. Vui lòng kiểm tra file service account.", e);
        }

        String uid = decodedToken.getUid();           // Firebase UID
        String email = decodedToken.getEmail();       // Email từ Google
        String name = decodedToken.getName();         // Tên hiển thị
        String picture = decodedToken.getPicture();   // Ảnh đại diện

        log.info("Firebase token verified successfully for UID: {}, email: {}", uid, email);

        // 2. Kiểm tra user đã tồn tại trong DB chưa (dựa trên email)
        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            // User đã tồn tại → cập nhật thông tin nếu cần
            User user = existingUser.get();
            if (!user.isActive()) {
                throw new SecurityException("Tài khoản đã bị khóa");
            }
            // Cập nhật avatar từ Google nếu chưa có
            if (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
                user.setAvatarUrl(picture);
            }
            return userRepository.save(user);
        }

        // 3. User chưa tồn tại → tạo mới
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email không hợp lệ từ tài khoản Google");
        }

        User newUser = User.builder()
            .username(email)                    // Dùng email làm username
            .passwordHash("")                    // Không cần password
            .email(email)
            .firstName(extractFirstName(name))
            .lastName(extractLastName(name))
            .avatarUrl(picture)
            .role(UserRole.BUYER)                // Mặc định là BUYER
            .isActive(true)
            .build();

        return userRepository.save(newUser);
    }

    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        return parts[0]; // First name = từ đầu tiên
    }

    private String extractLastName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length <= 1) return "";
        return String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
    }
}
