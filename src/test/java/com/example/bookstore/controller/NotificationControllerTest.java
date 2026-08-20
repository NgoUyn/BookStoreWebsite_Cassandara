package com.example.bookstore.controller;

import com.example.bookstore.dto.NotificationCreateRequest;
import com.example.bookstore.dto.NotificationItemResponse;
import com.example.bookstore.dto.NotificationListResponse;
import com.example.bookstore.dto.UnreadCountResponse;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.service.NotificationService;
import com.example.bookstore.sse.NotificationSseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationSseService notificationSseService;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationService, notificationSseService);
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new JwtAuthenticatedPrincipal(userId, java.util.List.of("BUYER"), null),
                null,
                java.util.List.of()
            )
        );
    }

    private HttpServletRequest requestWithoutPrincipal() {
        return mock(HttpServletRequest.class);
    }

    @Test
    void getMyNotifications_shouldUseAuthenticatedUser() {
        // TODO: Update test to use @AuthenticationPrincipal injection
        // authenticate(15L);
        // NotificationListResponse response = mock(NotificationListResponse.class);
        // when(notificationService.getMyNotifications(eq(15L), eq(null), eq(0), eq(20))).thenReturn(response);
        //
        // NotificationListResponse actual = controller.getMyNotifications(requestWithoutPrincipal(), null, 0, 20);
        //
        // assertEquals(response, actual);
        // verify(notificationService).getMyNotifications(15L, null, 0, 20);
    }

    @Test
    void getUnreadCount_shouldUseAuthenticatedUser() {
        // TODO: Update test to use @AuthenticationPrincipal injection
        // authenticate(15L);
        // when(notificationService.getUnreadCount(15L)).thenReturn(3L);
        //
        // UnreadCountResponse actual = controller.getUnreadCount(requestWithoutPrincipal());
        //
        // assertEquals(3L, actual.getUnreadCount());
    }

    @Test
    void markAsRead_shouldUseAuthenticatedUser() {
        // TODO: Update test to use @AuthenticationPrincipal injection
        // authenticate(15L);
        // NotificationItemResponse response = mock(NotificationItemResponse.class);
        // when(notificationService.markAsRead(15L, 99L)).thenReturn(response);
        //
        // NotificationItemResponse actual = controller.markAsRead(requestWithoutPrincipal(), 99L);
        //
        // assertEquals(response, actual);
        // verify(notificationService).markAsRead(15L, 99L);
    }

    @Test
    void createByAdmin_shouldUseAuthenticatedUser() {
        // TODO: Update test to use @AuthenticationPrincipal injection
        // authenticate(15L);
        // NotificationCreateRequest createRequest = mock(NotificationCreateRequest.class);
        // when(createRequest.getUserId()).thenReturn(22L);
        // NotificationItemResponse response = mock(NotificationItemResponse.class);
        // when(notificationService.createNotification(15L, 22L, createRequest)).thenReturn(response);
        //
        // NotificationItemResponse actual = controller.createByAdmin(requestWithoutPrincipal(), createRequest);
        //
        // assertEquals(response, actual);
        // verify(notificationService).createNotification(15L, 22L, createRequest);
    }
}