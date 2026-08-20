/**
 * 🎨 Chat UI Module - Bookom Bookstore
 * Component chat popup (buyer) + chat page (seller)
 * * Dependencies: chat-service.js, api-service.js
 */

const ChatUI = (() => {
    // ==========================================
    // STATE
    // ==========================================
    let activeChatId = null;
    let currentRoom = null;
    let messagePollingInterval = null;
    let unreadPollingInterval = null;
    let firebaseUnsubscribe = null; // Firebase realtime listener
    let sellerLastDocId = null;
    let sellerIsLoadingMore = false;
    let sellerHasMoreMessages = true;
    let sellerScrollPosition = 0;
    // Cache để tránh render lại tin nhắn đã có
    let renderedMessageIds = new Set();

    // ==========================================
    // BUYER CHAT POPUP
    // ==========================================

    /**
     * Khởi tạo chat popup cho buyer
     * Gọi khi trang Details_Produce load
     * CHỈ khởi tạo 1 lần duy nhất (kiểm tra popup đã tồn tại chưa)
     */
    const initBuyerPopup = () => {
        // Kiểm tra popup đã tồn tại chưa để tránh duplicate
        if (document.getElementById('buyer-chat-popup')) {
            console.log('[Chat Debug] Popup already exists, skipping init');
            return;
        }

        // Inject CSS
        injectBuyerPopupStyles();

        // Tạo popup HTML (Đã có sẵn input và cấu trúc scroll)
        const popupHTML = `
            <div id="buyer-chat-popup" class="fixed bottom-0 right-6 z-[9999] hidden">
                <button id="chat-toggle-btn" class="bg-brand-orange text-white rounded-t-xl px-5 py-3 shadow-lg hover:bg-orange-700 transition flex items-center gap-2 font-bold text-sm">
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"/>
                    </svg>
                    <span>Chat với người bán</span>
                    <span id="chat-unread-badge" class="bg-red-500 text-white text-[10px] rounded-full h-5 w-5 flex items-center justify-center font-bold hidden">0</span>
                </button>

                <div id="chat-window" class="hidden bg-white border border-gray-200 rounded-t-xl shadow-2xl w-[380px] h-[500px] flex flex-col">
                    <div id="chat-header" class="bg-brand-orange text-white px-4 py-3 rounded-t-xl flex items-center justify-between">
                        <div class="flex items-center gap-2">
                            <div class="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center text-xs font-bold" id="chat-header-avatar">NN</div>
                            <div>
                                <div class="font-bold text-sm" id="chat-header-name">Người bán</div>
                                <div class="text-[10px] text-white/80" id="chat-header-status">Đang hoạt động</div>
                            </div>
                        </div>
                        <button id="chat-close-btn" class="text-white/80 hover:text-white transition">
                            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                            </svg>
                        </button>
                    </div>

                    <div id="chat-product-card" class="hidden bg-gray-50 border-b border-gray-200 p-3 flex items-center gap-3">
                        <img id="chat-product-img" class="w-12 h-16 object-cover rounded border" src="" alt="">
                        <div class="flex-1 min-w-0">
                            <div class="text-sm font-bold text-gray-800 truncate" id="chat-product-title">Tên sản phẩm</div>
                            <div class="text-brand-orange font-bold text-sm" id="chat-product-price">0đ</div>
                        </div>
                        <a id="chat-product-link" href="#" class="text-xs text-blue-600 hover:underline flex-shrink-0">Xem</a>
                    </div>

                    <div id="chat-messages" class="flex-1 overflow-y-auto p-4 space-y-3 bg-gray-50 scroll-smooth">
                        <div class="text-center text-gray-400 text-sm py-10" id="chat-loading-msg">Đang tải tin nhắn...</div>
                    </div>

                    <div class="border-t border-gray-200 p-3 bg-white">
                        <form id="chat-input-form" class="flex gap-2">
                            <input id="chat-input" type="text" class="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm outline-none focus:border-brand-orange" placeholder="Nhập tin nhắn..." autocomplete="off">
                            <button type="submit" class="bg-brand-orange text-white px-4 py-2 rounded-lg hover:bg-orange-700 transition text-sm font-bold flex items-center gap-1 flex-shrink-0">
                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"/>
                                </svg>
                                Gửi
                            </button>
                        </form>
                    </div>
                </div>
            </div>

            <div id="chat-sidebar" class="fixed right-6 bottom-16 w-[350px] bg-white border border-gray-200 rounded-xl shadow-2xl hidden z-[9998]">
                <div class="bg-brand-orange text-white px-4 py-3 rounded-t-xl flex items-center justify-between">
                    <div class="font-bold text-sm">💬 Tin nhắn của tôi</div>
                    <button id="sidebar-close-btn" class="text-white/80 hover:text-white transition">
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                        </svg>
                    </button>
                </div>
                <div id="sidebar-room-list" class="max-h-[400px] overflow-y-auto">
                    <div class="text-center text-gray-400 text-sm py-10">Đang tải...</div>
                </div>
            </div>
        `;

        // Append to body
        const container = document.createElement('div');
        container.innerHTML = popupHTML;
        document.body.appendChild(container.firstElementChild);

        // Bind events
        bindBuyerPopupEvents();
        
        console.log('[Chat Debug] Buyer popup initialized successfully');
    };

    const injectBuyerPopupStyles = () => {
        const style = document.createElement('style');
        style.textContent = `
            #buyer-chat-popup { z-index: 9999; }
            #chat-window { z-index: 9999; }
            #chat-sidebar { z-index: 9998; }
            .chat-bubble { max-width: 80%; word-wrap: break-word; }
            .chat-bubble-seller { background: #ea580c; color: white; border-radius: 16px 16px 4px 16px; }
            .chat-bubble-buyer { background: #e5e7eb; color: #1f2937; border-radius: 16px 16px 16px 4px; }
            #chat-messages::-webkit-scrollbar { width: 6px; }
            #chat-messages::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
            #chat-messages::-webkit-scrollbar-track { background: #f1f5f9; }
            #sidebar-room-list::-webkit-scrollbar { width: 4px; }
            #sidebar-room-list::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 4px; }
        `;
        document.head.appendChild(style);
    };

    const bindBuyerPopupEvents = () => {
        const toggleBtn = document.getElementById('chat-toggle-btn');
        const chatWindow = document.getElementById('chat-window');
        const closeBtn = document.getElementById('chat-close-btn');
        const sidebar = document.getElementById('chat-sidebar');
        const sidebarCloseBtn = document.getElementById('sidebar-close-btn');
        const inputForm = document.getElementById('chat-input-form');

        // Toggle chat window
        toggleBtn?.addEventListener('click', () => {
            if (chatWindow.classList.contains('hidden')) {
                chatWindow.classList.remove('hidden');
                sidebar?.classList.add('hidden');
                if (activeChatId) {
                    loadMessages(activeChatId);
                    ChatService.markAsRead(activeChatId).catch(() => {});
                }
            } else {
                chatWindow.classList.add('hidden');
            }
        });

        // Close chat window
        closeBtn?.addEventListener('click', () => {
            chatWindow.classList.add('hidden');
        });

        // Sidebar close
        sidebarCloseBtn?.addEventListener('click', () => {
            sidebar?.classList.add('hidden');
        });

        // Send message - FIX: loadMessages ngay sau khi gửi thành công
        inputForm?.addEventListener('submit', async (e) => {
            e.preventDefault();
            const input = document.getElementById('chat-input');
            const content = input.value.trim();
            if (!content || !activeChatId) return;

            input.value = '';
            try {
                await ChatService.sendMessage(activeChatId, content);
                // FIX: Load lại tin nhắn ngay lập tức để hiển thị tin vừa gửi
                await loadMessages(activeChatId);
                // Tự động cuộn xuống sau khi gửi tin
                const container = document.getElementById('chat-messages');
                if (container) {
                    setTimeout(() => { container.scrollTop = container.scrollHeight; }, 50);
                }
            } catch (err) {
                console.error('Send message error:', err);
                alert('Không thể gửi tin nhắn');
            }
        });
    };

    /**
     * Mở popup chat với 1 room cụ thể
     */
    const openBuyerChat = async (chatId) => {
        activeChatId = chatId;
        // Reset cache khi mở room mới
        renderedMessageIds = new Set();
        const chatWindow = document.getElementById('chat-window');
        const popup = document.getElementById('buyer-chat-popup');

        if (popup) popup.classList.remove('hidden');
        if (chatWindow) chatWindow.classList.remove('hidden');

        // Load room detail
        try {
            const result = await ChatService.getRoomDetail(chatId);
            if (result.success) {
                currentRoom = result.data;
                updateChatHeader(currentRoom);
                updateProductCard(currentRoom);
            }
        } catch (err) {
            console.error('Error loading room:', err);
        }

        // Load messages
        await loadMessages(chatId);
        ChatService.markAsRead(chatId).catch(() => {});

        // Thử Firebase realtime trước, fallback polling
        stopMessagePolling();
        stopFirebaseListener();
        const unsub = ChatService.listenMessages(chatId, (msg) => {
            // Chỉ append tin nhắn mới nếu chưa có trong cache
            if (!renderedMessageIds.has(msg.messageId)) {
                appendSingleMessage(msg);
                renderedMessageIds.add(msg.messageId);
            }
        }, () => {
            // Fallback to polling nếu Firebase lỗi
            startMessagePolling(chatId);
        });
        if (unsub) {
            firebaseUnsubscribe = unsub;
        } else {
            startMessagePolling(chatId);
        }
    };

    /**
     * Mở sidebar danh sách chat
     */
    const openBuyerSidebar = async () => {
        const sidebar = document.getElementById('chat-sidebar');
        const popup = document.getElementById('buyer-chat-popup');
        const chatWindow = document.getElementById('chat-window');

        if (popup) popup.classList.remove('hidden');
        if (chatWindow) chatWindow.classList.add('hidden');
        if (sidebar) {
            sidebar.classList.remove('hidden');
            await loadRoomList('buyer');
        }
    };

    const loadRoomList = async (role) => {
        const containerId = role === 'seller' ? 'seller-room-list' : 'sidebar-room-list';
        const container = document.getElementById(containerId);
        if (!container) return;

        container.innerHTML = '<div class="text-center text-gray-400 text-sm py-10">Đang tải...</div>';

        try {
            const rooms = await ChatService.getRooms(role);
            if (!rooms || rooms.length === 0) {
                container.innerHTML = '<div class="text-center text-gray-400 text-sm py-10">Chưa có tin nhắn nào</div>';
                return;
            }

            container.innerHTML = '';
            rooms.forEach(room => {
                const isSeller = role === 'seller';
                const name = isSeller ? room.buyerName : room.sellerName;
                const avatar = (name || 'NN').substring(0, 1).toUpperCase();

                const item = document.createElement('div');
                item.className = `room-item flex items-center gap-3 p-3 border-b border-gray-100 cursor-pointer hover:bg-gray-50 transition ${activeChatId === room.chatId ? 'bg-orange-50' : ''}`;
                item.setAttribute('data-chat-id', room.chatId);
                item.innerHTML = `
                    <div class="w-10 h-10 rounded-full bg-brand-orange text-white flex items-center justify-center text-sm font-bold flex-shrink-0">${avatar}</div>
                    <div class="flex-1 min-w-0">
                        <div class="flex items-center justify-between">
                            <div class="font-bold text-sm text-gray-800 truncate">${name}</div>
                            <div class="text-[10px] text-gray-400 flex-shrink-0">${ChatService.formatTime(room.lastMessageAt)}</div>
                        </div>
                        <div class="text-xs text-gray-500 truncate">${room.lastMessage || 'Chưa có tin nhắn'}</div>
                        <div class="text-[10px] text-gray-400 truncate">📕 ${room.productTitle || ''}</div>
                    </div>
                    ${room.unreadCount > 0 ? `<div class="bg-red-500 text-white text-[10px] rounded-full h-5 w-5 flex items-center justify-center font-bold flex-shrink-0">${room.unreadCount}</div>` : ''}
                `;
                item.addEventListener('click', () => {
                    if (role === 'seller') {
                        openSellerChat(room.chatId);
                    } else {
                        document.getElementById('chat-sidebar')?.classList.add('hidden');
                        openBuyerChat(room.chatId);
                    }
                });
                container.appendChild(item);
            });
        } catch (err) {
            console.error('Error loading rooms:', err);
            container.innerHTML = '<div class="text-center text-red-400 text-sm py-10">Lỗi tải danh sách</div>';
        }
    };

    const loadMessages = async (chatId) => {
        const container = document.getElementById('chat-messages');
        if (!container) return;

        try {
            const result = await ChatService.getMessages(chatId);
            const messages = result.messages || [];
            // Cập nhật cache với tất cả message IDs
            messages.forEach(m => renderedMessageIds.add(m.messageId));
            renderMessages(messages);
        } catch (err) {
            console.error('Error loading messages:', err);
            container.innerHTML = '<div class="text-center text-red-400 text-sm py-10">Lỗi tải tin nhắn</div>';
        }
    };

    // CẢI TIẾN: Hàm render tin nhắn của Buyer tự động bám sát đáy khi nhận tin mới
    const renderMessages = (messages) => {
        const container = document.getElementById('chat-messages');
        if (!container) return;

        if (!messages || messages.length === 0) {
            container.innerHTML = '<div class="text-center text-gray-400 text-sm py-10">Chưa có tin nhắn. Hãy gửi tin nhắn đầu tiên!</div>';
            return;
        }

        // Kiểm tra xem người dùng có đang ở đáy khung chat không trước khi render tin mới
        const isAtBottom = container.scrollHeight - container.clientHeight <= container.scrollTop + 100;

        const { userId } = ChatService.getAuth();
        container.innerHTML = '';

        messages.forEach(msg => {
            const isMine = msg.senderId == userId;
            const bubble = document.createElement('div');
            bubble.className = `flex ${isMine ? 'justify-end' : 'justify-start'}`;
            bubble.setAttribute('data-msg-id', msg.messageId);
            bubble.innerHTML = `
                <div class="chat-bubble ${isMine ? 'chat-bubble-seller' : 'chat-bubble-buyer'} px-3 py-2 text-sm max-w-[80%]">
                    <div class="${isMine ? 'text-white' : 'text-gray-800'}">${escapeHtml(msg.content)}</div>
                    <div class="text-[10px] ${isMine ? 'text-white/70' : 'text-gray-400'} mt-1 text-right">${ChatService.formatTime(msg.createdAt)}</div>
                </div>
            `;
            container.appendChild(bubble);
        });

        // Nếu đang ở dưới cùng hoặc là tin nhắn đầu, tự động cuộn xuống đáy
        if (isAtBottom) {
            container.scrollTop = container.scrollHeight;
        }
    };

    /**
     * Append 1 tin nhắn mới vào cuối khung chat (không render lại toàn bộ)
     */
    const appendSingleMessage = (msg) => {
        const container = document.getElementById('chat-messages');
        if (!container) return;

        // Kiểm tra tin nhắn đã tồn tại chưa (tránh duplicate)
        if (container.querySelector(`[data-msg-id="${msg.messageId}"]`)) return;

        const isAtBottom = container.scrollHeight - container.clientHeight <= container.scrollTop + 100;
        const { userId } = ChatService.getAuth();
        const isMine = msg.senderId == userId;

        const bubble = document.createElement('div');
        bubble.className = `flex ${isMine ? 'justify-end' : 'justify-start'}`;
        bubble.setAttribute('data-msg-id', msg.messageId);
        bubble.innerHTML = `
            <div class="chat-bubble ${isMine ? 'chat-bubble-seller' : 'chat-bubble-buyer'} px-3 py-2 text-sm max-w-[80%]">
                <div class="${isMine ? 'text-white' : 'text-gray-800'}">${escapeHtml(msg.content)}</div>
                <div class="text-[10px] ${isMine ? 'text-white/70' : 'text-gray-400'} mt-1 text-right">${ChatService.formatTime(msg.createdAt)}</div>
            </div>
        `;
        container.appendChild(bubble);

        // Nếu đang ở dưới cùng, tự động cuộn xuống
        if (isAtBottom) {
            container.scrollTop = container.scrollHeight;
        }
    };

    const updateChatHeader = (room) => {
        const { role } = ChatService.getAuth();
        const name = role === 'seller' ? room.buyerName : room.sellerName;
        const avatar = (name || 'NN').substring(0, 1).toUpperCase();

        const headerName = document.getElementById('chat-header-name');
        const headerAvatar = document.getElementById('chat-header-avatar');
        if (headerName) headerName.textContent = name;
        if (headerAvatar) headerAvatar.textContent = avatar;
    };

    const updateProductCard = (room) => {
        const card = document.getElementById('chat-product-card');
        if (!card) return;

        if (room.productTitle) {
            card.classList.remove('hidden');
            const img = document.getElementById('chat-product-img');
            const title = document.getElementById('chat-product-title');
            const price = document.getElementById('chat-product-price');
            const link = document.getElementById('chat-product-link');

            if (img) img.src = room.productImage || 'https://via.placeholder.com/48x64?text=Book';
            if (title) title.textContent = room.productTitle;
            if (price) price.textContent = ApiService?.formatVND(room.productPrice) || (room.productPrice?.toLocaleString() + 'đ');
            if (link) link.href = `/book/${room.productId}`;
        } else {
            card.classList.add('hidden');
        }
    };

    const startMessagePolling = (chatId) => {
        stopMessagePolling();
        messagePollingInterval = setInterval(() => {
            if (activeChatId === chatId) {
                const { role } = ChatService.getAuth();
                if (role === 'SELLER' || role === 'seller') {
                    loadSellerMessagesSilent(chatId);
                } else {
                    loadMessages(chatId);
                }
            }
        }, 1500); // Giảm từ 3s xuống 1.5s
    };

    const stopMessagePolling = () => {
        if (messagePollingInterval) {
            clearInterval(messagePollingInterval);
            messagePollingInterval = null;
        }
    };

    const stopFirebaseListener = () => {
        if (firebaseUnsubscribe) {
            firebaseUnsubscribe();
            firebaseUnsubscribe = null;
        }
    };

    // ==========================================
    // SELLER CHAT PAGE
    // ==========================================

    /**
     * Khởi tạo trang chat cho seller (layout 2 cột)
     */
    const initSellerChat = () => {
        injectSellerChatStyles();
        loadRoomList('seller');
        startUnreadPolling('seller');
        bindSellerChatEvents();
    };

    const bindSellerChatEvents = () => {
        const inputForm = document.getElementById('seller-chat-input-form');
        const messagesContainer = document.getElementById('seller-chat-messages');

        // Submit form to send message
        inputForm?.addEventListener('submit', async (e) => {
            e.preventDefault();
            const input = document.getElementById('seller-chat-input');
            const content = input.value.trim();
            if (!content || !activeChatId) return;

            input.value = '';
            try {
                await ChatService.sendMessage(activeChatId, content);
                sellerLastDocId = null;
                sellerHasMoreMessages = true;
                loadSellerMessages(activeChatId);
            } catch (err) {
                console.error('Send message error:', err);
                alert('Không thể gửi tin nhắn');
            }
        });

        // Listen for scroll to load more messages
        messagesContainer?.addEventListener('scroll', () => {
            if (messagesContainer.scrollTop < 100 && !sellerIsLoadingMore && sellerHasMoreMessages && activeChatId) {
                loadMoreSellerMessages(activeChatId);
            }
        });
    };

const injectSellerChatStyles = () => {
        const style = document.createElement('style');
        style.textContent = `
            .chat-bubble { max-width: 75%; word-wrap: break-word; }
            .chat-bubble-seller { background: #ea580c; color: white; border-radius: 16px 16px 4px 16px; }
            .chat-bubble-buyer { background: #e5e7eb; color: #1f2937; border-radius: 16px 16px 16px 4px; }
            
            /* Tùy chỉnh thanh cuộn thanh mảnh hiển thị rõ ràng */
            #seller-chat-messages::-webkit-scrollbar { width: 6px; }
            #seller-chat-messages::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
            #seller-chat-messages::-webkit-scrollbar-track { background: #f1f5f9; }
            
            #seller-room-list::-webkit-scrollbar { width: 4px; }
            #seller-room-list::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 4px; }
            .room-item.active { background: #fff7ed; border-left: 3px solid #ea580c; }

            /* Định dạng chuẩn cho Container chính */
            #seller-chat-content {
                display: flex !important;
                flex-direction: column !important;
                height: 100% !important;
                min-height: 0 !important;
                overflow: hidden !important;
            }

            /* Đảm bảo khung Input Wrapper bám chuẩn đáy không lỗi layout */
            #seller-chat-input-wrapper {
                position: absolute !important;
                bottom: 0 !important;
                left: 0 !important;
                right: 0 !important;
                z-index: 30 !important;
                pointer-events: none;
            }
            #seller-chat-input-form {
                pointer-events: auto !important;
            }
        `;
        document.head.appendChild(style);
    };

    /**
     * Mở chat từ seller page
     */
    const openSellerChat = async (chatId) => {
        activeChatId = chatId;
        // Reset cache khi mở room mới
        renderedMessageIds = new Set();
        stopMessagePolling();
        stopFirebaseListener();

        const emptyState = document.getElementById('seller-chat-empty');
        const chatContent = document.getElementById('seller-chat-content');
        const inputWrapper = document.getElementById('seller-chat-input-wrapper');
        if (emptyState) emptyState.classList.add('hidden');
        if (chatContent) chatContent.classList.remove('hidden');
        if (inputWrapper) {
            inputWrapper.style.display = 'block';
            inputWrapper.style.visibility = 'visible';
            inputWrapper.style.opacity = '1';
        }

        document.querySelectorAll('.room-item').forEach(el => el.classList.remove('active'));
        const roomEl = document.querySelector(`.room-item[data-chat-id="${chatId}"]`);
        if (roomEl) roomEl.classList.add('active');

        try {
            const result = await ChatService.getRoomDetail(chatId);
            if (result.success) {
                currentRoom = result.data;
                updateSellerChatHeader(currentRoom);
                updateSellerProductCard(currentRoom);
            }
        } catch (err) {
            console.error('Error loading room:', err);
        }

        loadSellerMessages(chatId);
        ChatService.markAsRead(chatId).catch(() => {});

        // Thử Firebase realtime trước, fallback polling
        const unsub = ChatService.listenMessages(chatId, (msg) => {
            if (!renderedMessageIds.has(msg.messageId)) {
                appendSellerSingleMessage(msg);
                renderedMessageIds.add(msg.messageId);
            }
        }, () => {
            startMessagePolling(chatId);
        });
        if (unsub) {
            firebaseUnsubscribe = unsub;
        } else {
            startMessagePolling(chatId);
        }
    };

    const loadSellerMessages = async (chatId) => {
        const container = document.getElementById('seller-chat-messages');
        if (!container) return;

        if (!sellerLastDocId) {
            container.innerHTML = '<div class="text-center text-gray-400 text-sm py-10">Đang tải...</div>';
        }

        try {
            const result = await ChatService.getMessages(chatId, 30, null);
            const messages = result.messages || [];
            
            if (messages.length > 0) {
                sellerLastDocId = messages[messages.length - 1]?.id;
                sellerHasMoreMessages = messages.length === 30;
                // Cập nhật cache
                messages.forEach(m => renderedMessageIds.add(m.messageId));
            } else {
                sellerHasMoreMessages = false;
            }

            renderSellerMessages(messages);
        } catch (err) {
            console.error('Error loading messages:', err);
            container.innerHTML = '<div class="text-center text-red-400 text-sm py-10">Lỗi tải tin nhắn</div>';
        }
    };

    // Hàm lấy tin nhắn chạy ngầm cho Seller (Không hiện chữ "Đang tải...")
    const loadSellerMessagesSilent = async (chatId) => {
        try {
            const result = await ChatService.getMessages(chatId, 30, null);
            const messages = result.messages || [];
            
            if (messages.length > 0) {
                sellerLastDocId = messages[messages.length - 1]?.id;
                sellerHasMoreMessages = messages.length === 30;
                // Cập nhật cache
                messages.forEach(m => renderedMessageIds.add(m.messageId));
            }
            // Render thẳng ra giao diện mượt mà
            renderSellerMessages(messages);
        } catch (err) {
            console.error('Silent polling error:', err);
        }
    };

    const loadMoreSellerMessages = async (chatId) => {
        if (sellerIsLoadingMore || !sellerLastDocId || !sellerHasMoreMessages) return;
        
        sellerIsLoadingMore = true;
        const container = document.getElementById('seller-chat-messages');
        const scrollHeight = container?.scrollHeight || 0;

        try {
            const result = await ChatService.getMessages(chatId, 30, sellerLastDocId);
            const newMessages = result.messages || [];
            
            if (newMessages.length > 0) {
                sellerLastDocId = newMessages[newMessages.length - 1]?.id;
                sellerHasMoreMessages = newMessages.length === 30;
                // Cập nhật cache
                newMessages.forEach(m => renderedMessageIds.add(m.messageId));
                
                const { userId } = ChatService.getAuth();
                const fragment = document.createDocumentFragment();

                newMessages.reverse().forEach(msg => {
                    const isMine = msg.senderId == userId;
                    const bubble = document.createElement('div');
                    bubble.className = `flex ${isMine ? 'justify-end' : 'justify-start'} mb-3`;
                    bubble.setAttribute('data-msg-id', msg.messageId);
                    bubble.innerHTML = `
                        <div class="chat-bubble ${isMine ? 'chat-bubble-seller' : 'chat-bubble-buyer'} px-3 py-2 text-sm">
                            <div class="${isMine ? 'text-white' : 'text-gray-800'}">${escapeHtml(msg.content)}</div>
                            <div class="text-[10px] ${isMine ? 'text-white/70' : 'text-gray-400'} mt-1 text-right">${ChatService.formatTime(msg.createdAt)}</div>
                        </div>
                    `;
                    fragment.appendChild(bubble);
                });

                if (container?.firstChild) {
                    container.insertBefore(fragment, container.firstChild);
                    container.scrollTop = container.scrollHeight - scrollHeight + 50;
                }
            } else {
                sellerHasMoreMessages = false;
            }
        } catch (err) {
            console.error('Error loading more messages:', err);
        } finally {
            sellerIsLoadingMore = false;
        }
    };

    // CẢI TIẾN: Hàm render tin nhắn của Seller tự động bám sát đáy khi có dữ liệu mới
    const renderSellerMessages = (messages) => {
        const container = document.getElementById('seller-chat-messages');
        if (!container) return;

        if (!messages || messages.length === 0) {
            container.innerHTML = '<div class="text-center text-gray-400 text-sm py-10">Chưa có tin nhắn</div>';
            return;
        }

        const isAtBottom = container.scrollHeight - container.clientHeight <= container.scrollTop + 100;

        const { userId } = ChatService.getAuth();
        container.innerHTML = '';

        messages.forEach(msg => {
            const isMine = msg.senderId == userId;
            const bubble = document.createElement('div');
            bubble.className = `flex ${isMine ? 'justify-end' : 'justify-start'} mb-3`;
            bubble.setAttribute('data-msg-id', msg.messageId);
            bubble.innerHTML = `
                <div class="chat-bubble ${isMine ? 'chat-bubble-seller' : 'chat-bubble-buyer'} px-3 py-2 text-sm">
                    <div class="${isMine ? 'text-white' : 'text-gray-800'}">${escapeHtml(msg.content)}</div>
                    <div class="text-[10px] ${isMine ? 'text-white/70' : 'text-gray-400'} mt-1 text-right">${ChatService.formatTime(msg.createdAt)}</div>
                </div>
            `;
            container.appendChild(bubble);
        });

        if (isAtBottom) {
            container.scrollTop = container.scrollHeight;
        }
    };

    /**
     * Append 1 tin nhắn mới vào khung chat Seller (không render lại toàn bộ)
     */
    const appendSellerSingleMessage = (msg) => {
        const container = document.getElementById('seller-chat-messages');
        if (!container) return;

        // Kiểm tra tin nhắn đã tồn tại chưa
        if (container.querySelector(`[data-msg-id="${msg.messageId}"]`)) return;

        const isAtBottom = container.scrollHeight - container.clientHeight <= container.scrollTop + 100;
        const { userId } = ChatService.getAuth();
        const isMine = msg.senderId == userId;

        const bubble = document.createElement('div');
        bubble.className = `flex ${isMine ? 'justify-end' : 'justify-start'} mb-3`;
        bubble.setAttribute('data-msg-id', msg.messageId);
        bubble.innerHTML = `
            <div class="chat-bubble ${isMine ? 'chat-bubble-seller' : 'chat-bubble-buyer'} px-3 py-2 text-sm">
                <div class="${isMine ? 'text-white' : 'text-gray-800'}">${escapeHtml(msg.content)}</div>
                <div class="text-[10px] ${isMine ? 'text-white/70' : 'text-gray-400'} mt-1 text-right">${ChatService.formatTime(msg.createdAt)}</div>
            </div>
        `;
        container.appendChild(bubble);

        if (isAtBottom) {
            container.scrollTop = container.scrollHeight;
        }
    };

    const updateSellerChatHeader = (room) => {
        const name = room.buyerName || 'Người mua';
        const avatar = name.substring(0, 1).toUpperCase();

        const headerName = document.getElementById('seller-chat-header-name');
        const headerAvatar = document.getElementById('seller-chat-header-avatar');
        if (headerName) headerName.textContent = name;
        if (headerAvatar) headerAvatar.textContent = avatar;
    };

    const updateSellerProductCard = (room) => {
        const card = document.getElementById('seller-product-card');
        if (!card) return;

        if (room.productTitle) {
            card.classList.remove('hidden');
            const img = document.getElementById('seller-product-img');
            const title = document.getElementById('seller-product-title');
            const price = document.getElementById('seller-product-price');
            const link = document.getElementById('seller-product-link');

            if (img) img.src = room.productImage || 'https://via.placeholder.com/48x64?text=Book';
            if (title) title.textContent = room.productTitle;
            if (price) price.textContent = ApiService?.formatVND(room.productPrice) || (room.productPrice?.toLocaleString() + 'đ');
            if (link) link.href = `/book/${room.productId}`;
        } else {
            card.classList.add('hidden');
        }
    };

    // ==========================================
    // UNREAD COUNT POLLING
    // ==========================================

    const startUnreadPolling = (role) => {
        stopUnreadPolling();
        unreadPollingInterval = setInterval(async () => {
            try {
                const result = await ChatService.getUnreadCount(role);
                updateUnreadBadge(result.totalUnread || 0);
            } catch (err) {
                // Silently fail
            }
        }, 10000);
    };

    const stopUnreadPolling = () => {
        if (unreadPollingInterval) {
            clearInterval(unreadPollingInterval);
            unreadPollingInterval = null;
        }
    };

    const updateUnreadBadge = (count) => {
        const badges = document.querySelectorAll('.chat-unread-badge');
        badges.forEach(badge => {
            if (count > 0) {
                badge.classList.remove('hidden');
                badge.textContent = count > 99 ? '99+' : count;
            } else {
                badge.classList.add('hidden');
            }
        });

        const toggleBadge = document.getElementById('chat-unread-badge');
        if (toggleBadge) {
            if (count > 0) {
                toggleBadge.classList.remove('hidden');
                toggleBadge.textContent = count > 99 ? '99+' : count;
            } else {
                toggleBadge.classList.add('hidden');
            }
        }
    };

    // ==========================================
    // UTILITY
    // ==========================================

    const escapeHtml = (text) => {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    };

    // ==========================================
    // GLOBAL INIT
    // ==========================================

    const initGlobalChatIcon = () => {
        const headerIcons = document.querySelector('.flex.items-center.gap-6');
        if (!headerIcons) return;

        const chatIcon = document.createElement('button');
        chatIcon.id = 'global-chat-icon';
        chatIcon.className = 'hover:text-brand-brown transition flex flex-col items-center gap-1 group relative';
        chatIcon.innerHTML = `
            <div class="relative p-2 rounded-full group-hover:bg-brand-hero transition-colors">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"/>
                </svg>
                <span class="chat-unread-badge hidden absolute -top-1 -right-1 bg-red-500 text-white text-[10px] rounded-full h-4 w-4 flex items-center justify-center font-bold border border-white">0</span>
            </div>
            <span class="text-[10px] font-bold hidden md:block">Chat</span>
        `;

        const accountLink = headerIcons.querySelector('a[href*="auth"], a[href*="account"]');
        if (accountLink) {
            headerIcons.insertBefore(chatIcon, accountLink);
        } else {
            headerIcons.appendChild(chatIcon);
        }

        chatIcon.addEventListener('click', () => {
            const { userId, role } = ChatService.getAuth();
            if (!userId) {
                window.location.href = '/main/auth';
                return;
            }
            if (role === 'SELLER') {
                window.location.href = '/seller/chat';
            } else {
                openBuyerSidebar();
            }
        });
    };

    return {
        initBuyerPopup,
        initSellerChat,
        initGlobalChatIcon,
        openBuyerChat,
        openBuyerSidebar,
        openSellerChat,
        loadRoomList,
        stopMessagePolling,
        stopUnreadPolling
    };
})();

window.ChatUI = ChatUI;
