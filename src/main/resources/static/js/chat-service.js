/**
 * 📱 Chat Service Module - Bookom Bookstore
 * Module gọi API chat + Firebase realtime listener
 * 
 * Usage:
 *   <script src="/js/chat-service.js"></script>
 *   <script src="/js/chat-ui.js"></script>
 */

const ChatService = (() => {
    const API_BASE = '/api/chat';

    // ==========================================
    // 1. UTILITY
    // ==========================================

    const getAuth = () => {
        const userIdRaw = localStorage.getItem('userId');
        // Ép kiểu userId thành Number để đồng nhất với backend Long
        const userId = userIdRaw ? Number(userIdRaw) : null;
        return {
            userId: userId,
            token: localStorage.getItem('accessToken'),
            role: localStorage.getItem('userRole')
        };
    };

    const getHeaders = () => {
        const { userId, token } = getAuth();
        const headers = {
            'Content-Type': 'application/json',
            'X-User-Id': userId !== null ? String(userId) : ''
        };
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }
        return headers;
    };

    const handleResponse = async (response) => {
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.message || 'Request failed');
        }
        return data;
    };

    // ==========================================
    // 2. CHAT ROOM APIs
    // ==========================================

    /**
     * Tạo hoặc lấy chat room
     */
    const createRoom = async (productId, sellerId, productTitle, productImage, productPrice) => {
        const response = await fetch(`${API_BASE}/rooms`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify({
                productId: Number(productId),
                sellerId: Number(sellerId),
                productTitle,
                productImage,
                productPrice: Number(productPrice)
            })
        });
        return handleResponse(response);
    };

    /**
     * Lấy danh sách rooms
     */
    const getRooms = async (role = 'buyer') => {
        const response = await fetch(`${API_BASE}/rooms?role=${role}`, {
            headers: getHeaders()
        });
        return handleResponse(response);
    };

    /**
     * Lấy chi tiết 1 room
     */
    const getRoomDetail = async (chatId) => {
        const response = await fetch(`${API_BASE}/rooms/${chatId}`, {
            headers: getHeaders()
        });
        return handleResponse(response);
    };

    // ==========================================
    // 3. MESSAGE APIs
    // ==========================================

    /**
     * Lấy lịch sử tin nhắn
     */
    const getMessages = async (chatId, pageSize = 30, lastDocId = null) => {
        let url = `${API_BASE}/messages/${chatId}?pageSize=${pageSize}`;
        if (lastDocId) {
            url += `&lastDocId=${lastDocId}`;
        }
        const response = await fetch(url, {
            headers: getHeaders()
        });
        return handleResponse(response);
    };

    /**
     * Gửi tin nhắn
     */
    const sendMessage = async (chatId, content) => {
        const response = await fetch(`${API_BASE}/messages/${chatId}`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify({ content })
        });
        return handleResponse(response);
    };

    // ==========================================
    // 4. READ STATUS APIs
    // ==========================================

    /**
     * Đánh dấu đã đọc
     */
    const markAsRead = async (chatId) => {
        const response = await fetch(`${API_BASE}/rooms/${chatId}/read`, {
            method: 'PUT',
            headers: getHeaders()
        });
        return handleResponse(response);
    };

    // ==========================================
    // 5. UNREAD COUNT APIs
    // ==========================================

    /**
     * Lấy số tin chưa đọc
     */
    const getUnreadCount = async (role = 'buyer') => {
        const response = await fetch(`${API_BASE}/unread-count?role=${role}`, {
            headers: getHeaders()
        });
        return handleResponse(response);
    };

    // ==========================================
    // 6. FIREBASE REALTIME LISTENER
    // ==========================================

    /**
     * Lắng nghe tin nhắn mới realtime từ Firebase
     * @param {string} chatId - ID của chat room
     * @param {function} onMessage - Callback khi có tin nhắn mới (nhận message object)
     * @param {function} onError - Callback khi có lỗi
     * @returns {function|null} - Hàm unsubscribe, hoặc null nếu Firebase chưa sẵn sàng
     */
    const listenMessages = (chatId, onMessage, onError) => {
        // Kiểm tra Firebase SDK đã load chưa
        if (typeof firebase === 'undefined' || !firebase.firestore) {
            console.warn('⚠️ Firebase SDK not loaded, falling back to polling');
            return null;
        }

        try {
            const db = firebase.firestore();
            const settings = { timestampsInSnapshots: true };
            db.settings(settings);

            const unsubscribe = db.collection('chats')
                .doc(chatId)
                .collection('messages')
                .orderBy('createdAt', 'asc')
                .onSnapshot((snapshot) => {
                    snapshot.docChanges().forEach((change) => {
                        if (change.type === 'added') {
                            const data = change.doc.data();
                            const msg = {
                                messageId: change.doc.id,
                                senderId: data.senderId,
                                senderName: data.senderName || '',
                                content: data.content || '',
                                createdAt: data.createdAt || '',
                                readAt: data.readAt || null
                            };
                            onMessage(msg);
                        }
                    });
                }, (error) => {
                    console.error('❌ Firebase listener error:', error);
                    if (onError) onError(error);
                });

            return unsubscribe;
        } catch (e) {
            console.warn('⚠️ Firebase not available:', e.message);
            return null;
        }
    };

    // ==========================================
    // 7. TIME FORMATTING
    // ==========================================

    /**
     * Format thời gian hiển thị
     */
    const formatTime = (isoString) => {
        if (!isoString) return '';
        const date = new Date(isoString);
        const now = new Date();
        const diffMs = now - date;
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);

        if (diffMins < 1) return 'Vừa xong';
        if (diffMins < 60) return `${diffMins} phút trước`;
        if (diffHours < 24) return `${diffHours} giờ trước`;
        if (diffDays < 7) return `${diffDays} ngày trước`;

        return date.toLocaleDateString('vi-VN', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric'
        });
    };

    // ==========================================
    // PUBLIC API
    // ==========================================

    return {
        getAuth,
        createRoom,
        getRooms,
        getRoomDetail,
        getMessages,
        sendMessage,
        markAsRead,
        getUnreadCount,
        listenMessages,
        formatTime
    };
})();

window.ChatService = ChatService;
