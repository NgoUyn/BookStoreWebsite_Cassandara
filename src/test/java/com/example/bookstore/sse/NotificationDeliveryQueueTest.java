package com.example.bookstore.sse;

import com.example.bookstore.model.Notification;
import com.example.bookstore.model.NotificationDelivery;
import com.example.bookstore.model.enums.NotificationPriority;
import com.example.bookstore.model.enums.NotificationType;
import com.example.bookstore.repository.NotificationDeliveryRepository;
import com.example.bookstore.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test cho {@link NotificationDeliveryQueue} - worker gui thong bao qua SSE.
 *
 * <p><b>Bug da tung xay ra:</b> worker push thang {@link NotificationDelivery#getNotification()}
 * (chi la "vo" co id + userId vi collection {@code notification_deliveries} chi luu
 * {@code notificationId}) nen event SSE co {@code type/title/message/isRead/createdAt = null};
 * frontend prepend vao danh sach -> hien dong thong bao trong, badge thi tang.</p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationDeliveryQueueTest {

    private static final Long NOTIFICATION_ID = 1001L;
    private static final Long SELLER_ID = 1510L;

    @Mock
    private NotificationDeliveryRepository deliveryRepo;

    @Mock
    private NotificationSseService sseService;

    @Mock
    private HeartbeatService heartbeatService;

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationDeliveryQueue queue;

    @BeforeEach
    void setUp() {
        queue = new NotificationDeliveryQueue(deliveryRepo, sseService, heartbeatService, notificationRepository);
    }

    private NotificationDelivery pendingTask() {
        return NotificationDelivery.builder()
            .id(9001L)
            .notificationId(NOTIFICATION_ID)
            .userId(SELLER_ID)
            .channel("SSE")
            .status(NotificationDelivery.DeliveryStatus.PENDING)
            .attemptCount(0)
            .nextRetryAt(LocalDateTime.now())
            .build();
    }

    private Notification fullNotification() {
        return Notification.builder()
            .id(NOTIFICATION_ID)
            .userId(SELLER_ID)
            .type(NotificationType.SHOP_APPROVAL_UPDATED)
            .title("Yeu cau mo cua hang da duoc duyet")
            .message("Cua hang cua ban da duoc admin duyet va kich hoat.")
            .payloadJson("{\"shopId\":1,\"status\":\"APPROVED\"}")
            .isRead(false)
            .priority(NotificationPriority.NORMAL)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    void processQueue_shouldPushFullNotificationContent() {
        NotificationDelivery task = pendingTask();

        when(heartbeatService.isQueueWorkerEnabled()).thenReturn(true);
        when(deliveryRepo.findPendingRetries(any(Pageable.class))).thenReturn(List.of(task));
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(fullNotification()));
        when(sseService.sendEventToUser(anyLong(), anyString(), any(Notification.class))).thenReturn(true);

        queue.processQueue();

        ArgumentCaptor<Notification> pushed = ArgumentCaptor.forClass(Notification.class);
        verify(sseService).sendEventToUser(eq(SELLER_ID), eq("notification"), pushed.capture());

        Notification payload = pushed.getValue();
        assertEquals(NOTIFICATION_ID, payload.getId());
        assertNotNull(payload.getTitle(), "payload SSE phai co title day du, khong duoc la 'vo' chi co id");
        assertEquals("Yeu cau mo cua hang da duoc duyet", payload.getTitle());
        assertEquals(NotificationType.SHOP_APPROVAL_UPDATED, payload.getType());
        assertEquals(Boolean.FALSE, payload.getIsRead());
        assertNotNull(payload.getCreatedAt());

        // Gui thanh cong -> delivery duoc danh dau SENT
        ArgumentCaptor<NotificationDelivery> saved = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepo).save(saved.capture());
        assertEquals(NotificationDelivery.DeliveryStatus.SENT, saved.getValue().getStatus());
        assertNotNull(saved.getValue().getSentAt());
    }

    @Test
    void processQueue_shouldFallBackToStubWhenNotificationExpired() {
        NotificationDelivery task = pendingTask();

        when(heartbeatService.isQueueWorkerEnabled()).thenReturn(true);
        when(deliveryRepo.findPendingRetries(any(Pageable.class))).thenReturn(List.of(task));
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.empty());
        when(sseService.sendEventToUser(anyLong(), anyString(), any(Notification.class))).thenReturn(true);

        queue.processQueue();

        ArgumentCaptor<Notification> pushed = ArgumentCaptor.forClass(Notification.class);
        verify(sseService).sendEventToUser(eq(SELLER_ID), eq("notification"), pushed.capture());
        assertEquals(NOTIFICATION_ID, pushed.getValue().getId());
    }

    @Test
    void processQueue_shouldDoNothingWhenInstanceIsNotTheWorker() {
        when(heartbeatService.isQueueWorkerEnabled()).thenReturn(false);

        queue.processQueue();

        verifyNoInteractions(deliveryRepo);
        verify(sseService, never()).sendEventToUser(anyLong(), anyString(), any());
    }

    @Test
    void enqueue_shouldCreatePendingDeliveryWithAuditTimestamp() {
        queue.enqueue(fullNotification(), "SSE");

        ArgumentCaptor<NotificationDelivery> saved = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepo).save(saved.capture());
        NotificationDelivery delivery = saved.getValue();

        assertEquals(NOTIFICATION_ID, delivery.getNotificationId());
        assertEquals(SELLER_ID, delivery.getUserId());
        assertEquals("SSE", delivery.getChannel());
        assertEquals(NotificationDelivery.DeliveryStatus.PENDING, delivery.getStatus());
        assertEquals(0, delivery.getAttemptCount().intValue());
        assertNotNull(delivery.getNextRetryAt(), "phai san sang gui ngay (nextRetryAt = now)");
        assertNotNull(delivery.getCreatedAt(), "createdAt tung bi null -> khong co vet audit lan gui");
    }
}
