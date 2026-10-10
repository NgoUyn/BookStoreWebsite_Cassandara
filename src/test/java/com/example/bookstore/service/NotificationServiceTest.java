package com.example.bookstore.service;

import com.example.bookstore.dto.NotificationCreateRequest;
import com.example.bookstore.dto.NotificationItemResponse;
import com.example.bookstore.model.Notification;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.NotificationPriority;
import com.example.bookstore.model.enums.NotificationType;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.NotificationRepository;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.sse.NotificationDeliveryQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test cho {@link NotificationService}.
 *
 * <p><b>Bug da tung xay ra:</b> collection {@code notifications} co validator
 * {@code $jsonSchema} bat buoc {@code userId, type, title, isRead, createdAt}
 * (xem {@code db/mongo/01_create_collections_validators.js}). {@code saveNotification()}
 * khong set {@code isRead}/{@code createdAt} nen moi lan tao thong bao Mongo tra
 * {@code WriteError code=121 'Document failed validation'} -> admin duyet shop thi
 * seller KHONG bao gio nhan duoc thong bao (endpoint van tra HTTP 200 voi
 * {@code notificationId=null}). Cac test duoi day khoa lai hanh vi do.</p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long SELLER_ID = 1510L;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationDeliveryQueue deliveryQueue;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, userRepository, deliveryQueue);
    }

    private User seller() {
        return User.builder()
            .id(SELLER_ID)
            .username("nhaxuatbantre@bookom.vn")
            .passwordHash("x")
            .role(UserRole.SELLER)
            .build();
    }

    private NotificationCreateRequest approveRequest() {
        NotificationCreateRequest req = new NotificationCreateRequest();
        req.setType(NotificationType.SHOP_APPROVAL_UPDATED);
        req.setTitle("Yeu cau mo cua hang da duoc duyet");
        req.setMessage("Cua hang cua ban da duoc admin duyet va kich hoat.");
        req.setPayloadJson("{\"shopId\":1,\"status\":\"APPROVED\"}");
        return req;
    }

    @Test
    void createNotification_shouldFillFieldsRequiredByMongoValidator() {
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller()));
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> (Notification) invocation.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        NotificationItemResponse response = notificationService.createNotification(null, SELLER_ID, approveRequest());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();

        // 2 field tung lam insert that bai (validator $jsonSchema bat buoc)
        assertEquals(Boolean.FALSE, saved.getIsRead());
        assertNotNull(saved.getCreatedAt(), "createdAt phai duoc set tay (Notification khong phai AuditableDocument)");

        // expiresAt de TTL index don thong bao cu sau 90 ngay
        assertNotNull(saved.getExpiresAt());
        assertEquals(saved.getCreatedAt().plusDays(90), saved.getExpiresAt());

        assertEquals(SELLER_ID, saved.getUserId());
        assertEquals(NotificationType.SHOP_APPROVAL_UPDATED, saved.getType());
        assertEquals("Yeu cau mo cua hang da duoc duyet", saved.getTitle());
        assertEquals(NotificationPriority.NORMAL, saved.getPriority());
        assertFalse(saved.getCreatedAt().isBefore(before), "createdAt phai la thoi diem tao");

        assertNotNull(response);
        assertEquals(Boolean.FALSE, response.getIsRead());

        // Van phai enqueue de worker gui realtime qua SSE
        verify(deliveryQueue).enqueue(any(Notification.class), eq("SSE"));
    }

    @Test
    void createNotification_shouldReturn404WhenTargetUserNotFound() {
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> notificationService.createNotification(null, SELLER_ID, approveRequest()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(deliveryQueue, never()).enqueue(any(Notification.class), any());
    }

    @Test
    void createNotification_shouldReturn400WhenTitleMissing() {
        NotificationCreateRequest req = approveRequest();
        req.setTitle("   ");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> notificationService.createNotification(null, SELLER_ID, req));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void createNotification_shouldFanOutToEveryUserWhenTargetIsNull() {
        when(userRepository.findAll()).thenReturn(List.of(seller()));
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> (Notification) invocation.getArgument(0));

        notificationService.createNotification(null, null, approveRequest());

        verify(notificationRepository).save(any(Notification.class));
        verify(deliveryQueue).enqueue(any(Notification.class), eq("SSE"));
    }
}
