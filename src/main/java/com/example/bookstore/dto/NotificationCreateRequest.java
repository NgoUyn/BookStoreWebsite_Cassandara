package com.example.bookstore.dto;

import com.example.bookstore.model.enums.NotificationPriority;
import com.example.bookstore.model.enums.NotificationType;
import lombok.Data;

@Data
public class NotificationCreateRequest {
    private Long userId; // null = broadcast to all
    private NotificationType type;
    private String title;
    private String message;
    private String payloadJson;
    private NotificationPriority priority;
}
