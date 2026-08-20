package com.example.bookstore.controller;

import com.example.bookstore.dto.NotificationItemResponse;
import com.example.bookstore.dto.NotificationListResponse;
import com.example.bookstore.dto.NotificationCreateRequest;
import com.example.bookstore.dto.UnreadCountResponse;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final com.example.bookstore.sse.NotificationSseService notificationSseService;

    @GetMapping("/me")
    public NotificationListResponse getMyNotifications(
        @AuthenticationPrincipal JwtAuthenticatedPrincipal principal,
        @RequestParam(required = false) Boolean isRead,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = resolveCurrentUserId(principal);
        return notificationService.getMyNotifications(userId, isRead, page, size);
    }

    @GetMapping("/me/unread-count")
    public UnreadCountResponse getUnreadCount(@AuthenticationPrincipal JwtAuthenticatedPrincipal principal) {
        Long userId = resolveCurrentUserId(principal);
        return new UnreadCountResponse(notificationService.getUnreadCount(userId));
    }

    @PatchMapping("/me/{notificationId}/read")
    public NotificationItemResponse markAsRead(
        @AuthenticationPrincipal JwtAuthenticatedPrincipal principal,
        @PathVariable Long notificationId
    ) {
        Long userId = resolveCurrentUserId(principal);
        return notificationService.markAsRead(userId, notificationId);
    }

    @PatchMapping("/me/read-all")
    public Map<String, Integer> markAllAsRead(@AuthenticationPrincipal JwtAuthenticatedPrincipal principal) {
        Long userId = resolveCurrentUserId(principal);

        // Trả về số lượng đã cập nhật để FE có thể đồng bộ badge ngay.
        int updatedCount = notificationService.markAllAsRead(userId);
        return Map.of("updatedCount", updatedCount);
    }

    // SSE endpoint for the current user to subscribe to real-time notifications
    @GetMapping("/me/subscribe")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribe(@AuthenticationPrincipal JwtAuthenticatedPrincipal principal) {
        Long userId = resolveCurrentUserId(principal);
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = notificationSseService.register(userId);
        // Send a handshake event so client knows subscription ready
        try {
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("subscribed").data(Map.of("userId", userId)));
        } catch (Exception ignored) {
        }
        return emitter;
    }

    // Admin/System endpoint to create notification (single user or broadcast)
    @PostMapping("/admin")
    @PreAuthorize("hasAuthority('ADMIN')")
    public NotificationItemResponse createByAdmin(@AuthenticationPrincipal JwtAuthenticatedPrincipal principal,
                                                                            @RequestBody NotificationCreateRequest req) {
        Long creatorUserId = resolveCurrentUserId(principal);
        return notificationService.createNotification(creatorUserId, req.getUserId(), req);
    }

    private Long resolveCurrentUserId(JwtAuthenticatedPrincipal principal) {
        if (principal != null) return principal.userId();
        throw new ResponseStatusException(UNAUTHORIZED, "User is not authenticated");
    }
}
