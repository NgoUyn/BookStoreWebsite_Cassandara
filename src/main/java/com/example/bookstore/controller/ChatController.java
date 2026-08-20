package com.example.bookstore.controller;

import com.example.bookstore.dto.ChatDTO.*;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.security.AuthenticationUtil;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.service.FirestoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller cho hệ thống Chat.
 * Tất cả dữ liệu được lưu trên Firebase Firestore, KHÔNG lưu vào DB SQL.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private FirestoreService firestoreService;

    @Autowired
    private UserRepository userRepository;

    // ==========================================
    // 1. CHAT ROOM APIs
    // ==========================================

    /**
     * Tạo chat room (hoặc trả về room cũ)
     * POST /api/chat/rooms
     */
    @PostMapping("/rooms")
    public ResponseEntity<?> createRoom(
            @RequestBody CreateRoomRequest request,
            Authentication authentication) {
        try {
            Long buyerId = AuthenticationUtil.getCurrentUserId(authentication);
            if (buyerId == null) {
                return ResponseEntity.status(401).body(ApiResponse.error("Vui lòng đăng nhập"));
            }

            // Lấy thông tin buyer
            User buyer = userRepository.findById(buyerId).orElse(null);
            String buyerName = buyer != null ? buyer.getUsername() : "Người mua";
            String buyerAvatar = buyer != null && buyer.getAvatarUrl() != null ? buyer.getAvatarUrl() : "";

            // Lấy thông tin seller
            User seller = userRepository.findById(request.sellerId).orElse(null);
            String sellerName = seller != null ? seller.getUsername() : "Người bán";
            String sellerAvatar = seller != null && seller.getAvatarUrl() != null ? seller.getAvatarUrl() : "";

            ApiResponse result = firestoreService.createOrGetRoom(
                    buyerId, buyerName, buyerAvatar,
                    request.sellerId, sellerName, sellerAvatar,
                    request.productId, request.productTitle, request.productImage, request.productPrice
            );

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error creating room: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Lỗi tạo phòng chat: " + e.getMessage()));
        }
    }

    /**
     * Danh sách rooms của user
     * GET /api/chat/rooms?role=buyer|seller
     */
    @GetMapping("/rooms")
    public ResponseEntity<?> getRooms(
            @RequestParam(defaultValue = "buyer") String role,
            Authentication authentication) {
        try {
            Long userId = AuthenticationUtil.getCurrentUserId(authentication);
            if (userId == null) {
                return ResponseEntity.status(401).body(ApiResponse.error("Vui lòng đăng nhập"));
            }

            List<ChatRoomResponse> rooms = firestoreService.getRooms(userId, role);

            // Tính unreadCount cho từng room dựa trên role - đọc từ Firebase
            for (ChatRoomResponse room : rooms) {
                if ("seller".equalsIgnoreCase(role)) {
                    // Seller: đọc unreadSeller field từ Firebase
                    room.unreadCount = (int) firestoreService.getUnreadForRole(room.chatId, "unreadSeller").longValue();
                } else {
                    // Buyer: đọc unreadBuyer field từ Firebase
                    room.unreadCount = (int) firestoreService.getUnreadForRole(room.chatId, "unreadBuyer").longValue();
                }
                log.info("📭 Room: {}, Unread for role {}: {}", room.chatId, role, room.unreadCount);
            }

            log.info("✅ Returned {} rooms for role: {}", rooms.size(), role);
            return ResponseEntity.ok(rooms);
        } catch (Exception e) {
            log.error("❌ Error getting rooms: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Lỗi lấy danh sách phòng chat"));
        }
    }

    /**
     * Chi tiết 1 room
     * GET /api/chat/rooms/{chatId}
     */
    @GetMapping("/rooms/{chatId}")
    public ResponseEntity<?> getRoomDetail(@PathVariable String chatId) {
        ApiResponse result = firestoreService.getRoomDetail(chatId);
        if (result.success) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }

    // ==========================================
    // 2. MESSAGE APIs
    // ==========================================

    /**
     * Lịch sử tin nhắn (pagination)
     * GET /api/chat/messages/{chatId}?pageSize=30&lastDocId=xxx
     */
    @GetMapping("/messages/{chatId}")
    public ResponseEntity<?> getMessages(
            @PathVariable String chatId,
            @RequestParam(defaultValue = "30") int pageSize,
            @RequestParam(required = false) String lastDocId) {
        try {
            MessageListResponse messages = firestoreService.getMessages(chatId, pageSize, lastDocId);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error getting messages: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Lỗi lấy tin nhắn"));
        }
    }

    /**
     * Gửi tin nhắn
     * POST /api/chat/messages/{chatId}
     */
    @PostMapping("/messages/{chatId}")
    public ResponseEntity<?> sendMessage(
            @PathVariable String chatId,
            @RequestBody SendMessageRequest request,
            Authentication authentication) {
        try {
            Long userId = AuthenticationUtil.getCurrentUserId(authentication);
            if (userId == null) {
                return ResponseEntity.status(401).body(ApiResponse.error("Vui lòng đăng nhập"));
            }

            User user = userRepository.findById(userId).orElse(null);
            String userName = user != null ? user.getUsername() : "User";

            if (request.content == null || request.content.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Nội dung tin nhắn không được để trống"));
            }

            ApiResponse result = firestoreService.sendMessage(chatId, userId, userName, request.content.trim());
            if (result.success) {
                return ResponseEntity.ok(result);
            }
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Lỗi gửi tin nhắn"));
        }
    }

    // ==========================================
    // 3. READ STATUS APIs
    // ==========================================

    /**
     * Đánh dấu đã đọc tất cả
     * PUT /api/chat/rooms/{chatId}/read
     */
    @PutMapping("/rooms/{chatId}/read")
    public ResponseEntity<?> markAsRead(
            @PathVariable String chatId,
            Authentication authentication) {
        try {
            Long userId = AuthenticationUtil.getCurrentUserId(authentication);
            if (userId == null) {
                return ResponseEntity.status(401).body(ApiResponse.error("Vui lòng đăng nhập"));
            }

            ApiResponse result = firestoreService.markAsRead(chatId, userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error marking as read: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Lỗi đánh dấu đã đọc"));
        }
    }

    // ==========================================
    // 4. UNREAD COUNT APIs
    // ==========================================

    /**
     * Số tin chưa đọc (cho badge)
     * GET /api/chat/unread-count?role=buyer|seller
     */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(
            @RequestParam(defaultValue = "buyer") String role,
            Authentication authentication) {
        try {
            Long userId = AuthenticationUtil.getCurrentUserId(authentication);
            if (userId == null) {
                return ResponseEntity.ok(new UnreadCountResponse());
            }

            UnreadCountResponse result = firestoreService.getUnreadCount(userId, role);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting unread count: {}", e.getMessage(), e);
            return ResponseEntity.ok(new UnreadCountResponse());
        }
    }
}
