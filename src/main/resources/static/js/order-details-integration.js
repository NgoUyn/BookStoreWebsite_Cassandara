/**
 * 📋 Order Details Integration
 * File: order-details-integration.js
 * 
 * Để sử dụng, thêm vào Order_Details.html:
 * <script src="/js/api-service.js"></script>
 * <script src="/js/order-details-integration.js"></script>
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

    // ==========================================
    // 2. EXTRACT ORDER ID FROM URL
    // ==========================================

    const urlParams = new URLSearchParams(window.location.search);
    const orderId = urlParams.get('orderId');

    if (!orderId) {
        alert('❌ Không tìm thấy mã đơn hàng');
        window.location.href = '/seller/orders';
        return;
    }

    // ==========================================
    // 3. ELEMENT REFERENCES
    // ==========================================

    const orderMetaEl = document.getElementById('details-order-meta') || 
                       document.querySelector('[data-order-meta]');
    const orderItemsEl = document.getElementById('details-items-list') || 
                        document.querySelector('[data-items-list]');
    const orderSellerHintEl = document.getElementById('details-seller-hint') || 
                             document.querySelector('[data-seller-hint]');
    
    const subtotalLabelEl = document.getElementById('details-subtotal-label');
    const subtotalEl = document.getElementById('details-subtotal');
    const shippingEl = document.getElementById('details-shipping');
    const discountEl = document.getElementById('details-discount');
    const totalEl = document.getElementById('details-total');

    const orderStatusLineEl = document.querySelector('[data-progress-line]');
    const statusStepsEl = document.querySelectorAll('[data-status-step]');

    // ==========================================
    // 4. STATE MANAGEMENT
    // ==========================================

    let currentOrder = null;

    // ==========================================
    // 5. UTILITY FUNCTIONS
    // ==========================================

    const formatDate = (dateString) => {
        if (!dateString) return '-';
        const date = new Date(dateString);
        return date.toLocaleDateString('vi-VN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    const formatDateShort = (dateString) => {
        if (!dateString) return '--/--';
        const date = new Date(dateString);
        return `${date.getDate().toString().padStart(2, '0')}/${(date.getMonth() + 1).toString().padStart(2, '0')}`;
    };

    const getStatusLabel = (status) => {
        const labels = {
            'PENDING': '⏳ Chờ xác nhận',
            'CONFIRMED': '✓ Đã xác nhận',
            'PACKED': '📦 Đã đóng gói',
            'SHIPPED': '🚚 Đang giao',
            'DELIVERED': '🏠 Đã giao',
            'CANCELLED': '✗ Đã hủy'
        };
        return labels[status] || status;
    };

    const getProgressPercentage = (status) => {
        const percentages = {
            'PENDING': 20,
            'CONFIRMED': 40,
            'PACKED': 60,
            'SHIPPED': 80,
            'DELIVERED': 100,
            'CANCELLED': 0
        };
        return percentages[status] || 0;
    };

    // ==========================================
    // 6. FETCH & RENDER FUNCTIONS
    // ==========================================

    const loadOrderDetails = async () => {
        try {
            const order = await ApiService.Order.getDetail(orderId);
            currentOrder = order;

            // Render all components
            renderOrderMeta(order);
            renderOrderItems(order);
            renderOrderSummary(order);
            renderOrderStatus(order);

            console.log('✓ Chi tiết đơn hàng tải thành công', order);

        } catch (error) {
            console.error('❌ Lỗi tải chi tiết đơn hàng:', error);
            
            if (orderMetaEl) {
                orderMetaEl.innerHTML = `
                    <div class="text-center py-6">
                        <p class="text-red-600 font-bold">❌ Không thể tải chi tiết đơn hàng</p>
                        <p class="text-sm text-gray-500 mt-2">${error.message}</p>
                        <a href="javascript:window.history.back()" class="text-blue-600 hover:underline mt-3 inline-block">
                            ← Quay lại
                        </a>
                    </div>
                `;
            }
        }
    };

    const renderOrderMeta = (order) => {
        if (!orderMetaEl) return;

        const dateStr = formatDate(order.createdAt);
        orderMetaEl.innerHTML = `
            Mã đơn: <span class="font-bold text-brand-dark">#${order.id}</span> • 
            Đặt lúc <span class="font-bold">${dateStr}</span>
        `;
    };

    const renderOrderItems = (order) => {
        if (!orderItemsEl) return;

        const items = order.items || [];
        if (items.length === 0) {
            orderItemsEl.innerHTML = '<p class="text-center text-gray-500 py-6">Không có sản phẩm</p>';
            return;
        }

        // Get unique sellers for hint
        const sellers = [...new Set(items.map(i => i.sellerName || 'Nhiều nhà bán'))];
        if (orderSellerHintEl) {
            if (sellers.length === 1) {
                orderSellerHintEl.textContent = `Được bán bởi ${sellers[0]}`;
            } else {
                orderSellerHintEl.textContent = `Được bán bởi ${sellers.length} nhà bán`;
            }
        }

        orderItemsEl.innerHTML = items.map(item => `
            <div class="flex gap-4 mb-5 pb-5 border-b last:border-b-0">
                <div class="relative w-20 h-28 bg-[#2c3e50] border-2 border-gray-100 shadow-sm flex-shrink-0 flex items-center justify-center">
                    <span class="text-white font-bold text-[7px] text-center px-1">BOOK</span>
                    <span class="absolute -top-2 -right-2 bg-brand-orange text-white text-[10px] w-5 h-5 rounded-full flex items-center justify-center font-bold">
                        x${item.quantity}
                    </span>
                </div>

                <div class="flex-grow flex flex-col justify-between py-1">
                    <div>
                        <h3 class="font-bold text-brand-dark text-lg leading-tight">${item.title || 'N/A'}</h3>
                        <p class="text-sm text-gray-500 mt-1">
                            Tác giả: ${item.author || 'Đang cập nhật'}
                        </p>
                        <p class="text-xs text-gray-400 mt-1">
                            Bán bởi: <span class="font-semibold">${item.sellerName || 'N/A'}</span>
                        </p>
                    </div>
                    <span class="font-black text-brand-orange text-lg">
                        ${ApiService.formatVND(item.lineTotal || 0)}
                    </span>
                </div>

                <div class="flex items-end">
                    <button 
                        onclick="window.reviewProduct(${item.bookId})"
                        class="text-sm font-bold text-brand-dark px-4 py-2 border-2 border-brand-border rounded-lg hover:bg-gray-50 transition"
                    >
                        ⭐ Đánh giá
                    </button>
                </div>
            </div>
        `).join('');
    };

    const renderOrderSummary = (order) => {
        const totalItems = (order.items || []).length;
        const subtotal = order.totalAmount || 0;
        const shipping = order.shippingFee || 35000;
        const discount = order.discount || 0;
        const total = subtotal + shipping - discount;

        if (subtotalLabelEl) {
            subtotalLabelEl.textContent = `Tạm tính (${totalItems} sản phẩm)`;
        }
        if (subtotalEl) subtotalEl.textContent = ApiService.formatVND(subtotal);
        if (shippingEl) shippingEl.textContent = ApiService.formatVND(shipping);
        if (discountEl) discountEl.textContent = `-${ApiService.formatVND(discount)}`;
        if (totalEl) totalEl.textContent = ApiService.formatVND(total);
    };

    const renderOrderStatus = (order) => {
        const status = order.status || 'PENDING';
        const progress = getProgressPercentage(status);

        // Update progress line
        if (orderStatusLineEl) {
            orderStatusLineEl.style.width = `${progress}%`;
        }

        // Update status steps
        const statusOrder = ['PENDING', 'CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED'];
        statusStepsEl.forEach((step, index) => {
            const stepStatus = statusOrder[index];
            const isActive = statusOrder.indexOf(status) >= index;
            
            const badge = step.querySelector('[data-status-badge]');
            const label = step.querySelector('[data-status-label]');
            const date = step.querySelector('[data-status-date]');

            if (badge) {
                badge.classList.toggle('bg-brand-orange', isActive);
                badge.classList.toggle('bg-gray-200', !isActive);
                badge.classList.toggle('text-white', isActive);
                badge.classList.toggle('text-gray-400', !isActive);
                badge.classList.toggle('shadow-md shadow-brand-orange/30', isActive);
            }

            if (label) {
                label.classList.toggle('text-brand-dark', isActive);
                label.classList.toggle('text-gray-500', !isActive);
                label.classList.toggle('font-bold', isActive);
            }
        });
    };

    // ==========================================
    // 7. ACTION HANDLERS
    // ==========================================

    window.reviewProduct = (bookId) => {
        alert('Tính năng đánh giá sẽ được mở trong phiên bản tiếp theo');
    };

    window.cancelOrder = async () => {
        if (currentOrder.status !== 'PENDING') {
            alert('❌ Chỉ có thể hủy đơn hàng ở trạng thái "Chờ xác nhận"');
            return;
        }

        if (!confirm('⚠️ Bạn chắc chắn muốn hủy đơn hàng này?')) {
            return;
        }

        try {
            await ApiService.Order.updateSubOrderStatus(orderId, 'CANCELLED');
            alert('✓ Hủy đơn hàng thành công');
            location.reload();
        } catch (error) {
            alert('❌ Lỗi hủy đơn hàng: ' + error.message);
        }
    };

    window.downloadInvoice = () => {
        alert('📄 Tính năng tải hóa đơn sẽ được mở trong phiên bản tiếp theo');
    };

    window.contactSeller = () => {
        alert('💬 Tính năng liên hệ người bán sẽ được mở trong phiên bản tiếp theo');
    };

    // ==========================================
    // 8. INITIALIZATION
    // ==========================================

    console.log('📋 Order Details loaded for order #' + orderId);
    loadOrderDetails();

})();
