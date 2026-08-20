(function() {
    if (!window.ApiService) return;

    const API_BASE = '/api';

    // --- DOM Elements ---
    const createBtn = document.getElementById('create-coupon-btn');
    const formContainer = document.getElementById('coupon-form-container');
    const cancelBtn = document.getElementById('cancel-coupon-btn');
    const couponForm = document.getElementById('coupon-form');
    const tableBody = document.getElementById('coupon-table-body');

    // --- Helpers ---
    const formatVnd = (value) => ApiService.formatVND(Math.max(0, Number(value) || 0));

    const getAuthHeaders = () => {
        const auth = ApiService.getAuth();
        const headers = { 'Content-Type': 'application/json' };
        if (auth.token) headers['Authorization'] = `Bearer ${auth.token}`;
        return headers;
    };

    const getSellerId = () => {
        const auth = ApiService.getAuth();
        return auth.sellerId || auth.userId || null;
    };

    const formatDate = (dateStr) => {
        if (!dateStr) return '-';
        const d = new Date(dateStr);
        return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    };

    const getStatusBadge = (coupon) => {
        const now = new Date();
        const startDate = new Date(coupon.startDate);
        const endDate = new Date(coupon.endDate);

        if (now < startDate) {
            return '<span class="text-yellow-600 bg-yellow-50 px-2 py-0.5 rounded text-xs font-bold">Sắp diễn ra</span>';
        } else if (now > endDate) {
            return '<span class="text-red-600 bg-red-50 px-2 py-0.5 rounded text-xs font-bold">Đã kết thúc</span>';
        } else {
            const usageLimit = coupon.usageLimit != null ? coupon.usageLimit : coupon.maxUses;
            const usedCount = coupon.usedCount != null ? coupon.usedCount : (coupon.currentUses || 0);
            if (usageLimit != null && usedCount >= usageLimit) {
                return '<span class="text-red-600 bg-red-50 px-2 py-0.5 rounded text-xs font-bold">Hết lượt</span>';
            }
            return '<span class="text-green-600 bg-green-50 px-2 py-0.5 rounded text-xs font-bold">Đang hoạt động</span>';
        }
    };

    // --- Fetch and render coupons ---
    const fetchCoupons = async () => {
        const sellerId = getSellerId();
        if (!sellerId) {
            tableBody.innerHTML = '<tr><td colspan="9" class="px-4 py-8 text-center text-gray-400 font-semibold">Vui lòng đăng nhập tài khoản SELLER</td></tr>';
            return;
        }

        try {
            const res = await fetch(`${API_BASE}/seller/vouchers`, { headers: getAuthHeaders() });
            if (!res.ok) throw new Error('Không thể tải danh sách mã giảm giá');
            const pageData = await res.json();
            // API trả về Page object { content: [...], totalElements, ... } hoặc array trực tiếp
            const coupons = Array.isArray(pageData) ? pageData : (pageData.content || []);

            if (coupons.length === 0) {
                tableBody.innerHTML = '<tr><td colspan="9" class="px-4 py-8 text-center text-gray-400 font-semibold">Chưa có mã giảm giá nào. Hãy tạo mã mới!</td></tr>';
                return;
            }

            tableBody.innerHTML = coupons.map(c => {
                const discountText = c.discountType === 'PERCENT'
                    ? `${c.discountValue}%`
                    : formatVnd(c.discountValue);
                const usageLimit = c.usageLimit != null ? c.usageLimit : c.maxUses;
                const usedCount = c.usedCount != null ? c.usedCount : (c.currentUses || 0);
                const usageText = usageLimit != null
                    ? `${usedCount} / ${usageLimit}`
                    : `${usedCount} / ∞`;
                const minOrderText = c.minOrderAmount != null ? formatVnd(c.minOrderAmount) : 'Không';

                return `<tr class="hover:bg-[#FAF5E8]/50 transition">
                    <td class="px-4 py-3 font-bold uppercase text-[#D19C74]">${c.code || '-'}</td>
                    <td class="px-4 py-3">${c.discountType === 'PERCENT' ? '%' : 'VNĐ'}</td>
                    <td class="px-4 py-3 font-bold">${discountText}</td>
                    <td class="px-4 py-3">${minOrderText}</td>
                    <td class="px-4 py-3">${usageText}</td>
                    <td class="px-4 py-3 text-xs">${formatDate(c.startDate)}</td>
                    <td class="px-4 py-3 text-xs">${formatDate(c.endDate)}</td>
                    <td class="px-4 py-3">${getStatusBadge(c)}</td>
                    <td class="px-4 py-3 text-center">
                        <button class="delete-coupon-btn text-red-500 hover:text-red-700 transition text-xs font-bold" data-id="${c.id}">Xóa</button>
                    </td>
                </tr>`;
            }).join('');

            // Attach delete handlers
            document.querySelectorAll('.delete-coupon-btn').forEach(btn => {
                btn.addEventListener('click', async (e) => {
                    const voucherId = e.target.getAttribute('data-id');
                    const code = e.target.parentElement.parentElement.querySelector('td:first-child').textContent;
                    if (!confirm(`Xóa mã giảm giá "${code}"?`)) return;
                    try {
                        const res = await fetch(`${API_BASE}/seller/vouchers/${voucherId}`, {
                            method: 'DELETE',
                            headers: getAuthHeaders()
                        });
                        if (!res.ok) throw new Error('Xóa thất bại');
                        await fetchCoupons();
                    } catch (err) {
                        alert(err.message);
                    }
                });
            });

        } catch (err) {
            tableBody.innerHTML = `<tr><td colspan="9" class="px-4 py-8 text-center text-red-500 font-semibold">${err.message}</td></tr>`;
        }
    };

    // --- Toggle form visibility ---
    createBtn?.addEventListener('click', () => {
        formContainer.classList.toggle('hidden');
        if (!formContainer.classList.contains('hidden')) {
            // Set default dates
            const now = new Date();
            const startDateInput = document.getElementById('coupon-start-date');
            const endDateInput = document.getElementById('coupon-end-date');
            if (startDateInput) {
                startDateInput.value = now.toISOString().slice(0, 16);
            }
            if (endDateInput) {
                const endDate = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000);
                endDateInput.value = endDate.toISOString().slice(0, 16);
            }
        }
    });

    cancelBtn?.addEventListener('click', () => {
        formContainer.classList.add('hidden');
        couponForm?.reset();
    });

    // --- Create coupon ---
    couponForm?.addEventListener('submit', async (e) => {
        e.preventDefault();

        const sellerId = getSellerId();
        if (!sellerId) {
            alert('Vui lòng đăng nhập tài khoản SELLER');
            return;
        }

        const code = document.getElementById('coupon-code').value.trim().toUpperCase();
        const discountType = document.getElementById('coupon-type').value;
        const discountValue = parseInt(document.getElementById('coupon-value').value) || 0;
        const minOrderAmountStr = document.getElementById('coupon-min-order').value.trim();
        const maxUsesStr = document.getElementById('coupon-max-uses').value.trim();
        const startDate = document.getElementById('coupon-start-date').value;
        const endDate = document.getElementById('coupon-end-date').value;

        // Validation
        if (!code) return alert('Vui lòng nhập mã giảm giá');
        if (discountValue <= 0) return alert('Giá trị giảm phải lớn hơn 0');
        if (discountType === 'PERCENTAGE' && discountValue > 100) return alert('Phần trăm giảm không được vượt quá 100%');
        if (!startDate) return alert('Vui lòng chọn ngày bắt đầu');
        if (!endDate) return alert('Vui lòng chọn ngày kết thúc');
        if (new Date(endDate) <= new Date(startDate)) return alert('Ngày kết thúc phải sau ngày bắt đầu');

        const payload = {
            code,
            discountType,
            discountValue,
            minOrderAmount: minOrderAmountStr ? parseInt(minOrderAmountStr) : null,
            usageLimit: maxUsesStr ? parseInt(maxUsesStr) : null,
            startDate: new Date(startDate).toISOString(),
            endDate: new Date(endDate).toISOString()
        };

        try {
            const res = await fetch(`${API_BASE}/seller/vouchers`, {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify(payload)
            });

            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                throw new Error(err.message || 'Tạo mã giảm giá thất bại');
            }

            alert('Tạo mã giảm giá thành công!');
            couponForm.reset();
            formContainer.classList.add('hidden');
            await fetchCoupons();
        } catch (err) {
            alert(err.message);
        }
    });

    // --- Bootstrap ---
    fetchCoupons();
})();
