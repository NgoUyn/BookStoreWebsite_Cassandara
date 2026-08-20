package com.example.bookstore.dto;

import com.example.bookstore.model.enums.NotificationPriority;
import com.example.bookstore.model.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationItemResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private String payloadJson;
    private Boolean isRead;
    private NotificationPriority priority;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
