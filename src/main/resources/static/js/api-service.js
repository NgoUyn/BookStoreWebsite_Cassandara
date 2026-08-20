/**
 * 📱 API Service Module - Bookom Bookstore
 * Cung cấp các hàm tiện ích để gọi API từ Frontend
 * * Usage:
 * <script src="api-service.js"></script>
 * const auth = ApiService.getAuth();
 * const books = await ApiService.searchBooks('keyword');
 */

var ApiService = window.ApiService || (() => {
    const API_BASE = '/api';

    // ==========================================
    // 1. UTILITY FUNCTIONS
    // ==========================================

    /**
     * Lấy thông tin xác thực từ localStorage
     */
    const getAuth = () => {
        let userId = localStorage.getItem('userId') || sessionStorage.getItem('userId');
        let token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');
        let role = localStorage.getItem('userRole') || sessionStorage.getItem('userRole');

        // Tự động giải mã JWT chuẩn nếu bị mất ID
        if (token) {
            try {
                let base64Url = token.split('.')[1];
                let base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
                while (base64.length % 4) { base64 += '='; }
                let payload = JSON.parse(window.atob(base64));

                userId = payload.userId || payload.id;
                role = payload.roles ? payload.roles[0] : (payload.role || 'BUYER');

                if (role) role = role.replace('ROLE_', '').toUpperCase();

                // Lưu đè lại để dọn dẹp sạch lỗi GUEST cũ trong trình duyệt của bạn
                if (userId) localStorage.setItem('userId', String(userId));
                if (role) localStorage.setItem('userRole', role);
            } catch (e) {
                console.warn("Lỗi bẻ khóa JWT:", e);
            }
        }

        return { userId, token, role };
    };

    /**
     * Tạo header cho HTTP request
     */
    const getHeaders = () => {
        const { userId, token } = getAuth();
        const headers = {
            'Content-Type': 'application/json',
            'X-User-Id': userId || ''
        };
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }
        return headers;
    };

    /**
     * Format tiền tệ VND
     */
    const formatVND = (value) => {
        return new Intl.NumberFormat('vi-VN', {
            style: 'currency',
            currency: 'VND',
            maximumFractionDigits: 0
        }).format(value || 0);
    };

    /**
     * Parse JSON or text response safely
     */
    const parseResponse = async (response) => {
        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('application/json')) {
            return response.json();
        }
        const text = await response.text();
        return text;
    };

    /**
     * Handle API response with error normalization
     */
    const handleResponse = async (response) => {
        const data = await parseResponse(response);
        if (!response.ok) {
            const message = typeof data === 'string' && data.trim()
                ? data
                : 'Request failed';
            const error = new Error(message);
            error.data = data;
            error.status = response.status;
            error.statusText = response.statusText;
            throw error;
        }
        return data;
    };

    // ==========================================
    // 2. AUTHENTICATION APIs
    // ==========================================

    const Auth = {
        /**
         * Yêu cầu OTP
         * @param {string} email - Email người dùng
         */
        requestOtp: async (email) => {
            const response = await fetch(`${API_BASE}/auth/otp/request`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email })
            });
            return handleResponse(response);
        },

        /**
         * Xác minh OTP
         * @param {string} email
         * @param {string} otp
         */
        verifyOtp: async (email, otp) => {
            const response = await fetch(`${API_BASE}/auth/otp/verify`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, otp })
            });
            return handleResponse(response);
        },

        /**
         * Đăng ký tài khoản
         */
        register: async (username, password, avatarUrl, favoriteCategoryIds) => {
            const response = await fetch(`${API_BASE}/auth/register`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username,
                    password,
                    avatarUrl,
                    favoriteCategoryIds
                })
            });
            return handleResponse(response);
        },

        /**
         * Đăng nhập (JWT)
         * @returns {Object} { tokenType, accessToken, userId, role }
         */
        loginJwt: async (username, password) => {
            const response = await fetch(`${API_BASE}/auth/login-jwt`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            if (!response.ok) throw new Error('Login failed');
            return response.json();
        },

        /**
         * Lấy thông tin profile
         */
        getProfile: async (userId) => {
            const response = await fetch(`${API_BASE}/auth/profile/${userId}`, {
                headers: getHeaders()
            });
            return response.json();
        },

        /**
         * Cập nhật profile
         */
        updateProfile: async (userId, data) => {
            const response = await fetch(`${API_BASE}/auth/profile/${userId}`, {
                method: 'PUT',
                headers: getHeaders(),
                body: JSON.stringify(data)
            });
            return response.json();
        },
        /**
         * Upgrade current authenticated BUYER to SELLER
         * @param {Object} payload { shopName, shopAddress }
         * @returns {Object} response containing new accessToken and role info
         */
        becomeSeller: async (payload = {}) => {
            const response = await fetch(`${API_BASE}/auth/become-seller`, {
                method: 'POST',
                headers: getHeaders(),
                body: JSON.stringify(payload)
            });

            // Parse response body (could be JSON or text)
            const parsed = await parseResponse(response);

            // 202 Accepted: application submitted and pending admin approval
            if (response.status === 202) {
                return { status: 202, message: typeof parsed === 'string' ? parsed : (parsed?.message || parsed) };
            }

            // For other statuses use standard error handling
            if (!response.ok) {
                const message = (typeof parsed === 'string' && parsed.trim()) ? parsed : (parsed?.message || 'Request failed');
                const error = new Error(message);
                error.data = parsed;
                throw error;
            }

            return parsed;
        }
    };

    // ==========================================
    // 3. BOOK APIs
    // ==========================================

    const Book = {
        /**
         * Tìm kiếm sách APPROVED cho BUYER
         */
        search: async (query = '', categoryId = null, page = 0, size = 20, filters = {}) => {
            const params = new URLSearchParams();
            params.set('q', query || '');
            params.set('page', page);
            params.set('size', size);

            if (categoryId) params.append('categoryIds', categoryId);

            const appendIfPresent = (key, value) => {
                if (value !== null && value !== undefined && `${value}`.trim() !== '') {
                    params.append(key, value);
                }
            };

            appendIfPresent('author', filters.author);
            appendIfPresent('minPrice', filters.minPrice);
            appendIfPresent('maxPrice', filters.maxPrice);
            appendIfPresent('publishYearFrom', filters.publishYearFrom);
            appendIfPresent('publishYearTo', filters.publishYearTo);
            appendIfPresent('sort', filters.sort);

            // ✅ ĐÃ SỬA: Đẩy minRating xuống Backend
            appendIfPresent('minRating', filters.minRating);

            // ✅ ĐÃ SỬA: Đẩy danh sách nhà xuất bản (publishers) xuống Backend
            if (filters.publishers && Array.isArray(filters.publishers)) {
                filters.publishers.forEach(pub => {
                    if (pub) params.append('publishers', pub);
                });
            }

            const response = await fetch(`${API_BASE}/books/search?${params}`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Gợi ý tìm kiếm nhanh theo tiêu đề/tác giả
         */
        suggestions: async (query = '', size = 8) => {
            const params = new URLSearchParams();
            params.set('q', query || '');
            params.set('size', size);

            const response = await fetch(`${API_BASE}/books/suggestions?${params}`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Sách bán chạy nhất
         */
        bestSellers: async (size = 8) => {
            const response = await fetch(`${API_BASE}/books/discovery/best-sellers?size=${encodeURIComponent(size)}`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Sách đang trending theo đơn gần đây
         */
        trending: async (size = 8, days = 30) => {
            const params = new URLSearchParams();
            params.set('size', size);
            params.set('days', days);
            const response = await fetch(`${API_BASE}/books/discovery/trending?${params}`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Lấy danh sách sách của SELLER (bao gồm PENDING, APPROVED, REJECTED)
         * Dùng cho trang Inventory Management (S03)
         */
        getSellerBooks: async (query = '', categoryId = null, page = 0, size = 500) => {
            const params = new URLSearchParams({
                q: query,
                page: page,
                size: size
            });
            if (categoryId) params.append('categoryId', categoryId);

            const response = await fetch(`${API_BASE}/books/seller/me?${params}`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Lấy chi tiết sách
         */
        getById: async (bookId) => {
            const response = await fetch(`${API_BASE}/books/${bookId}`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Lấy chi tiết sách của seller hiện tại (Bảo mật - kiểm tra ownership)
         * Chỉ trả về sách mà seller sở hữu
         */
        getOwnBook: async (bookId) => {
            const response = await fetch(`${API_BASE}/books/seller/book/${bookId}`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Tạo sách mới (seller)
         */
        create: async (bookData) => {
            const response = await fetch(`${API_BASE}/books/seller`, {
                method: 'POST',
                headers: getHeaders(),
                body: JSON.stringify(bookData)
            });
            return handleResponse(response);
        },

        /**
         * Cập nhật sách
         */
        update: async (bookId, bookData) => {
            const response = await fetch(`${API_BASE}/books/seller/${bookId}`, {
                method: 'PUT',
                headers: getHeaders(),
                body: JSON.stringify(bookData)
            });
            return handleResponse(response);
        },
        uploadCover: async (bookId, formData) => {
            // CẦN LƯU Ý: Khi dùng fetch với FormData, KHÔNG set header Content-Type.
            // Trình duyệt sẽ tự động set 'multipart/form-data' kèm theo Boundary (ranh giới file).

            const { userId, token } = getAuth();
            const headers = {
                'X-User-Id': userId || ''
            };
            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            }

            const response = await fetch(`${API_BASE}/books/seller/${bookId}/upload-cover`, {
                method: 'POST',
                headers: headers, // Dùng bộ header riêng, KHÔNG dùng getHeaders() vì cái đó đang set cứng application/json
                body: formData
            });

            // Nếu Backend trả về text (đường dẫn link) thay vì JSON, thì return dạng text
            if (!response.ok) throw new Error('Upload ảnh thất bại');

            // Xử lý cẩn thận: nếu response là text thì lấy text, json thì lấy json
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.indexOf("application/json") !== -1) {
                return response.json();
            } else {
                return response.text();
            }
        },
        /**
         * Xóa sách
         */
        delete: async (bookId) => {
            const response = await fetch(`${API_BASE}/books/seller/${bookId}`, {
                method: 'DELETE',
                headers: getHeaders()
            });
            return handleResponse(response);
        }
    };

    const Admin = {
        listSellerApplications: async ({ q = '', page = 0, size = 10 } = {}) => {
            const params = new URLSearchParams();
            params.set('page', page);
            params.set('size', size);
            if (q && `${q}`.trim()) params.set('q', q.trim());
            console.log("Đang kích hoạt gọi API listSellerApplications với URL:", `${API_BASE}/admin/seller-shops-management?${params.toString()}`);
            const response = await fetch(`${API_BASE}/admin/seller-shops-management?${params.toString()}`, { headers: getHeaders() });
            return handleResponse(response);
        },
        approveSellerApplication: async (shopId) => {
            const response = await fetch(`${API_BASE}/admin/seller-shops-management/${encodeURIComponent(shopId)}/approve`, {
                method: 'PUT', headers: getHeaders()
            });
            return handleResponse(response);
        },
        rejectSellerApplication: async (shopId, reason) => {
            const response = await fetch(`${API_BASE}/admin/seller-shops-management/${encodeURIComponent(shopId)}/reject`, {
                method: 'PUT', headers: getHeaders(), body: JSON.stringify({ reason })
            });
            return handleResponse(response);
        }
        ,
        resendEmail: async (shopId, type = 'rejected') => {
            const response = await fetch(`${API_BASE}/admin/seller-shops-management/${encodeURIComponent(shopId)}/resend-email?type=${encodeURIComponent(type)}`, {
                method: 'PUT', headers: getHeaders()
            });
            return handleResponse(response);
        },
        resendNotification: async (shopId, type = 'rejected') => {
            const response = await fetch(`${API_BASE}/admin/seller-shops-management/${encodeURIComponent(shopId)}/resend-notification?type=${encodeURIComponent(type)}`, {
                method: 'PUT', headers: getHeaders()
            });
            return handleResponse(response);
        }
    };

    const WISHLIST_STORAGE_PREFIX = 'bookom:wishlist:';

    const getWishlistKey = (userId = getAuth().userId) => `${WISHLIST_STORAGE_PREFIX}${userId || 'guest'}`;

    const readWishlist = (userId = getAuth().userId) => {
        try {
            const raw = localStorage.getItem(getWishlistKey(userId));
            if (!raw) {
                return [];
            }

            const parsed = JSON.parse(raw);
            return Array.isArray(parsed)
                ? parsed.filter((item) => item && item.id !== undefined && item.id !== null)
                : [];
        } catch (error) {
            return [];
        }
    };

    const writeWishlist = (userId, items) => {
        localStorage.setItem(getWishlistKey(userId), JSON.stringify(items));
    };

    const normalizeWishlistBook = (book) => {
        const bookId = Number(book?.id);
        return {
            id: bookId,
            title: book?.title || '',
            author: book?.author || '',
            price: Number(book?.price || 0),
            imageUrl: book?.imageUrl || '',
            categoryName: book?.categoryName || book?.category?.name || 'Sach',
            stockQuantity: Number(book?.stockQuantity || 0),
            shopName: book?.shopName || book?.seller?.shopName || '',
            savedAt: Date.now()
        };
    };

    const normalizeWishlistItems = (items) => {
        if (!Array.isArray(items)) {
            return [];
        }

        return items
            .filter((item) => item && item.id !== undefined && item.id !== null)
            .map((item) => ({
                id: Number(item.id),
                title: item.title || '',
                author: item.author || '',
                price: Number(item.price || 0),
                imageUrl: item.imageUrl || '',
                categoryName: item.categoryName || 'Sach',
                stockQuantity: Number(item.stockQuantity || 0),
                shopName: item.shopName || '',
                savedAt: Number(item.savedAt || Date.now())
            }));
    };

    const syncWishlistCache = (userId, items) => {
        writeWishlist(userId, normalizeWishlistItems(items));
    };

    const Wishlist = {
        bootstrap: async (buyerId = null) => {
            return Wishlist.getItems(buyerId);
        },

        getItems: async (buyerId = null) => {
            const id = buyerId || getAuth().userId;
            if (!id) {
                return readWishlist(id).slice().sort((a, b) => Number(b.savedAt || 0) - Number(a.savedAt || 0));
            }

            try {
                const response = await fetch(`${API_BASE}/wishlist/me`, {
                    headers: getHeaders()
                });
                const items = await handleResponse(response);
                syncWishlistCache(id, items);
                return readWishlist(id).slice().sort((a, b) => Number(b.savedAt || 0) - Number(a.savedAt || 0));
            } catch (error) {
                return readWishlist(id).slice().sort((a, b) => Number(b.savedAt || 0) - Number(a.savedAt || 0));
            }
        },

        count: (buyerId = null) => readWishlist(buyerId).length,

        isSaved: (bookId, buyerId = null) => {
            const id = Number(bookId);
            if (!id) {
                return false;
            }
            return readWishlist(buyerId).some((item) => Number(item.id) === id);
        },

        toggle: async (book, buyerId = null) => {
            const id = buyerId || getAuth().userId;
            if (!id) {
                throw new Error('Vui long dang nhap de luu vao Wishlist.');
            }

            const snapshot = normalizeWishlistBook(book);
            if (!snapshot.id) {
                throw new Error('Khong the luu sach nay.');
            }

            try {
                const response = await fetch(`${API_BASE}/wishlist/me/${snapshot.id}`, {
                    method: 'POST',
                    headers: getHeaders()
                });
                const data = await handleResponse(response);
                syncWishlistCache(id, data?.items || []);
                return {
                    saved: !!data?.saved,
                    count: Number(data?.count || 0),
                    items: readWishlist(id)
                };
            } catch (error) {
                const items = readWishlist(id);
                const index = items.findIndex((item) => Number(item.id) === snapshot.id);
                let saved = true;

                if (index >= 0) {
                    items.splice(index, 1);
                    saved = false;
                } else {
                    items.unshift(snapshot);
                }

                writeWishlist(id, items);
                return { saved, count: items.length, items };
            }
        },

        remove: async (bookId, buyerId = null) => {
            const id = buyerId || getAuth().userId;
            if (!id) {
                return [];
            }

            try {
                const response = await fetch(`${API_BASE}/wishlist/me/${Number(bookId)}`, {
                    method: 'DELETE',
                    headers: getHeaders()
                });
                const data = await handleResponse(response);
                syncWishlistCache(id, data?.items || []);
                return readWishlist(id);
            } catch (error) {
                const items = readWishlist(id).filter((item) => Number(item.id) !== Number(bookId));
                writeWishlist(id, items);
                return items;
            }
        }
    };

    // ==========================================
    // 4. CART APIs
    // ==========================================

    const Cart = {
        /**
         * Lấy giỏ hàng
         */
        get: async (buyerId = null) => {
            const id = buyerId || getAuth().userId;
            const response = await fetch(`${API_BASE}/carts/buyer/${id}`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Thêm item vào giỏ
         */
        addItem: async (buyerId = null, itemData) => {
            const id = buyerId || getAuth().userId;
            const response = await fetch(`${API_BASE}/carts/buyer/${id}/items`, {
                method: 'POST',
                headers: getHeaders(),
                body: JSON.stringify(itemData)
            });
            return handleResponse(response);
        },

        /**
         * Cập nhật số lượng item
         */
        updateItem: async (buyerId = null, itemId, quantity) => {
            const id = buyerId || getAuth().userId;
            const response = await fetch(
                `${API_BASE}/carts/buyer/${id}/items/${itemId}?quantity=${quantity}`,
                {
                    method: 'PATCH',
                    headers: getHeaders()
                }
            );
            return handleResponse(response);
        },

        /**
         * Xóa item
         */
        removeItem: async (buyerId = null, itemId) => {
            const id = buyerId || getAuth().userId;
            const response = await fetch(
                `${API_BASE}/carts/buyer/${id}/items/${itemId}`,
                {
                    method: 'DELETE',
                    headers: getHeaders()
                }
            );
            return handleResponse(response);
        }
    };

    // ==========================================
    // 5. ORDER APIs
    // ==========================================

    const Order = {
        /**
         * Checkout từ giỏ hàng
         */
        checkout: async (shippingAddress, couponCode = null) => {
            const body = { shippingAddress };
            if (couponCode) body.couponCode = couponCode;

            const response = await fetch(`${API_BASE}/orders/me/checkout`, {
                method: 'POST',
                headers: getHeaders(),
                body: JSON.stringify(body)
            });
            return handleResponse(response);
        },

        /**
         * Lấy danh sách đơn hàng của buyer
         */
        getBuyerOrders: async () => {
            const response = await fetch(`${API_BASE}/orders/me`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Lấy danh sách đơn hàng dạng summary cho buyer
         */
        getBuyerOrderSummaries: async () => {
            const response = await fetch(`${API_BASE}/orders/me/filter/summary`, {
                method: 'POST',
                headers: getHeaders(),
                body: JSON.stringify({})
            });
            return handleResponse(response);
        },

        /**
         * Lấy chi tiết đơn hàng
         */
        getDetail: async (orderId) => {
            const response = await fetch(`${API_BASE}/orders/me/${orderId}`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Lấy sub-orders của seller
         */
        getSellerOrders: async (sellerId = null) => {
            const url = sellerId
                ? `${API_BASE}/orders/seller/${sellerId}/sub-orders`
                : `${API_BASE}/orders/seller/me/sub-orders`;
            const response = await fetch(url, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Xác nhận sub-order - tự động chuyển trạng thái theo luồng:
         * PROCESSING -> CONFIRMED -> SHIPPING -> COMPLETED
         */
        confirmSubOrder: async (subOrderId) => {
            const response = await fetch(
                `${API_BASE}/orders/sub-orders/${subOrderId}/confirm`,
                {
                    method: 'POST',
                    headers: getHeaders()
                }
            );
            return handleResponse(response);
        },

        /**
         * Cập nhật trạng thái sub-order (dùng cho hủy đơn)
         */
        updateSubOrderStatus: async (subOrderId, status) => {
            const response = await fetch(
                `${API_BASE}/orders/sub-orders/${subOrderId}/status?status=${status}`,
                {
                    method: 'PATCH',
                    headers: getHeaders()
                }
            );
            return handleResponse(response);
        },

        /**
         * Hủy đơn hàng của buyer
         */
        cancelBuyerOrder: async (orderId) => {
            const response = await fetch(`${API_BASE}/orders/me/${orderId}/cancel`, {
                method: 'PATCH',
                headers: getHeaders()
            });
            return handleResponse(response);
        }
    };

    // ==========================================
    // 6. SELLER SHOP APIs
    // ==========================================

    const SellerShop = {
        /**
         * Lấy thông tin shop của seller
         */
        getMyShop: async () => {
            const response = await fetch(`${API_BASE}/seller/me/shop`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Tạo shop mới
         */
        create: async (shopData) => {
            const response = await fetch(`${API_BASE}/seller/me/shop`, {
                method: 'POST',
                headers: getHeaders(),
                body: JSON.stringify(shopData)
            });
            return handleResponse(response);
        },

        /**
         * Cập nhật shop
         */
        update: async (shopData) => {
            const response = await fetch(`${API_BASE}/seller/me/shop`, {
                method: 'PUT',
                headers: getHeaders(),
                body: JSON.stringify(shopData)
            });
            return handleResponse(response);
        },

        /**
         * Lấy thông tin shop công khai
         */
        getPublicShop: async (slug) => {
            const response = await fetch(`${API_BASE}/shops/${slug}`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        }
    };

    // ==========================================
    // 7. CATEGORY APIs
    // ==========================================

    const Category = {
        /**
         * Lấy danh sách category
         */
        getAll: async () => {
            const response = await fetch(`${API_BASE}/categories`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        }
    };

    // ==========================================
    // 8. CHAT APIs
    // ==========================================

    const Chat = {
        /**
         * Tạo chat room (hoặc trả về room cũ)
         */
        createRoom: async (productId, sellerId, productTitle, productImage, productPrice) => {
            const response = await fetch(`${API_BASE}/chat/rooms`, {
                method: 'POST',
                headers: getHeaders(),
                body: JSON.stringify({
                    productId,
                    sellerId,
                    productTitle,
                    productImage,
                    productPrice
                })
            });
            return handleResponse(response);
        },

        /**
         * Danh sách rooms của user
         */
        getRooms: async (role = 'buyer') => {
            const response = await fetch(`${API_BASE}/chat/rooms?role=${role}`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Chi tiết 1 room
         */
        getRoomDetail: async (chatId) => {
            const response = await fetch(`${API_BASE}/chat/rooms/${chatId}`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Lịch sử tin nhắn (pagination)
         */
        getMessages: async (chatId, pageSize = 30, lastDocId = null) => {
            let url = `${API_BASE}/chat/messages/${chatId}?pageSize=${pageSize}`;
            if (lastDocId) url += `&lastDocId=${lastDocId}`;
            const response = await fetch(url, {
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Gửi tin nhắn
         */
        sendMessage: async (chatId, content) => {
            const response = await fetch(`${API_BASE}/chat/messages/${chatId}`, {
                method: 'POST',
                headers: getHeaders(),
                body: JSON.stringify({ content })
            });
            return handleResponse(response);
        },

        /**
         * Đánh dấu đã đọc
         */
        markAsRead: async (chatId) => {
            const response = await fetch(`${API_BASE}/chat/rooms/${chatId}/read`, {
                method: 'PUT',
                headers: getHeaders()
            });
            return handleResponse(response);
        },

        /**
         * Số tin chưa đọc
         */
        getUnreadCount: async (role = 'buyer') => {
            const response = await fetch(`${API_BASE}/chat/unread-count?role=${role}`, {
                headers: getHeaders()
            });
            return handleResponse(response);
        }
    };





    // ==========================================
    // PUBLIC API
    // ==========================================
    /**
     * Fetch wrapper that adds authentication headers (keeps caller able to inspect response)
     * @param {string} path - absolute or relative path
     * @param {object} options - fetch options
     */
    const fetchWithAuth = async (path, options = {}) => {
        const merged = Object.assign({}, options);
        const baseHeaders = getHeaders();
        // If caller passed headers, merge; otherwise use baseHeaders
        merged.headers = Object.assign({}, baseHeaders, options.headers || {});
        return fetch(path, merged);
    };

    return {
        // Utility
        getAuth,
        getHeaders,
        formatVND,
        fetchWithAuth,

        // API Groups
        Auth,
        Book,
        Cart,
        Wishlist,
        Order,
        SellerShop,
        Category,
        Chat,
        Admin,

        Recommendation: {
            getBoughtTogether: async (bookId) => {
                const response = await fetch(`${API_BASE}/recommendations/${bookId}/bought-together`, {
                    method: 'GET',
                    headers: getHeaders()
                });
                return handleResponse(response);
            },
            getSimilar: async (bookId) => {
                const response = await fetch(`${API_BASE}/recommendations/${bookId}/similar`, {
                    method: 'GET',
                    headers: getHeaders()
                });
                return handleResponse(response);
            }
        },


        // Helper: Kiểm tra role
        isAuthenticated: () => !!getAuth().userId,
        isSeller: () => getAuth().role === 'SELLER',
        isBuyer: () => getAuth().role === 'BUYER',
        isAdmin: () => getAuth().role === 'ADMIN',

        // Helper: Login store
        storeAuth: (authData) => {
            localStorage.setItem('userId', authData.userId);
            localStorage.setItem('accessToken', authData.accessToken);

            //  Chuẩn hóa ROLE_BUYER thành BUYER ngay khi ghi vào bộ nhớ
            let role = authData.role || '';
            role = role.replace('ROLE_', '').toUpperCase();
            localStorage.setItem('userRole', role);

            if (authData.sellerId) {
                localStorage.setItem('sellerId', String(authData.sellerId));
            } else {
                localStorage.removeItem('sellerId');
            }
        },
        // Helper: Logout - Xóa toàn bộ dữ liệu xác thực và chống rollback
        logout: async () => {
            // 1. Gọi server logout để invalidate token (nếu có)
            try {
                await fetch('/api/auth/logout', {
                    method: 'POST',
                    headers: getHeaders()
                });
            } catch (e) {
                // Bỏ qua lỗi nếu server không hỗ trợ endpoint logout
                console.log('Logout API call failed (optional):', e);
            }

            // 2. Xóa toàn bộ dữ liệu xác thực khỏi localStorage
            localStorage.removeItem('userId');
            localStorage.removeItem('accessToken');
            localStorage.removeItem('userRole');
            localStorage.removeItem('sellerId');

            // 3. Xóa toàn bộ dữ liệu xác thực khỏi sessionStorage
            sessionStorage.removeItem('userId');
            sessionStorage.removeItem('accessToken');
            sessionStorage.removeItem('userRole');
            sessionStorage.removeItem('sellerId');

            // 4. Xóa toàn bộ localStorage (dự phòng xóa các key khác)
            localStorage.clear();

            // 5. Dùng replace() để xóa trang hiện tại khỏi lịch sử trình duyệt
            //    User KHÔNG THỂ bấm nút Back để quay lại trang seller
            window.location.replace('/');
        }
    };
})();

// ==========================================
// Export for Browser
// ==========================================
window.ApiService = ApiService;