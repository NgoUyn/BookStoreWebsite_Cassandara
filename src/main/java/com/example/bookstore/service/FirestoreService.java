package com.example.bookstore.service;

import com.example.bookstore.dto.ChatDTO.*;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Service duy nhất để thao tác với Firebase Firestore cho hệ thống Chat.
 * KHÔNG lưu bất kỳ dữ liệu chat nào vào Database SQL.
 */
@Service
public class FirestoreService {

    private static final Logger log = LoggerFactory.getLogger(FirestoreService.class);
    private static final String CHATS_COLLECTION = "chats";
    private static final String MESSAGES_SUBCOLLECTION = "messages";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private Firestore getFirestore() {
        return FirestoreClient.getFirestore();
    }

    // ==========================================
    // 1. CHAT ROOM OPERATIONS
    // ==========================================

    /**
     * Tạo chat room mới hoặc trả về room cũ nếu đã tồn tại.
     * Kiểm tra dựa trên (buyerId, sellerId, productId).
     */
    public ApiResponse createOrGetRoom(Long buyerId, String buyerName, String buyerAvatar,
                                        Long sellerId, String sellerName, String sellerAvatar,
                                        Long productId, String productTitle, String productImage, Double productPrice) {
        try {
            // Kiểm tra room đã tồn tại chưa
            String existingChatId = findExistingRoom(buyerId, sellerId, productId);
            if (existingChatId != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("chatId", existingChatId);
                data.put("existing", true);
                return ApiResponse.ok("Room already exists", data);
            }

            // Tạo room mới
            String chatId = UUID.randomUUID().toString();
            long now = Instant.now().toEpochMilli();
            String nowStr = FORMATTER.format(Instant.ofEpochMilli(now));

            Map<String, Object> roomData = new HashMap<>();
            roomData.put("buyerId", buyerId);
            roomData.put("buyerName", buyerName != null ? buyerName : "Người mua");
            roomData.put("buyerAvatar", buyerAvatar != null ? buyerAvatar : "");
            roomData.put("sellerId", sellerId);
            roomData.put("sellerName", sellerName != null ? sellerName : "Người bán");
            roomData.put("sellerAvatar", sellerAvatar != null ? sellerAvatar : "");
            roomData.put("productId", productId);
            roomData.put("productTitle", productTitle != null ? productTitle : "");
            roomData.put("productImage", productImage != null ? productImage : "");
            roomData.put("productPrice", productPrice != null ? productPrice : 0.0);
            roomData.put("lastMessage", "");
            roomData.put("lastMessageAt", nowStr);
            roomData.put("lastSenderId", 0L);
            roomData.put("unreadBuyer", 0);
            roomData.put("unreadSeller", 0);
            roomData.put("createdAt", nowStr);

            getFirestore().collection(CHATS_COLLECTION).document(chatId).set(roomData).get();

            Map<String, Object> data = new HashMap<>();
            data.put("chatId", chatId);
            data.put("existing", false);
            return ApiResponse.ok("Room created", data);
        } catch (Exception e) {
            log.error("Error creating chat room: {}", e.getMessage(), e);
            return ApiResponse.error("Không thể tạo phòng chat: " + e.getMessage());
        }
    }

    /**
     * Tìm room đã tồn tại dựa trên buyerId, sellerId, productId
     */
    private String findExistingRoom(Long buyerId, Long sellerId, Long productId) throws Exception {
        Firestore db = getFirestore();
        CollectionReference chats = db.collection(CHATS_COLLECTION);

        // Query: buyerId == buyerId AND sellerId == sellerId AND productId == productId
        Query query = chats.whereEqualTo("buyerId", buyerId)
                .whereEqualTo("sellerId", sellerId)
                .whereEqualTo("productId", productId);

        QuerySnapshot snapshot = query.get().get();
        if (!snapshot.isEmpty()) {
            return snapshot.getDocuments().get(0).getId();
        }
        return null;
    }

