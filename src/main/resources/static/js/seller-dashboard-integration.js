/**
 * 🏪 Seller Dashboard Integration
 * File: seller-dashboard-integration.js
 * 
 * Để sử dụng, thêm vào Seller_Dashboard.html:
 * <script src="/js/api-service.js"></script>
 * <script src="/js/seller-dashboard-integration.js"></script>
 */

(function () {
    // ==========================================
    // 1. CHECK AUTHENTICATION
    // ==========================================

    if (!ApiService.isAuthenticated()) {
        alert('Vui lòng đăng nhập để truy cập');
        window.location.href = '/';
        return;
    }

    if (!ApiService.isSeller()) {
        alert('Chỉ seller mới có quyền truy cập trang này');
        window.location.href = '/';
        return;
    }

    // ==========================================
    // 2. ELEMENT REFERENCES
    // ==========================================

    const sellerShopNameEl = document.getElementById('seller-shop-name');
    const sellerRevenueEl = document.getElementById('seller-revenue');
    const sellerOrdersCountEl = document.getElementById('seller-orders-count');
    const sellerPendingCountEl = document.getElementById('seller-pending-count');
    const sellerOrdersListEl = document.getElementById('seller-orders-list');
    const orderStatusChartCanvas = document.getElementById('seller-order-status-chart');

    // ==========================================
    // 3. DATA STORAGE
    // ==========================================

    let currentShopData = null;
    let currentOrdersData = [];
    let statusChart = null;

    // ==========================================
    // 4. UTILITY FUNCTIONS
    // ==========================================

    const formatDate = (dateString) => {
        if (!dateString) return '-';
        return new Date(dateString).toLocaleDateString('vi-VN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit'
        });
    };

    const getStatusBadgeClass = (status) => {
        const classMap = {
            'PENDING': 'bg-yellow-100 text-yellow-800',
            'CONFIRMED': 'bg-blue-100 text-blue-800',
            'PACKED': 'bg-purple-100 text-purple-800',
            'SHIPPED': 'bg-cyan-100 text-cyan-800',
            'DELIVERED': 'bg-green-100 text-green-800',
            'CANCELLED': 'bg-red-100 text-red-800'
        };
        return classMap[status] || 'bg-gray-100 text-gray-800';
    };

    const getStatusLabel = (status) => {
        const labels = {
            'PENDING': '⏳ Chờ xác nhận',
            'CONFIRMED': '✓ Đã xác nhận',
            'PACKED': '📦 Đã đóng gói',
            'SHIPPED': '🚚 Đang giao',
            'DELIVERED': '✓ Đã giao',
            'CANCELLED': '✗ Đã hủy'
        };
        return labels[status] || status;
    };

    // ==========================================
    // 5. FETCH DATA FUNCTIONS
    // ==========================================

    const fetchShopInfo = async () => {
        try {
            const shop = await ApiService.SellerShop.getMyShop();
            currentShopData = shop;

            if (sellerShopNameEl) {
                sellerShopNameEl.textContent = shop.shopName || 'Chưa có tên shop';
            }

            return shop;
        } catch (error) {
            console.error('❌ Lỗi tải thông tin shop:', error);
            if (sellerShopNameEl) {
                sellerShopNameEl.textContent = '❌ Lỗi tải';
            }
            return null;
        }
    };

    const fetchSellerOrders = async () => {
        try {
            const orders = await ApiService.Order.getSellerOrders();
            currentOrdersData = Array.isArray(orders) ? orders : [];
            return currentOrdersData;
        } catch (error) {
            console.error('❌ Lỗi tải danh sách đơn hàng:', error);
            currentOrdersData = [];
            return [];
        }
    };

    // ==========================================
    // 6. RENDER FUNCTIONS
    // ==========================================

    const renderDashboardStats = (shop, orders) => {
        // Tính toán thống kê từ sub-orders
        // Orders data là SubOrderSummaryResponse array từ API
        const totalRevenue = orders.reduce((sum, o) => sum + (o.subTotal || o.totalAmount || 0), 0);
        const totalOrders = orders.length;
        const pendingOrders = orders.filter(o => o.status === 'PROCESSING' || o.status === 'PENDING').length;
        const confirmedOrders = orders.filter(o => o.status === 'CONFIRMED').length;
        const shippedOrders = orders.filter(o => o.status === 'SHIPPING' || o.status === 'SHIPPED').length;
        const deliveredOrders = orders.filter(o => o.status === 'DELIVERED').length;

        console.log('📊 Dashboard Stats:', { totalRevenue, totalOrders, pendingOrders });

        // Render stats
        if (sellerRevenueEl) {
            sellerRevenueEl.textContent = ApiService.formatVND(totalRevenue);
        }
        if (sellerOrdersCountEl) {
            sellerOrdersCountEl.textContent = totalOrders;
        }
        if (sellerPendingCountEl) {
            sellerPendingCountEl.textContent = pendingOrders;
        }

        // Render chart
        if (orderStatusChartCanvas && typeof Chart !== 'undefined') {
            if (statusChart) {
                statusChart.destroy();
            }

            const ctx = orderStatusChartCanvas.getContext('2d');
            statusChart = new Chart(ctx, {
                type: 'doughnut',
                data: {
                    labels: ['Chờ xác nhận', 'Đã xác nhận', 'Đang giao', 'Đã giao'],
                    datasets: [{
                        data: [pendingOrders, confirmedOrders, shippedOrders, deliveredOrders],
                        backgroundColor: ['#fbbf24', '#3b82f6', '#06b6d4', '#10b981'],
                        borderColor: '#fff',
                        borderWidth: 2
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: true,
                    plugins: {
                        legend: { 
                            position: 'bottom',
                            labels: { 
                                font: { size: 12, weight: 'bold' },
                                padding: 15
                            }
                        }
                    }
                }
            });
        }
    };

    const renderOrdersList = (orders) => {
        if (!sellerOrdersListEl) return;

        if (orders.length === 0) {
            sellerOrdersListEl.innerHTML = `
                <tr>
                    <td colspan="7" class="px-4 py-6 text-center text-gray-500">
                        Không có đơn hàng nào
                    </td>
                </tr>
            `;
            return;
        }

        sellerOrdersListEl.innerHTML = orders.map(order => `
            <tr class="border-b hover:bg-gray-50 transition">
                <td class="px-4 py-3">
                    <span class="font-mono font-bold text-brand-dark">#${order.id}</span>
                </td>
                <td class="px-4 py-3">
                    ${formatDate(order.createdAt)}
                </td>
                <td class="px-4 py-3">
                    <div class="font-semibold">${order.buyerName || 'N/A'}</div>
                    <div class="text-xs text-gray-500">ID: ${order.buyerId || '-'}</div>
                </td>
                <td class="px-4 py-3 text-center">
                    <span class="font-bold text-brand-dark">${order.itemCount || 1}</span> sản phẩm
                </td>
                <td class="px-4 py-3 text-right">
                    <span class="font-black text-brand-orange">
                        ${ApiService.formatVND(order.totalAmount || 0)}
                    </span>
                </td>
                <td class="px-4 py-3">
                    <select 
                        class="border-2 border-gray-200 rounded px-3 py-1 text-sm font-semibold transition 
                                 hover:border-brand-orange focus:border-brand-orange focus:outline-none"
                        onchange="window.updateOrderStatus(${order.id}, this.value)"
                        value="${order.status || 'PENDING'}"
                    >
                        <option value="PENDING">Chờ xác nhận</option>
                        <option value="CONFIRMED">Đã xác nhận</option>
                        <option value="PACKED">Đã đóng gói</option>
                        <option value="SHIPPED">Đang giao</option>
                        <option value="DELIVERED">Đã giao</option>
                        <option value="CANCELLED">Hủy</option>
                    </select>
                </td>
                <td class="px-4 py-3">
                    <button 
                        onclick="window.viewOrderDetails(${order.id})"
                        class="text-brand-orange hover:text-brand-dark font-bold text-sm hover:underline transition"
                    >
                        Xem chi tiết →
                    </button>
                </td>
            </tr>
        `).join('');
    };

    // ==========================================
    // 7. ACTION HANDLERS
    // ==========================================

    window.updateOrderStatus = async (subOrderId, newStatus) => {
        try {
            if (!confirm(`Bạn muốn cập nhật trạng thái sang "${getStatusLabel(newStatus)}"?`)) {
                // Revert dropdown
                location.reload();
                return;
            }

            await ApiService.Order.updateSubOrderStatus(subOrderId, newStatus);
            alert('✓ Cập nhật trạng thái thành công');
            
            // Reload data
            await loadDashboardData();
        } catch (error) {
            alert('❌ Lỗi cập nhật: ' + (error.message || 'Thử lại sau'));
            console.error(error);
            // Revert dropdown
            location.reload();
        }
    };

    window.viewOrderDetails = (orderId) => {
        window.location.href = `/main/order-details?orderId=${orderId}`;
    };

    window.refreshDashboard = async () => {
        await loadDashboardData();
        alert('✓ Đã làm mới dữ liệu');
    };

    // ==========================================
    // 8. MAIN LOAD FUNCTION
    // ==========================================

    const loadDashboardData = async () => {
        try {
            const [shop, orders] = await Promise.all([
                fetchShopInfo(),
                fetchSellerOrders()
            ]);

            renderDashboardStats(shop, orders);
            renderOrdersList(orders);
        } catch (error) {
            console.error('❌ Lỗi tải dashboard:', error);
            alert('❌ Không thể tải dữ liệu dashboard');
        }
    };

    // ==========================================
    // 9. INITIALIZATION
    // ==========================================

    console.log('📊 Seller Dashboard loaded successfully');
    loadDashboardData();

})();
