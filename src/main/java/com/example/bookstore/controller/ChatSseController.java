package com.example.bookstore.controller;

import com.example.bookstore.dto.ChatDTO.MessageResponse;
import com.example.bookstore.security.AuthenticationUtil;
import com.example.bookstore.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE Controller cho hệ thống Chat Realtime.
 * Dùng Server-Sent Events để push tin nhắn mới đến client gần như tức thời (< 200ms).
 * 
 * Cách hoạt động:
 * 1. Client mở kết nối SSE đến /api/chat/sse/stream?chatId=xxx
 * 2. Server giữ kết nối HTTP persistent
 * 3. Khi có tin nhắn mới, server push ngay qua kết nối này
 * 4. Client nhận và render tin nhắn
 */
@RestController
@RequestMapping("/api/chat/sse")
public class ChatSseController {

    private static final Logger log = LoggerFactory.getLogger(ChatSseController.class);

    // Map<chatId, List<SseEmitter>> - lưu các emitter theo từng chat room
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> chatEmitters = new ConcurrentHashMap<>();

    // Timeout cho SSE connection: 30 phút
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * Client mở kết nối SSE để nhận tin nhắn realtime
     * GET /api/chat/sse/stream?chatId=xxx&token=xxx
     * 
     * Dùng token query param vì EventSource (browser API) không hỗ trợ custom headers.
     * Nếu Authentication object có sẵn từ Spring Security filter thì dùng,
     * nếu không thì fallback dùng token từ query param.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam String chatId,
            @RequestParam(required = false) String token,
            Authentication authentication) {
        
        Long userId = resolveUserId(authentication, token);
        if (userId == null) {
            SseEmitter errorEmitter = new SseEmitter(0L);
            try {
                errorEmitter.send(SseEmitter.event()
                    .name("error")
                    .data("Unauthorized"));
                errorEmitter.complete();
            } catch (IOException e) {
                // ignore
            }
            return errorEmitter;
        }

        log.info("🔌 SSE connected - chatId: {}, userId: {}", chatId, userId);

        // Tạo emitter với timeout dài
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // Đăng ký emitter cho chat room này
        chatEmitters.computeIfAbsent(chatId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Gửi event "connected" để client biết đã kết nối thành công
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("{\"chatId\":\"" + chatId + "\",\"userId\":" + userId + "}"));
        } catch (IOException e) {
            log.warn("⚠️ Failed to send connected event for chatId: {}", chatId);
            emitter.completeWithError(e);
        }

        // Callback khi hoàn thành hoặc timeout
        emitter.onCompletion(() -> {
            log.info("🔌 SSE completed - chatId: {}, userId: {}", chatId, userId);
            removeEmitter(chatId, emitter);
        });

        emitter.onTimeout(() -> {
            log.info("🔌 SSE timeout - chatId: {}, userId: {}", chatId, userId);
            removeEmitter(chatId, emitter);
        });

        emitter.onError(e -> {
            log.warn("🔌 SSE error - chatId: {}, userId: {}, error: {}", chatId, userId, e.getMessage());
            removeEmitter(chatId, emitter);
        });

        return emitter;
    }

    /**
     * Resolve userId từ Authentication object hoặc token query param.
     * EventSource không hỗ trợ custom headers, nên cần fallback qua query param.
     */
    private Long resolveUserId(Authentication authentication, String token) {
        // Ưu tiên từ Authentication object (nếu Spring Security filter đã xử lý)
        if (authentication != null) {
            Long userId = AuthenticationUtil.getCurrentUserId(authentication);
            if (userId != null) {
                return userId;
            }
        }

        // Fallback: dùng token từ query param
        if (token != null && !token.isBlank()) {
            try {
                Long userId = jwtTokenProvider.extractUserId(token);
                if (userId != null) {
                    log.info("🔑 SSE authenticated via token param: userId={}", userId);
                    return userId;
                }
            } catch (Exception e) {
                log.warn("⚠️ Invalid SSE token: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * Push tin nhắn mới đến tất cả client đang kết nối trong chat room
     * Được gọi từ ChatController.sendMessage()
     */
    public void pushNewMessage(String chatId, MessageResponse message) {
        CopyOnWriteArrayList<SseEmitter> emitters = chatEmitters.get(chatId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        log.info("📤 Pushing message to {} clients in chatId: {}", emitters.size(), chatId);

        // Chuyển message thành JSON string
        String messageJson = String.format(
            "{\"messageId\":\"%s\",\"senderId\":%d,\"senderName\":\"%s\",\"content\":\"%s\",\"createdAt\":\"%s\",\"readAt\":%s}",
            escapeJson(message.messageId),
            message.senderId,
            escapeJson(message.senderName != null ? message.senderName : ""),
            escapeJson(message.content != null ? message.content : ""),
            escapeJson(message.createdAt != null ? message.createdAt : ""),
            message.readAt != null ? "\"" + escapeJson(message.readAt) + "\"" : "null"
        );

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("new_message")
                    .data(messageJson));
            } catch (IOException e) {
                log.warn("⚠️ Failed to push message to emitter, removing: {}", e.getMessage());
                removeEmitter(chatId, emitter);
            }
        }
    }

    /**
     * Push sự kiện cập nhật unread count
     */
    public void pushUnreadUpdate(String chatId, Long userId, int unreadCount) {
        CopyOnWriteArrayList<SseEmitter> emitters = chatEmitters.get(chatId);
        if (emitters == null || emitters.isEmpty()) return;

        String data = String.format(
            "{\"chatId\":\"%s\",\"userId\":%d,\"unreadCount\":%d}",
            chatId, userId, unreadCount
        );

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("unread_update")
                    .data(data));
            } catch (IOException e) {
                removeEmitter(chatId, emitter);
            }
        }
    }

    /**
     * Xóa emitter khỏi danh sách khi kết nối đóng
     */
    private void removeEmitter(String chatId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = chatEmitters.get(chatId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                chatEmitters.remove(chatId);
            }
        }
    }

    /**
     * Escape JSON string để tránh lỗi khi content chứa ký tự đặc biệt
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\f", "\\f");
    }
}
