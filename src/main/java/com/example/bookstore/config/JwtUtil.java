package com.example.bookstore.config;

import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.security.JwtTokenProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JwtUtil - Utility class for JWT token operations.
 * <p>
 * Provides convenient methods for generating and validating JWT tokens
 * for seed data users and other internal use cases.
 * <p>
 * This class wraps {@link JwtTokenProvider} to simplify token generation
 * from {@link User} entities without manually constructing role lists.
 * <p>
 * Usage example:
 * <pre>{@code
 *     @Autowired
 *     private JwtUtil jwtUtil;
 *
 *     String token = jwtUtil.generateToken(user);
 *     Long userId = jwtUtil.extractUserId(token);
 * }</pre>
 */
@Component
public class JwtUtil {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtUtil(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Generate a JWT token for a given User entity.
     * Automatically determines the appropriate role and sellerId.
     *
     * @param user the User entity (must not be null)
     * @return a signed JWT token string
     * @throws IllegalArgumentException if user is null
     */
    public String generateToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        String roleName = user.getRole() != null ? user.getRole().name() : UserRole.BUYER.name();
        List<String> roles = List.of(roleName);

        Long sellerId = user.getRole() == UserRole.SELLER ? user.getId() : null;

        return jwtTokenProvider.createToken(user.getId(), roles, sellerId);
    }

    /**
     * Generate a JWT token with explicit role and sellerId.
     * Useful when you need to override the user's current role.
     *
     * @param userId   the user ID
     * @param role     the role name (e.g., "SELLER", "ADMIN", "BUYER")
     * @param sellerId the seller ID (can be null for non-seller roles)
     * @return a signed JWT token string
     */
    public String generateToken(Long userId, String role, Long sellerId) {
        List<String> roles = List.of(role);
        return jwtTokenProvider.createToken(userId, roles, sellerId);
    }

    /**
     * Validate a JWT token and extract the user ID.
     *
     * @param token the JWT token string
     * @return the user ID, or null if the token is invalid or expired
     */
    public Long extractUserId(String token) {
        return jwtTokenProvider.extractUserId(token);
    }

    /**
     * Validate a JWT token and extract the roles.
     *
     * @param token the JWT token string
     * @return list of role names, or empty list if token is invalid
     */
    public List<String> extractRoles(String token) {
        return jwtTokenProvider.extractRoles(token);
    }

    /**
     * Validate a JWT token and extract the seller ID.
     *
     * @param token the JWT token string
     * @return the seller ID, or null if not a seller or token is invalid
     */
    public Long extractSellerId(String token) {
        return jwtTokenProvider.extractSellerId(token);
    }

    /**
     * Check if a token is valid (not expired and properly signed).
     *
     * @param token the JWT token string
     * @return true if the token is valid, false otherwise
     */
    public boolean isValid(String token) {
        return jwtTokenProvider.extractUserId(token) != null;
    }
}
