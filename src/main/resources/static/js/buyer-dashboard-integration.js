/**
 * 👤 Buyer Dashboard Integration
 * File: buyer-dashboard-integration.js
 * 
 * Để sử dụng, thêm vào Buyer_DashBoard.html:
 * <script src="/js/api-service.js"></script>
 * <script src="/js/buyer-dashboard-integration.js"></script>
 */

(function () {
    // ==========================================
    // 1. CHECK AUTHENTICATION
    // ==========================================

    if (!ApiService.isAuthenticated()) {
        alert('Vui lòng đăng nhập');
        window.location.href = '/';
        return;
    }

    if (!ApiService.isBuyer()) {
        alert('Chỉ buyer mới có quyền truy cập');
        window.location.href = '/';
        return;
    }

    // ==========================================
    // 2. ELEMENT REFERENCES
    // ==========================================

    const buyerNameEls = document.querySelectorAll('#buyer-name, #sidebar-name, [data-buyer-name]');
    const buyerEmailEls = document.querySelectorAll('#buyer-email, [data-buyer-email]');
    const buyerAvatarEls = document.querySelectorAll('#header-avatar-preview, #sidebar-avatar, #profile-avatar-preview, [data-buyer-avatar]');

    const ordersListEl = document.getElementById('buyer-orders-list') || document.querySelector('[data-orders-list]');
    const totalOrdersEls = document.querySelectorAll('#total-orders, [data-total-orders]');
    const totalSpendEls = document.querySelectorAll('#total-spend, [data-total-spend]');
    const totalOrdersNavEls = document.querySelectorAll('[data-total-orders-nav]');
    const pendingCountEls = document.querySelectorAll('[data-pending-count]');
    const shippingCountEls = document.querySelectorAll('[data-shipping-count]');
    const completedCountEls = document.querySelectorAll('[data-completed-count]');

    const filterButtons = Array.from(document.querySelectorAll('button[data-filter]'));
    const searchOrdersInput = document.getElementById('search-orders') || document.querySelector('input[data-search="orders"]');

    // ==========================================
    // 3. STATE MANAGEMENT
    // ==========================================

    let allOrders = [];
    let currentFilter = 'all';
    let currentSearchQuery = '';

    // ==========================================
    // 4. UTILITY FUNCTIONS
    // ==========================================

    const normalizeStatus = (status) => String(status || 'PROCESSING').toUpperCase();

    const getOrderId = (order) => Number(order?.orderId ?? order?.id ?? 0);

    const formatDate = (dateString) => {
        if (!dateString) return '-';
        return new Date(dateString).toLocaleString('vi-VN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    const formatOrderCode = (orderId) => `#BKO-${String(orderId || 0).padStart(6, '0')}`;

    const getStatusMeta = (status) => {
        const normalized = normalizeStatus(status);

        const meta = {
            PROCESSING: {
                label: 'Đang xác nhận',
                icon: '⏳',
                badge: 'bg-amber-100 text-amber-800',
                border: 'border-amber-200',
                hint: 'Đơn hàng đang chờ người bán xác nhận.'
            },
            COMFIRMED: {
                label: 'Đã xác nhận',
                icon: '📝',
                badge: 'bg-blue-100 text-blue-800',
                border: 'border-blue-200',
                hint: 'Người bán đã xác nhận đơn hàng.'
            },
            SHIPPING: {
                label: 'Đang giao',
                icon: '🚚',
                badge: 'bg-cyan-100 text-cyan-800',
                border: 'border-cyan-200',
                hint: 'Đơn hàng đang trên đường giao tới bạn.'
            },
            COMPLETED: {
                label: 'Hoàn thành',
                icon: '✅',
                badge: 'bg-green-100 text-green-800',
                border: 'border-green-200',
                hint: 'Đơn hàng đã hoàn tất.'
            },
            CANCELLED: {
                label: 'Đã hủy',
                icon: '✕',
                badge: 'bg-red-100 text-red-800',
                border: 'border-red-200',
                hint: 'Đơn hàng đã bị hủy.'
            }
        };

        return meta[normalized] || {
            label: normalized,
            icon: '•',
            badge: 'bg-gray-100 text-gray-700',
            border: 'border-gray-200',
            hint: 'Trạng thái đơn hàng.'
        };
    };

    const isPendingLike = (status) => {
        const normalized = normalizeStatus(status);
        return normalized === 'PROCESSING';
    };

    const isCancelable = (order) => normalizeStatus(order?.overallStatus) === 'PROCESSING';

    const setTextOnNodes = (nodes, value) => {
        nodes.forEach((node) => {
            node.textContent = value;
        });
    };

    const setAvatarNodes = (nodes, avatarUrl) => {
        if (!avatarUrl) return;
        nodes.forEach((node) => {
            if (node.tagName === 'IMG') {
                node.src = avatarUrl;
                return;
            }
            node.style.backgroundImage = `url('${avatarUrl}')`;
        });
    };

    const updateStats = () => {
        const totalSpend = allOrders.reduce((sum, order) => sum + Number(order?.totalAmount || 0), 0);
        const pendingCount = allOrders.filter((order) => isPendingLike(order?.overallStatus)).length;
        const shippingCount = allOrders.filter((order) => normalizeStatus(order?.overallStatus) === 'SHIPPING').length;
        const completedCount = allOrders.filter((order) => normalizeStatus(order?.overallStatus) === 'COMPLETED').length;

        setTextOnNodes(totalOrdersEls, String(allOrders.length));
        setTextOnNodes(totalSpendEls, ApiService.formatVND(totalSpend));
        setTextOnNodes(totalOrdersNavEls, String(allOrders.length));
        setTextOnNodes(pendingCountEls, String(pendingCount));
        setTextOnNodes(shippingCountEls, String(shippingCount));
        setTextOnNodes(completedCountEls, String(completedCount));
    };

    const filterOrders = () => {
        let filtered = [...allOrders];

        if (currentFilter !== 'all') {
            filtered = filtered.filter((order) => {
                const status = normalizeStatus(order?.overallStatus);
                if (currentFilter === 'pending') {
                    return isPendingLike(status);
                }
                if (currentFilter === 'shipping') {
                    return status === 'SHIPPING';
                }
                if (currentFilter === 'completed') {
                    return status === 'COMPLETED';
                }
                if (currentFilter === 'cancelled') {
                    return status === 'CANCELLED';
                }
                return true;
            });
        }

        if (currentSearchQuery) {
            const query = currentSearchQuery.toLowerCase();
            filtered = filtered.filter((order) => {
                const statusMeta = getStatusMeta(order?.overallStatus);
                return (
                    String(getOrderId(order)).includes(query) ||
                    String(order?.shippingAddress || '').toLowerCase().includes(query) ||
                    String(order?.buyerUsername || '').toLowerCase().includes(query) ||
                    statusMeta.label.toLowerCase().includes(query)
                );
            });
        }

        return filtered;
    };

    const updateFilterButtons = () => {
        filterButtons.forEach((button) => {
            const isActive = button.getAttribute('data-filter') === currentFilter;
            button.classList.toggle('bg-brand-orange', isActive);
            button.classList.toggle('text-white', isActive);
            button.classList.toggle('bg-gray-50', !isActive);
            button.classList.toggle('text-gray-700', !isActive);
        });
    };

    // ==========================================
    // 5. FETCH & RENDER FUNCTIONS
    // ==========================================

    const loadBuyerProfile = async () => {
        try {
            const userId = ApiService.getAuth().userId;
            const profile = await ApiService.Auth.getProfile(userId);

            setTextOnNodes(buyerNameEls, profile.username || 'Khách hàng');
            setTextOnNodes(buyerEmailEls, profile.email || '');
            setAvatarNodes(buyerAvatarEls, profile.avatarUrl || 'https://i.pravatar.cc/150?img=11');
        } catch (error) {
            console.error('❌ Lỗi tải profile:', error);
        }
    };

    const loadBuyerOrders = async () => {
        try {
            const response = await ApiService.Order.getBuyerOrderSummaries();
            const orders = Array.isArray(response?.orders) ? response.orders : Array.isArray(response) ? response : [];
            allOrders = orders;
            updateStats();
            renderOrdersList();
        } catch (error) {
            console.error('❌ Lỗi tải đơn hàng:', error);
            if (ordersListEl) {
                ordersListEl.innerHTML = `
                    <div class="rounded-2xl border border-red-200 bg-red-50 px-6 py-10 text-center text-red-700 font-semibold">
                        Không thể tải danh sách đơn hàng. Vui lòng thử lại sau.
                    </div>
                `;
            }
        }
    };

    const renderOrdersList = () => {
        if (!ordersListEl) return;

        const filtered = filterOrders();

        if (filtered.length === 0) {
            ordersListEl.innerHTML = `
                <div class="rounded-2xl border border-dashed border-brand-accent bg-brand-cream/30 px-6 py-12 text-center text-gray-500 font-semibold">
                    ${currentSearchQuery ? 'Không tìm thấy đơn hàng phù hợp.' : 'Bạn chưa có đơn hàng nào.'}
                </div>
            `;
            return;
        }

        ordersListEl.innerHTML = filtered.map((order) => {
            const orderId = getOrderId(order);
            const statusMeta = getStatusMeta(order?.overallStatus);
            const canCancel = isCancelable(order);
            const createdAt = formatDate(order?.createdAt);
            const totalAmount = ApiService.formatVND(order?.totalAmount || 0);
            const subOrderCount = Number(order?.subOrderCount || 0);
            const shippingAddress = order?.shippingAddress || 'Chưa có địa chỉ giao hàng';

            return `
                <article class="rounded-2xl border ${statusMeta.border} bg-white shadow-sm overflow-hidden hover:shadow-md transition-shadow">
                    <div class="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-4 px-5 py-4 bg-brand-cream/30 border-b ${statusMeta.border}">
                        <div class="space-y-2">
                            <div class="flex items-center gap-2 flex-wrap">
                                <span class="font-black text-brand-dark text-lg">${formatOrderCode(orderId)}</span>
                                <span class="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-black ${statusMeta.badge}">
                                    <span>${statusMeta.icon}</span>
                                    <span>${statusMeta.label}</span>
                                </span>
                            </div>
                            <p class="text-sm text-gray-500">Đặt lúc ${createdAt}</p>
                            <p class="text-sm text-gray-600 line-clamp-2 max-w-3xl">${shippingAddress}</p>
                        </div>
                        <div class="text-left lg:text-right shrink-0">
                            <div class="text-xs uppercase tracking-[0.2em] text-gray-400 font-bold">Thành tiền</div>
                            <div class="text-3xl font-black text-brand-orange mt-1">${totalAmount}</div>
                            <div class="text-xs text-gray-500 font-semibold mt-1">${subOrderCount} phần giao</div>
                        </div>
                    </div>

                    <div class="px-5 py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                        <div class="flex items-center gap-3 text-sm text-gray-600">
                            <span class="inline-flex h-10 w-10 items-center justify-center rounded-full bg-brand-cream text-brand-orange font-black">${statusMeta.icon}</span>
                            <div>
                                <div class="font-bold text-brand-dark">${statusMeta.label}</div>
                                <div class="text-xs text-gray-500">${statusMeta.hint}</div>
                            </div>
                        </div>

                        <div class="flex flex-wrap gap-3">
                            <button 
                                type="button"
                                onclick="window.viewOrderDetail(${orderId})"
                                class="px-4 py-2 bg-brand-orange text-white rounded-xl hover:bg-brand-dark transition font-bold text-sm shadow-sm"
                            >
                                Xem chi tiết
                            </button>
                            ${canCancel ? `
                                <button 
                                    type="button"
                                    onclick="window.cancelOrder(${orderId})"
                                    class="px-4 py-2 bg-red-50 text-red-700 rounded-xl hover:bg-red-100 transition font-bold text-sm border border-red-200"
                                >
                                    Hủy đơn
                                </button>
                            ` : ''}
                        </div>
                    </div>
                </article>
            `;
        }).join('');
    };

    // ==========================================
    // 6. ACTION HANDLERS
    // ==========================================

    window.viewOrderDetail = (orderId) => {
        window.location.href = `/main/order-details?orderId=${orderId}`;
    };

    window.cancelOrder = async (orderId) => {
        if (!confirm('Bạn chắc chắn muốn hủy đơn hàng này?')) {
            return;
        }

        try {
            await ApiService.Order.cancelBuyerOrder(orderId);
            alert('Hủy đơn hàng thành công');
            await loadBuyerOrders();
        } catch (error) {
            alert('Lỗi hủy đơn hàng: ' + error.message);
        }
    };

    // ==========================================
    // 7. FILTER & SEARCH
    // ==========================================

    filterButtons.forEach((button) => {
        button.addEventListener('click', () => {
            currentFilter = button.getAttribute('data-filter') || 'all';
            updateFilterButtons();
            renderOrdersList();
        });
    });

    if (searchOrdersInput) {
        let searchTimeout;
        searchOrdersInput.addEventListener('input', (event) => {
            currentSearchQuery = event.target.value.trim();
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                renderOrdersList();
            }, 250);
        });
    }

    // ==========================================
    // 8. INITIALIZATION
    // ==========================================

    console.log('👤 Buyer Dashboard loaded');
    updateFilterButtons();
    Promise.all([loadBuyerProfile(), loadBuyerOrders()]);

})();
