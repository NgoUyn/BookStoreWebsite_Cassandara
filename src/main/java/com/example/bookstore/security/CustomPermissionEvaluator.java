package com.example.bookstore.security;

import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.OrderRepository;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.repository.SubOrderRepository;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final BookRepository bookRepository;
    private final OrderRepository orderRepository;
    private final SubOrderRepository subOrderRepository;
    private final com.example.bookstore.repository.SellerShopRepository sellerShopRepository;

    public CustomPermissionEvaluator(BookRepository bookRepository,
                                     OrderRepository orderRepository,
                                     SubOrderRepository subOrderRepository,
                                     com.example.bookstore.repository.SellerShopRepository sellerShopRepository) {
        this.bookRepository = bookRepository;
        this.orderRepository = orderRepository;
        this.subOrderRepository = subOrderRepository;
        this.sellerShopRepository = sellerShopRepository;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false;
        }

        if (targetDomainObject instanceof com.example.bookstore.model.Book book) {
            return hasBookPermission(authentication, book.getId(), permission.toString());
        }

        if (targetDomainObject instanceof com.example.bookstore.model.Order order) {
            return hasOrderPermission(authentication, order.getId(), permission.toString());
        }

        if (targetDomainObject instanceof com.example.bookstore.model.SubOrder subOrder) {
            return hasSubOrderPermission(authentication, subOrder.getId(), permission.toString());
        }

        if (targetDomainObject instanceof User user) {
            return hasUserPermission(authentication, user.getId(), permission.toString());
        }

        if (targetDomainObject instanceof Long targetId) {
            return hasPermission(authentication, targetId, targetDomainObject.getClass().getSimpleName(), permission);
        }

        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || targetId == null || targetType == null || permission == null) {
            return false;
        }

        if (isAdmin(authentication)) {
            return true;
        }

        Long id = toLong(targetId);
        if (id == null) {
            return false;
        }

        String normalizedType = targetType.trim().toLowerCase(Locale.ROOT);
        String normalizedPermission = permission.toString().trim().toLowerCase(Locale.ROOT);

        return switch (normalizedType) {
            case "book", "product" -> hasBookPermission(authentication, id, normalizedPermission);
            case "order" -> hasOrderPermission(authentication, id, normalizedPermission);
            case "suborder", "sub_order" -> hasSubOrderPermission(authentication, id, normalizedPermission);
            case "user", "profile" -> hasUserPermission(authentication, id, normalizedPermission);
            default -> false;
        };
    }

    private boolean hasBookPermission(Authentication authentication, Long bookId, String permission) {
        if (bookId == null) {
            return false;
        }

        if (isAdmin(authentication)) {
            return true;
        }

        if (AuthenticationUtil.getCurrentUserId(authentication) == null) {
            return false;
        }

        if ("read".equals(permission) || "view".equals(permission)) {
            ApprovalStatus approvalStatus = bookRepository.findApprovalStatusById(bookId);
            if (approvalStatus == null) {
                return false;
            }

            Long sellerId = AuthenticationUtil.getCurrentSellerId(authentication);
            return approvalStatus == ApprovalStatus.APPROVED
                || (sellerId != null && bookRepository.existsByIdAndSellerId(bookId, sellerId));
        }

        if ("create".equals(permission)) {
            return AuthenticationUtil.hasRole(authentication, UserRole.SELLER);
        }

        if ("update".equals(permission) || "edit".equals(permission) || "delete".equals(permission)) {
            Long sellerId = AuthenticationUtil.getCurrentSellerId(authentication);
            return AuthenticationUtil.hasRole(authentication, UserRole.SELLER)
                && sellerId != null
                && bookRepository.existsByIdAndSellerId(bookId, sellerId);
        }

        return false;
    }

    private boolean hasOrderPermission(Authentication authentication, Long orderId, String permission) {
        if (orderId == null) {
            return false;
        }

        if (isAdmin(authentication)) {
            return true;
        }

        if (AuthenticationUtil.getCurrentUserId(authentication) == null) {
            return false;
        }

        if ("read".equals(permission) || "view".equals(permission)) {
            return orderRepository.existsByIdAndBuyerId(orderId, AuthenticationUtil.getCurrentUserId(authentication));
        }

        if ("update".equals(permission) || "edit".equals(permission) || "cancel".equals(permission)) {
            Long currentUserId = AuthenticationUtil.getCurrentUserId(authentication);
            return AuthenticationUtil.hasRole(authentication, UserRole.BUYER)
                && currentUserId != null
                && orderRepository.existsByIdAndBuyerId(orderId, currentUserId);
        }

        return false;
    }

    private boolean hasSubOrderPermission(Authentication authentication, Long subOrderId, String permission) {
        if (subOrderId == null) {
            return false;
        }

        if (isAdmin(authentication)) {
            return true;
        }

        if (AuthenticationUtil.getCurrentUserId(authentication) == null) {
            return false;
        }

        if ("read".equals(permission) || "view".equals(permission)) {
            Long currentUserId = AuthenticationUtil.getCurrentUserId(authentication);
            Long sellerId = AuthenticationUtil.getCurrentSellerId(authentication);
            return (currentUserId != null && subOrderRepository.existsByIdAndBuyerId(subOrderId, currentUserId))
                || (sellerId != null && subOrderRepository.existsByIdAndSellerId(subOrderId, sellerId));
        }

        if ("update".equals(permission) || "edit".equals(permission) || "status".equals(permission)) {
            Long sellerId = AuthenticationUtil.getCurrentSellerId(authentication);
            // Check 1: Is user a seller with valid sellerId?
            if (!AuthenticationUtil.hasRole(authentication, UserRole.SELLER) || sellerId == null) {
                return false;
            }
            // Check 2: Does the suborder belong to this seller?
            return subOrderRepository.existsByIdAndSellerId(subOrderId, sellerId);
        }

        return false;
    }

    private boolean hasUserPermission(Authentication authentication, Long userId, String permission) {
        if (userId == null) {
            return false;
        }

        if (isAdmin(authentication)) {
            return true;
        }

        Long currentUserId = AuthenticationUtil.getCurrentUserId(authentication);
        if (currentUserId == null) {
            return false;
        }

        if ("read".equals(permission) || "view".equals(permission) || "update".equals(permission) || "edit".equals(permission)) {
            return Objects.equals(currentUserId, userId);
        }

        return false;
    }

    private boolean isAdmin(Authentication authentication) {
        return AuthenticationUtil.hasRole(authentication, UserRole.ADMIN);
    }

    private Long toLong(Serializable targetId) {
        if (targetId instanceof Long value) {
            return value;
        }

        if (targetId instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.parseLong(targetId.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