    /**
     * Lấy danh sách chat rooms của user (buyer hoặc seller)
     */
    public List<ChatRoomResponse> getRooms(Long userId, String role) {
        List<ChatRoomResponse> result = new ArrayList<>();
        try {
            Firestore db = getFirestore();
            CollectionReference chats = db.collection(CHATS_COLLECTION);

            Query query;
            String fieldName = "seller".equalsIgnoreCase(role) ? "sellerId" : "buyerId";
            
            // Query với Long userId (Firebase sẽ so sánh đúng type)
            query = chats.whereEqualTo(fieldName, userId);

            // Sắp xếp theo lastMessageAt giảm dần
            // ⚠️ LƯU Ý: Query này yêu cầu composite index trên Firestore!
            // Nếu chưa tạo index, Firestore sẽ trả về lỗi và fallback sang query không orderBy
            try {
                query = query.orderBy("lastMessageAt", Query.Direction.DESCENDING);
                QuerySnapshot snapshot = query.get().get();
                log.info("📭 getRooms - Role: {}, UserId: {}, Field: {}, Found {} rooms (with orderBy)", 
                    role, userId, fieldName, snapshot.size());
                
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    ChatRoomResponse room = documentToRoomResponse(doc);
                    if (room != null) {
                        result.add(room);
                    }
                }
            } catch (Exception orderErr) {
                // Fallback: Nếu lỗi composite index, query không orderBy
                log.warn("⚠️ Composite index may be missing for query (field={}, orderBy=lastMessageAt). Error: {}", 
                    fieldName, orderErr.getMessage());
                log.warn("⚠️ Falling back to query without orderBy. Please create composite index in Firebase Console.");
                log.warn("⚠️ Required index: collection 'chats' with fields '{}' ASC and 'lastMessageAt' DESC", fieldName);
                
                // Query không orderBy
                Query fallbackQuery = chats.whereEqualTo(fieldName, userId);
                QuerySnapshot fallbackSnapshot = fallbackQuery.get().get();
                log.info("📭 getRooms (fallback) - Found {} rooms", fallbackSnapshot.size());
                
                for (DocumentSnapshot doc : fallbackSnapshot.getDocuments()) {
                    ChatRoomResponse room = documentToRoomResponse(doc);
                    if (room != null) {
                        result.add(room);
                    }
                }
                
                // Sắp xếp trong memory
                result.sort((a, b) -> {
                    if (a.lastMessageAt == null && b.lastMessageAt == null) return 0;
                    if (a.lastMessageAt == null) return 1;
                    if (b.lastMessageAt == null) return -1;
                    return b.lastMessageAt.compareTo(a.lastMessageAt);
                });
            }
            
            if (result.isEmpty()) {
                log.warn("⚠️ No rooms found for userId={}, role={}", userId, role);
            }
        } catch (Exception e) {
            log.error("❌ Error getting rooms for user {}, role {}: {}", userId, role, e.getMessage(), e);
        }
        return result;
    }

    /**
     * Lấy chi tiết 1 room
     */
    public ApiResponse getRoomDetail(String chatId) {
        try {
            DocumentSnapshot doc = getFirestore().collection(CHATS_COLLECTION).document(chatId).get().get();
            if (!doc.exists()) {
                return ApiResponse.error("Room not found");
            }
            ChatRoomResponse room = documentToRoomResponse(doc);
            return ApiResponse.ok("OK", room);
        } catch (Exception e) {
            log.error("Error getting room detail: {}", e.getMessage(), e);
            return ApiResponse.error("Không thể lấy thông tin phòng chat");
        }
    }

    // ==========================================
    // 2. MESSAGE OPERATIONS
    // ==========================================

    /**
     * Gửi tin nhắn mới
     */
    public ApiResponse sendMessage(String chatId, Long senderId, String senderName, String content) {
        try {
            // Kiểm tra room tồn tại
            DocumentReference roomRef = getFirestore().collection(CHATS_COLLECTION).document(chatId);
            DocumentSnapshot roomDoc = roomRef.get().get();
            if (!roomDoc.exists()) {
                return ApiResponse.error("Room not found");
            }

            // Tạo message document
            String messageId = roomRef.collection(MESSAGES_SUBCOLLECTION).document().getId();
            long now = Instant.now().toEpochMilli();
            String nowStr = FORMATTER.format(Instant.ofEpochMilli(now));

            Map<String, Object> messageData = new HashMap<>();
            messageData.put("senderId", senderId);
            messageData.put("senderName", senderName != null ? senderName : "");
            messageData.put("content", content);
            messageData.put("createdAt", nowStr);
            messageData.put("readAt", null);

            roomRef.collection(MESSAGES_SUBCOLLECTION).document(messageId).set(messageData).get();

            // Cập nhật lastMessage trong room document
            Map<String, Object> updates = new HashMap<>();
            updates.put("lastMessage", content);
            updates.put("lastMessageAt", nowStr);
            updates.put("lastSenderId", senderId);

            // Tăng unread count cho người nhận
            Long buyerId = roomDoc.getLong("buyerId");
            Long sellerId = roomDoc.getLong("sellerId");
            if (senderId.equals(buyerId)) {
                // Buyer gửi -> tăng unreadSeller
                Integer current = roomDoc.getLong("unreadSeller") != null ? roomDoc.getLong("unreadSeller").intValue() : 0;
                updates.put("unreadSeller", current + 1);
            } else {
                // Seller gửi -> tăng unreadBuyer
                Integer current = roomDoc.getLong("unreadBuyer") != null ? roomDoc.getLong("unreadBuyer").intValue() : 0;
                updates.put("unreadBuyer", current + 1);
            }

            roomRef.update(updates).get();

            MessageResponse msgResp = new MessageResponse();
            msgResp.messageId = messageId;
            msgResp.senderId = senderId;
            msgResp.senderName = senderName;
            msgResp.content = content;
            msgResp.createdAt = nowStr;
            msgResp.readAt = null;

            return ApiResponse.ok("Message sent", msgResp);
        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage(), e);
            return ApiResponse.error("Không thể gửi tin nhắn: " + e.getMessage());
        }
    }

    /**
     * Lấy lịch sử tin nhắn với pagination
     */
    public MessageListResponse getMessages(String chatId, int pageSize, String lastDocId) {
        MessageListResponse response = new MessageListResponse();
        response.messages = new ArrayList<>();
        response.hasMore = false;

        try {
            CollectionReference messagesRef = getFirestore()
                    .collection(CHATS_COLLECTION)
                    .document(chatId)
                    .collection(MESSAGES_SUBCOLLECTION);

            Query query = messagesRef.orderBy("createdAt", Query.Direction.DESCENDING).limit(pageSize + 1);

            // Nếu có lastDocId, bắt đầu từ document đó
            if (lastDocId != null && !lastDocId.isEmpty()) {
                DocumentSnapshot lastDoc = messagesRef.document(lastDocId).get().get();
                if (lastDoc.exists()) {
                    query = query.startAfter(lastDoc);
                }
            }

            QuerySnapshot snapshot = query.get().get();
            List<QueryDocumentSnapshot> documents = snapshot.getDocuments();

            // Kiểm tra nếu có nhiều hơn pageSize -> còn trang tiếp
            if (documents.size() > pageSize) {
                response.hasMore = true;
                documents = documents.subList(0, pageSize);
            }

            // Chuyển đổi và sắp xếp theo thứ tự tăng dần (cũ nhất trước)
            List<MessageResponse> messages = new ArrayList<>();
            for (QueryDocumentSnapshot doc : documents) {
                MessageResponse msg = documentToMessageResponse(doc);
                if (msg != null) {
                    messages.add(msg);
                }
            }
            Collections.reverse(messages); // Sắp xếp tăng dần theo thời gian

            response.messages = messages;
            if (!documents.isEmpty()) {
                response.lastDocId = documents.get(documents.size() - 1).getId();
            }
        } catch (Exception e) {
            log.error("Error getting messages: {}", e.getMessage(), e);
        }
        return response;
    }

    // ==========================================
    // 3. READ STATUS OPERATIONS
    // ==========================================

    /**
     * Đánh dấu đã đọc tất cả tin nhắn trong room
     */
    public ApiResponse markAsRead(String chatId, Long userId) {
        try {
            DocumentReference roomRef = getFirestore().collection(CHATS_COLLECTION).document(chatId);
            DocumentSnapshot roomDoc = roomRef.get().get();
            if (!roomDoc.exists()) {
                return ApiResponse.error("Room not found");
            }

            Long buyerId = roomDoc.getLong("buyerId");
            Map<String, Object> updates = new HashMap<>();

            if (userId.equals(buyerId)) {
                updates.put("unreadBuyer", 0);
            } else {
                updates.put("unreadSeller", 0);
            }

            roomRef.update(updates).get();
            return ApiResponse.ok("Marked as read");
        } catch (Exception e) {
            log.error("Error marking as read: {}", e.getMessage(), e);
            return ApiResponse.error("Không thể đánh dấu đã đọc");
        }
    }

    // ==========================================
    // 4. UNREAD COUNT OPERATIONS
    // ==========================================

    /**
     * Lấy số tin chưa đọc cho user
     */
    public UnreadCountResponse getUnreadCount(Long userId, String role) {
        UnreadCountResponse response = new UnreadCountResponse();
        response.totalUnread = 0;
        response.roomUnread = new HashMap<>();

        try {
            List<ChatRoomResponse> rooms = getRooms(userId, role);
            for (ChatRoomResponse room : rooms) {
                if (room.unreadCount > 0) {
                    response.totalUnread += room.unreadCount;
                    response.roomUnread.put(room.chatId, room.unreadCount);
                }
            }
        } catch (Exception e) {
            log.error("Error getting unread count: {}", e.getMessage(), e);
        }
        return response;
    }

    // ==========================================
    // 5. HELPER METHODS
    // ==========================================

    private ChatRoomResponse documentToRoomResponse(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;

        ChatRoomResponse room = new ChatRoomResponse();
        room.chatId = doc.getId();
        room.buyerId = getLong(doc, "buyerId");
        room.buyerName = getString(doc, "buyerName");
        room.buyerAvatar = getString(doc, "buyerAvatar");
        room.sellerId = getLong(doc, "sellerId");
        room.sellerName = getString(doc, "sellerName");
        room.sellerAvatar = getString(doc, "sellerAvatar");
        room.productId = getLong(doc, "productId");
        room.productTitle = getString(doc, "productTitle");
        room.productImage = getString(doc, "productImage");
        room.productPrice = getDouble(doc, "productPrice");
        room.lastMessage = getString(doc, "lastMessage");
        room.lastMessageAt = getString(doc, "lastMessageAt");
        room.lastSenderId = getLong(doc, "lastSenderId");
        room.createdAt = getString(doc, "createdAt");

        // Default unreadCount - sẽ override ở Controller dựa trên role
        room.unreadCount = 0;
        return room;
    }

    private MessageResponse documentToMessageResponse(QueryDocumentSnapshot doc) {
        if (doc == null) return null;

        MessageResponse msg = new MessageResponse();
        msg.messageId = doc.getId();
        msg.senderId = getLong(doc, "senderId");
        msg.senderName = getString(doc, "senderName");
        msg.content = getString(doc, "content");
        msg.createdAt = getString(doc, "createdAt");
        msg.readAt = getString(doc, "readAt");
        return msg;
    }

    private String getString(DocumentSnapshot doc, String field) {
        Object val = doc.get(field);
        return val != null ? val.toString() : "";
    }

    private Long getLong(DocumentSnapshot doc, String field) {
        Object val = doc.get(field);
        if (val instanceof Long) return (Long) val;
        if (val instanceof Number) return ((Number) val).longValue();
        return null;
    }

    private Double getDouble(DocumentSnapshot doc, String field) {
        Object val = doc.get(field);
        if (val instanceof Double) return (Double) val;
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0.0;
    }

    /**
     * Lấy unread count cho một role cụ thể từ Firebase
     */
    public Long getUnreadForRole(String chatId, String fieldName) {
        try {
            DocumentSnapshot doc = getFirestore()
                .collection(CHATS_COLLECTION)
                .document(chatId)
                .get().get();
            
            if (!doc.exists()) return 0L;
            
            Object val = doc.get(fieldName);
            if (val instanceof Number) {
                return ((Number) val).longValue();
            }
            return 0L;
        } catch (Exception e) {
            log.error("❌ Error getting unread count for {}, field {}: {}", chatId, fieldName, e.getMessage());
            return 0L;
        }
    }
}
