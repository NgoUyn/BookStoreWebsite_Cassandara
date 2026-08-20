package com.example.bookstore.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO duy nhất cho toàn bộ hệ thống Chat.
 * Chứa các nested class để tránh tạo nhiều file.
 */
public class ChatDTO {

    // ==========================================
    // 1. REQUEST DTOs
    // ==========================================

    /**
     * Tạo mới chat room (hoặc trả về room cũ nếu đã tồn tại)
     */
    public static class CreateRoomRequest {
        public Long productId;
        public Long sellerId;
        public String productTitle;
        public String productImage;
        public Double productPrice;
    }

    /**
     * Gửi tin nhắn mới
     */
    public static class SendMessageRequest {
        public String content;
    }

    // ==========================================
    // 2. RESPONSE DTOs
    // ==========================================

    /**
     * Chat Room summary (dùng cho danh sách rooms)
     */
    public static class ChatRoomResponse {
        public String chatId;
        public Long buyerId;
        public String buyerName;
        public String buyerAvatar;
        public Long sellerId;
        public String sellerName;
        public String sellerAvatar;
        public Long productId;
        public String productTitle;
        public String productImage;
        public Double productPrice;
        public String lastMessage;
        public String lastMessageAt;
        public Long lastSenderId;
        public int unreadCount;
        public String createdAt;
    }

    /**
     * Tin nhắn
     */
    public static class MessageResponse {
        public String messageId;
        public Long senderId;
        public String senderName;
        public String content;
        public String createdAt;
        public String readAt;
    }

    /**
     * Danh sách tin nhắn (có pagination)
     */
    public static class MessageListResponse {
        public List<MessageResponse> messages;
        public String lastDocId;
        public boolean hasMore;
    }

    /**
     * Unread count response
     */
    public static class UnreadCountResponse {
        public int totalUnread;
        public Map<String, Integer> roomUnread;
    }

    /**
     * Generic response
     */
    public static class ApiResponse {
        public boolean success;
        public String message;
        public Object data;

        public static ApiResponse ok(String message) {
            ApiResponse r = new ApiResponse();
            r.success = true;
            r.message = message;
            return r;
        }

        public static ApiResponse ok(String message, Object data) {
            ApiResponse r = ok(message);
            r.data = data;
            return r;
        }

        public static ApiResponse error(String message) {
            ApiResponse r = new ApiResponse();
            r.success = false;
            r.message = message;
            return r;
        }
    }
}
