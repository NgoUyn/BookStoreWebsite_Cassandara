package com.example.bookstore.security;

import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.UserRole;
import org.springframework.security.core.Authentication;

/**
 * Utility class for consistent authentication extraction across the application.
 * This ensures sellerId and userId are handled uniformly whether using JWT or User entity.
 */
public class AuthenticationUtil {

    private AuthenticationUtil() {
        // Utility class - no instantiation
    }

    /**
     * Extract current user ID from authentication.
     * Tries User entity first, then falls back to JWT principal.
     *
     * @param authentication Spring Security authentication object
     * @return User ID, or null if not authenticated
     */
    public static Long getCurrentUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        // Try User entity first
        if (principal instanceof User user) {
            return user.getId();
        }

        // Fall back to JWT principal
        if (principal instanceof JwtAuthenticatedPrincipal jwtPrincipal) {
            return jwtPrincipal.userId();
        }

        return null;
    }

    /**
     * Extract current seller ID from authentication.
     * Logic:
     * 1. If JWT principal has explicit sellerId → use it
     * 2. If User entity has role SELLER → use userId as sellerId
     * 3. If JWT principal has role SELLER but no sellerId → use userId as fallback
     * 4. Otherwise → return null (user is not a seller)
     *
     * This handles the case where a user's role changes after token issuance.
     * The fallback to userId ensures sellers can still access their resources.
     *
     * @param authentication Spring Security authentication object
     * @return Seller ID if user is a seller, null otherwise
     */
    public static Long getCurrentSellerId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        // Try User entity first (most reliable, reflects current DB state)
        if (principal instanceof User user) {
            if (user.getRole() == UserRole.SELLER) {
                return user.getId();
            }
            return null;
        }

        // Fall back to JWT principal
        if (principal instanceof JwtAuthenticatedPrincipal jwtPrincipal) {
            // If JWT has explicit sellerId, use it (most cases)
            if (jwtPrincipal.sellerId() != null) {
                return jwtPrincipal.sellerId();
            }

            // If JWT has SELLER role but no sellerId, fallback to userId
            // This handles the edge case where role was updated after token issuance
            if (hasRole(jwtPrincipal, UserRole.SELLER)) {
                return jwtPrincipal.userId();
            }
        }

        return null;
    }

    /**
     * Extract current user ID from a principal object.
     * <p>
     * This overloaded method is useful when you only have access to the principal
     * object (e.g., from {@code @AuthenticationPrincipal}) rather than the full
     * {@link Authentication} object.
     * </p>
     *
     * @param principal the principal object (can be {@link User} or {@link JwtAuthenticatedPrincipal})
     * @return User ID, or null if not authenticated
     */
    public static Long getCurrentUserId(Object principal) {
        if (principal == null) {
            return null;
        }

        if (principal instanceof User user) {
            return user.getId();
        }

        if (principal instanceof JwtAuthenticatedPrincipal jwtPrincipal) {
            return jwtPrincipal.userId();
        }

        return null;
    }

    /**
     * Extract current seller ID from a principal object.
     * <p>
     * This overloaded method is useful when you only have access to the principal
     * object (e.g., from {@code @AuthenticationPrincipal}) rather than the full
     * {@link Authentication} object. It accepts any principal type including
     * {@link UserDetails} implementations, {@link User}, or {@link JwtAuthenticatedPrincipal}.
     * </p>
     * Logic:
     * <ul>
     *   <li>If principal is a {@link User} entity with role {@link UserRole#SELLER}
     *       → returns the user's ID as sellerId (Shared Primary Key pattern).</li>
     *   <li>If principal is a {@link JwtAuthenticatedPrincipal}:
     *       <ul>
     *         <li>If it has an explicit sellerId → returns it.</li>
     *         <li>If sellerId is null but the user has SELLER role → returns userId as fallback.</li>
     *       </ul>
     *   </li>
     *   <li>Otherwise → returns {@code null}.</li>
     * </ul>
     *
     * @param principal the principal object (can be {@link User}, {@link JwtAuthenticatedPrincipal},
     *                  or any {@link UserDetails} implementation)
     * @return Seller ID if the user is a seller, {@code null} otherwise
     */
    public static Long getCurrentSellerId(Object principal) {
        if (principal == null) {
            return null;
        }

        // Handle User entity (implements UserDetails)
        if (principal instanceof User user) {
            if (user.getRole() == UserRole.SELLER) {
                return user.getId();
            }
            return null;
        }

        // Handle JwtAuthenticatedPrincipal
        if (principal instanceof JwtAuthenticatedPrincipal jwtPrincipal) {
            // If JWT has explicit sellerId, use it (most common case)
            if (jwtPrincipal.sellerId() != null) {
                return jwtPrincipal.sellerId();
            }

            // If JWT has SELLER role but no sellerId, fallback to userId
            // This handles the edge case where role was updated after token issuance
            if (hasRole(jwtPrincipal, UserRole.SELLER)) {
                return jwtPrincipal.userId();
            }
        }

        return null;
    }

    /**
     * Check if authentication has a specific role.
     *
     * @param authentication Spring Security authentication object
     * @param role Role to check for
     * @return true if user has the role, false otherwise
     */
    public static boolean hasRole(Authentication authentication, UserRole role) {
        if (authentication == null || role == null) {
            return false;
        }

        Object principal = authentication.getPrincipal();

        // Check User entity
        if (principal instanceof User user) {
            return user.getRole() == role;
        }

        // Check JWT principal
        if (principal instanceof JwtAuthenticatedPrincipal jwtPrincipal) {
            return hasRole(jwtPrincipal, role);
        }

        return false;
    }

    /**
     * Check if JWT principal has a specific role.
     *
     * @param jwtPrincipal JWT authenticated principal
     * @param role Role to check for
     * @return true if principal has the role, false otherwise
     */
    public static boolean hasRole(JwtAuthenticatedPrincipal jwtPrincipal, UserRole role) {
        if (jwtPrincipal == null || role == null) {
            return false;
        }

        return jwtPrincipal.roles().stream()
            .anyMatch(r -> role.name().equalsIgnoreCase(r));
    }
}
