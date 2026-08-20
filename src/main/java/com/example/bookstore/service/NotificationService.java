package com.example.bookstore.service;

import com.example.bookstore.dto.NotificationCreateRequest;
import com.example.bookstore.dto.NotificationItemResponse;
import com.example.bookstore.dto.NotificationListResponse;
import com.example.bookstore.model.Notification;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.NotificationPriority;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.NotificationRepository;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.sse.NotificationDeliveryQueue;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationDeliveryQueue deliveryQueue;

    public NotificationListResponse getMyNotifications(Long userId, Boolean isRead, int page, int size) {
        ensureUserExists(userId);

        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 100);

        Pageable pageable = PageRequest.of(
            normalizedPage,
            normalizedSize,
            Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Notification> resultPage;
        if (isRead == null) {
            resultPage = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        } else {
            resultPage = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, isRead, pageable);
        }

        return NotificationListResponse.builder()
            .items(resultPage.getContent().stream().map(this::toItemResponse).toList())
            .page(resultPage.getNumber())
            .size(resultPage.getSize())
            .totalItems(resultPage.getTotalElements())
            .totalPages(resultPage.getTotalPages())
            .hasNext(resultPage.hasNext())
            .build();
    }

    public long getUnreadCount(Long userId) {
        ensureUserExists(userId);
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationItemResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        // Ownership is already enforced by findByIdAndUserId; only update if needed.
        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }

        return toItemResponse(notification);
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        ensureUserExists(userId);
        return notificationRepository.markAllAsReadByUserId(userId, LocalDateTime.now());
    }

    /**
     * Create notification for single user or broadcast to all users
     * 
     * Flow:
     *   1. Validate request (type, title required)
     *   2. Create Notification entity in DB (persistent)
     *   3. Enqueue delivery task (fire-and-forget)
     *   4. Queue worker picks up immediately → attempts SSE delivery
     *   5. If delivery fails → exponential backoff retry (2s, 4s, 8s, ...)
     *   6. After 5 failed attempts → mark as DROPPED, log failure
     * 
     * Why persistent first, then async delivery?
     *   - Ensures notification is saved even if SSE fails
     *   - Client can fetch via GET /api/notifications/me (polling fallback)
     *   - Delivery is best-effort, not guaranteed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationItemResponse createNotification(Long creatorUserId, Long targetUserId, NotificationCreateRequest req) {
        validateCreateRequest(req);

        if (targetUserId != null) {
            // Single user notification
            User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found"));
            Notification single = saveNotification(target, req);
            // Enqueue for async delivery via SSE (DB-backed queue)
            deliveryQueue.enqueue(single, "SSE");
            return toItemResponse(single);
        }

        // Broadcast: fan-out per user because notifications.user_id is NOT NULL.
        // Why fan-out? Schema constraint requires user_id not null.
        // Alternative: separate broadcast_notifications table (more complex)
        // Fan-out approach is simpler + easier queries + better security model
        List<User> users = userRepository.findAll();
        NotificationItemResponse firstCreated = null;
        for (User user : users) {
            Notification n = saveNotification(user, req);
            // Each user gets own notification record
            deliveryQueue.enqueue(n, "SSE");
            if (firstCreated == null) {
                firstCreated = toItemResponse(n);
            }
        }

        if (firstCreated == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No users available for broadcast");
        }
        return firstCreated;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationItemResponse createNotificationForAdmins(Long creatorUserId, NotificationCreateRequest req) {
        validateCreateRequest(req);

        List<User> admins = userRepository.findAll().stream()
            .filter(user -> user.getRole() == UserRole.ADMIN)
            .toList();

        if (admins.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No admin users available for notification");
        }

        NotificationItemResponse firstCreated = null;
        for (User admin : admins) {
            Notification notification = saveNotification(admin, req);
            deliveryQueue.enqueue(notification, "SSE");
            if (firstCreated == null) {
                firstCreated = toItemResponse(notification);
            }
        }

        return firstCreated;
    }

    private Notification saveNotification(User user, NotificationCreateRequest req) {
        Notification n = Notification.builder()
            .user(user)
            .type(req.getType())
            .title(req.getTitle())
            .message(req.getMessage())
            .payloadJson(req.getPayloadJson())
            .priority(req.getPriority() == null ? NotificationPriority.NORMAL : req.getPriority())
            .build();

        return notificationRepository.save(n);
    }

    private void validateCreateRequest(NotificationCreateRequest req) {
        if (req == null || req.getType() == null || req.getTitle() == null || req.getTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notification request");
        }
    }

    private NotificationItemResponse toItemResponse(Notification n) {
        return NotificationItemResponse.builder()
            .id(n.getId())
            .type(n.getType())
            .title(n.getTitle())
            .message(n.getMessage())
            .payloadJson(n.getPayloadJson())
            .isRead(n.getIsRead())
            .priority(n.getPriority())
            .createdAt(n.getCreatedAt())
            .readAt(n.getReadAt())
            .build();
    }

    private void ensureUserExists(Long userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
    }
}
