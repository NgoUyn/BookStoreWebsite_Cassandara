document.addEventListener('DOMContentLoaded', () => {
    if (!window.ApiService) {
        console.error('ApiService not available');
        return;
    }

    const formatVnd = (value) => new Intl.NumberFormat('vi-VN').format(Math.max(0, Number(value) || 0)) + 'đ';
    const formatDate = (iso) => {
        if (!iso) return '--';
        const d = new Date(iso);
        return d.toLocaleString('vi-VN');
    };

    const getOrderId = async () => {
        const params = new URLSearchParams(window.location.search);
        const queryOrderId = params.get('orderId');
        if (queryOrderId) {
            return Number(queryOrderId);
        }

        const stored = localStorage.getItem('lastOrderId');
        if (stored) {
            return Number(stored);
        }

        const orders = await ApiService.Order.getBuyerOrders();
        const list = Array.isArray(orders) ? orders : [];
        return list.length > 0 ? Number(list[0].orderId || list[0].id) : null;
    };

    const bindPage = async () => {
        const orderId = await getOrderId();
        if (!orderId) {
            return;
        }

        const order = await ApiService.Order.getDetail(orderId);
        const items = Array.isArray(order?.items) ? order.items : [];
        const totalItems = Number(order?.totalItems || 0);
        const subtotal = Number(order?.totalAmount || 0);
        const shipping = totalItems > 0 ? 35000 : 0;
        const shippingDiscount = subtotal >= 250000 ? 15000 : 0;
        const total = Math.max(0, subtotal + shipping - shippingDiscount);
        const cancelButton = document.getElementById('details-cancel-order-button');
        const cancelHint = document.getElementById('details-cancel-hint');
        const canCancel = items.length > 0 && items.every((item) => String(item.subOrderStatus || '').toUpperCase() === 'PROCESSING');

        const metaEl = document.getElementById('details-order-meta');
        if (metaEl) {
            metaEl.innerHTML = `Ma don: <span class="font-bold text-brand-dark">#BKO-${String(orderId).padStart(6, '0')}</span> • Dat luc ${formatDate(order?.createdAt)}`;
        }

        const shippingCard = document.getElementById('details-shipping-card');
        if (shippingCard) {
            shippingCard.innerHTML = `
                <p class="font-bold text-brand-dark">${order?.buyerUsername || 'Nguoi mua'}</p>
                <p class="text-sm text-gray-500">ID: ${order?.buyerId || '--'}</p>
                <p class="text-sm text-gray-500 leading-relaxed mt-2">${order?.shippingAddress || 'Chua co dia chi giao hang'}</p>
            `;
        }

        const subtotalLabel = document.getElementById('details-subtotal-label');
        const subtotalEl = document.getElementById('details-subtotal');
        const shippingEl = document.getElementById('details-shipping');
        const shippingDiscountEl = document.getElementById('details-shipping-discount');
        const totalEl = document.getElementById('details-total');
        if (subtotalLabel) subtotalLabel.textContent = `Tam tinh (${totalItems} san pham)`;
        if (subtotalEl) subtotalEl.textContent = formatVnd(subtotal);
        if (shippingEl) shippingEl.textContent = formatVnd(shipping);
        if (shippingDiscountEl) shippingDiscountEl.textContent = `-${formatVnd(shippingDiscount)}`;
        if (totalEl) totalEl.textContent = formatVnd(total);

        const sellerHint = document.getElementById('details-seller-hint');
        const uniqueSellers = [...new Set(items.map((i) => i.sellerName).filter(Boolean))];
        if (sellerHint) {
            sellerHint.textContent = uniqueSellers.length <= 1
                ? `Duoc ban boi ${uniqueSellers[0] || 'Nha sach'}`
                : `Don hang tu ${uniqueSellers.length} nha ban`;
        }

        const listEl = document.getElementById('details-items-list');
        if (listEl) {
            if (items.length === 0) {
                listEl.innerHTML = '<div class="text-sm text-gray-500 font-semibold">Don hang khong co san pham.</div>';
            } else {
                listEl.innerHTML = items.map((item) => `
                    <div class="flex gap-4">
                        <div class="w-20 h-28 bg-[#2c3e50] border-2 border-gray-100 shadow-sm flex-shrink-0 flex items-center justify-center text-white text-[8px] font-bold text-center">BOOK</div>
                        <div class="flex-grow flex flex-col justify-between py-1">
                            <div>
                                <h3 class="font-bold text-brand-dark text-lg leading-tight">${item.title || 'Khong co ten sach'}</h3>
                                <p class="text-sm text-gray-500 mt-1">Tac gia: ${item.author || 'Dang cap nhat'} • So luong: ${item.quantity || 0}</p>
                                <p class="text-xs text-gray-500 mt-1">Shop: ${item.sellerName || 'Nha sach'}</p>
                            </div>
                            <span class="font-black text-brand-orange">${formatVnd(item.lineTotal || 0)}</span>
                        </div>
                        <div class="flex items-center">
                            <a href="/book/${item.bookId || ''}" class="text-sm font-bold text-brand-brown hover:underline px-4 py-2 border border-brand-border rounded-lg">Xem sach</a>
                        </div>
                    </div>
                `).join('');
            }
        }

        if (cancelButton) {
            cancelButton.disabled = !canCancel;
            cancelButton.classList.toggle('opacity-50', !canCancel);
            cancelButton.classList.toggle('cursor-not-allowed', !canCancel);
            cancelButton.classList.toggle('bg-gray-100', !canCancel);
            cancelButton.classList.toggle('text-gray-400', !canCancel);
            cancelButton.classList.toggle('border-gray-200', !canCancel);
            cancelButton.classList.toggle('bg-red-50', canCancel);
            cancelButton.classList.toggle('text-red-700', canCancel);
            cancelButton.classList.toggle('border-red-200', canCancel);
            cancelButton.textContent = canCancel ? 'Hủy đơn hàng' : 'Không thể hủy';
        }

        if (cancelHint) {
            cancelHint.textContent = canCancel
                ? 'Đơn này đang chờ xác nhận, bạn có thể hủy ngay.'
                : 'Đơn hàng chỉ có thể hủy khi tất cả phần giao vẫn đang chờ xác nhận.';
        }

        if (cancelButton) {
            cancelButton.onclick = async () => {
                if (!canCancel) return;

                if (!confirm('Bạn chắc chắn muốn hủy đơn hàng này?')) {
                    return;
                }

                try {
                    await ApiService.Order.cancelBuyerOrder(orderId);
                    alert('Hủy đơn hàng thành công');
                    location.reload();
                } catch (error) {
                    alert('Lỗi hủy đơn hàng: ' + error.message);
                }
            };
        }
    };

    bindPage().catch((error) => {
        console.error('Bind order details failed:', error);
    });
});
