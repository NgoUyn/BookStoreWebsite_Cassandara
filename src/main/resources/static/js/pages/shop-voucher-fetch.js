/**
 * Shop Voucher Fetch - Client-side voucher rendering for Shop pages
 * Supports both public (buyer/guest) and seller modes:
 * - Public: fetches from /api/coupons/seller/{sellerId}
 * - Seller: fetches from /api/seller/vouchers
 */
(function() {
    'use strict';

    const API_BASE = '/api';

    // --- DOM Elements ---
    const loadingEl = document.getElementById('vouchers-loading');
    const emptyEl = document.getElementById('vouchers-empty');
    const errorEl = document.getElementById('vouchers-error');
    const containerEl = document.getElementById('vouchers-container');

    // Guard: if container doesn't exist, we're not on the right page
    if (!containerEl) return;

    // --- Helpers ---
    const formatVnd = (value) => {
        if (value == null || value <= 0) return null;
        return new Intl.NumberFormat('vi-VN', {
            style: 'currency',
            currency: 'VND',
            maximumFractionDigits: 0
        }).format(value);
    };

    const formatDate = (dateStr) => {
        if (!dateStr) return '-';
        const d = new Date(dateStr);
        return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
    };

    const getAuthHeaders = () => {
        const token = localStorage.getItem('accessToken') || localStorage.getItem('token') || localStorage.getItem('jwt');
        const userId = localStorage.getItem('userId');
        const headers = { 'Content-Type': 'application/json' };
        if (token) headers['Authorization'] = `Bearer ${token}`;
        if (userId) headers['X-User-Id'] = userId;
        return headers;
    };

    /**
     * Render a single voucher card matching the style of voucher_card.html
     */
    const renderVoucherCard = (coupon) => {
        // Determine discount text
        const type = coupon.discountType || coupon.type || 'FIXED';
        const amount = coupon.discountValue || coupon.amount || 0;
        let discountText, discountType;

        if (type === 'PERCENT' || type === 'PERCENTAGE') {
            discountText = amount + '%';
            discountType = 'HOÀN XU';
        } else {
            discountText = amount >= 1000 ? Math.floor(amount / 1000) + 'K' : String(amount);
            discountType = 'GIẢM';
        }

        // Determine remaining count
        const totalQty = coupon.totalQuantity != null ? coupon.totalQuantity : (coupon.usageLimit || coupon.maxUses || 0);
        const usedCount = coupon.usedCount != null ? coupon.usedCount : (coupon.currentUses || 0);
        const remaining = totalQty > 0 ? totalQty - usedCount : -1;

        // Min order text
        const minOrderAmount = coupon.minOrderAmount || coupon.minimumOrderAmount;
        const minOrderText = minOrderAmount != null && minOrderAmount > 0
            ? 'Đơn Tối Thiểu ' + formatVnd(minOrderAmount)
            : 'Mọi Đơn Hàng';

        // Expiry date
        const expiresAt = coupon.expiresAt || coupon.endDate;
        const hsdText = expiresAt ? 'HSD: ' + formatDate(expiresAt) : '';

        // Remaining badge text
        let remainingText;
        if (remaining >= 0 && remaining <= 5) {
            remainingText = 'Sắp hết';
        } else if (remaining === 0) {
            remainingText = 'Hết mã';
        } else if (remaining > 0) {
            remainingText = 'Còn ' + remaining + ' mã';
        } else {
            remainingText = 'Số lượng có hạn';
        }

        const code = coupon.code || '';

        return `
            <div class="voucher-ticket flex-shrink-0 w-72 flex shadow-sm hover:shadow-md transition-shadow">
                <!-- Left column: discount value -->
                <div class="w-1/3 bg-brand-hero border-r border-dashed border-brand-border flex flex-col items-center justify-center p-4">
                    <div class="text-brand-orange font-black text-2xl">${discountText}</div>
                    <div class="text-xs font-bold text-brand-dark mt-1">${discountType}</div>
                </div>
                <!-- Right column: info -->
                <div class="w-2/3 p-4 flex flex-col justify-between bg-white">
                    <div>
                        <div class="font-bold text-sm text-brand-dark">${minOrderText}</div>
                        <div class="text-xs text-gray-500 mt-1">${hsdText}</div>
                    </div>
                    <div class="mt-3 flex justify-between items-center">
                        <div class="text-[10px] bg-brand-bg px-2 py-1 rounded text-gray-500 border border-brand-border">${remainingText}</div>
                        <button data-code="${code}"
                                class="bg-brand-brown text-white text-xs font-bold px-4 py-1.5 rounded hover:bg-brand-brown-dark transition-colors">Lưu mã</button>
                    </div>
                </div>
            </div>
        `;
    };

    /**
     * Show one state element, hide others
     */
    const showState = (showEl) => {
        [loadingEl, emptyEl, errorEl, containerEl].forEach(el => {
            if (el) el.classList.add('hidden');
        });
        if (showEl) showEl.classList.remove('hidden');
    };

    /**
     * Get sellerId from the shop info (try multiple sources)
     */
    const getSellerIdFromShop = () => {
        // Try to get from the shop-host data attribute (set by Thymeleaf)
        const host = document.getElementById('shop-host');
        if (host && host.dataset.slug) {
            // We have slug, will need to fetch shop info first
            return null;
        }
        return null;
    };

    /**
     * Fetch vouchers from API and render
     */
    const fetchAndRenderVouchers = async () => {
        showState(loadingEl);

        try {
            const userRole = localStorage.getItem('userRole');
            const isSeller = userRole === 'SELLER';

            let vouchers = [];

            if (isSeller) {
                // Seller mode: fetch from seller API
                const response = await fetch(`${API_BASE}/seller/vouchers`, {
                    headers: getAuthHeaders()
                });

                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }

                const pageData = await response.json();
                vouchers = Array.isArray(pageData) ? pageData : (pageData.content || []);
            } else {
                // Public mode: need sellerId from shop info
                // First, get the shop slug from the host element
                const host = document.getElementById('shop-host');
                if (!host || !host.dataset.slug) {
                    showState(emptyEl);
                    return;
                }
                const slug = host.dataset.slug;

                // Fetch shop info to get sellerId
                const shopRes = await fetch(`${API_BASE}/shops/${encodeURIComponent(slug)}`);
                if (!shopRes.ok) {
                    throw new Error('Không thể tải thông tin shop');
                }
                const shop = await shopRes.json();
                const sellerId = shop.sellerId || (shop.seller && shop.seller.id) || shop.id;

                if (!sellerId) {
                    showState(emptyEl);
                    return;
                }

                // Fetch public coupons for this seller
                const couponRes = await fetch(`${API_BASE}/coupons/seller/${sellerId}`);
                if (!couponRes.ok) {
                    throw new Error(`HTTP ${couponRes.status}`);
                }

                vouchers = await couponRes.json();
                // Ensure it's an array
                if (!Array.isArray(vouchers)) {
                    vouchers = [];
                }
            }

            if (vouchers.length === 0) {
                showState(emptyEl);
                return;
            }

            // Render all voucher cards
            containerEl.innerHTML = vouchers.map(renderVoucherCard).join('');
            showState(containerEl);

        } catch (err) {
            console.error('Failed to load vouchers:', err);
            showState(errorEl);
        }
    };

    // --- Bootstrap ---
    // Wait for DOM and ApiService to be ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', fetchAndRenderVouchers);
    } else {
        fetchAndRenderVouchers();
    }
})();
