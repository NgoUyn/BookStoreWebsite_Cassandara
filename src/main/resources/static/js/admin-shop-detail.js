/**
 * admin-shop-detail.js
 * Trang xem CHI TIẾT (read-only) một hồ sơ đăng ký người bán cho admin.
 * Dữ liệu lấy từ GET /api/admin/seller-shops-management/{shopId}.
 */
(function () {
    'use strict';

    const API = () => window.ApiService;

    // Yêu cầu hồ sơ theo từng loại hình kinh doanh (để admin đối chiếu nhanh)
    const BUSINESS_TYPES = [
        { code: 'INDIVIDUAL', label: 'Cá nhân', docs: 'CCCD/CMND (2 mặt)', note: 'MST cá nhân nếu có phát sinh doanh thu.' },
        { code: 'HOUSEHOLD', label: 'Hộ kinh doanh', docs: 'CCCD người đại diện + Giấy chứng nhận ĐKKD hộ kinh doanh + MST', note: 'Bắt buộc có người đại diện.' },
        { code: 'ENTERPRISE', label: 'Doanh nghiệp', docs: 'Giấy chứng nhận ĐKDN + MST doanh nghiệp + CCCD người đại diện', note: 'Đối chiếu ngành nghề đăng ký.' },
        { code: 'SPECIAL', label: 'Ngành nghề đặc thù', docs: 'ĐKKD + giấy phép chuyên ngành (xuất bản, in ấn…)', note: 'Giấy phép chuyên ngành phải còn hiệu lực.' }
    ];

    const IDENTITY_LABELS = { CCCD: 'Căn cước công dân (CCCD)', CMND: 'Chứng minh nhân dân (CMND)', PASSPORT: 'Hộ chiếu (Passport)' };

    const DOC_TYPE_LABELS = {
        IDENTITY: 'Giấy tờ tuỳ thân',
        BUSINESS_REGISTRATION: 'Đăng ký kinh doanh',
        TAX: 'Thuế / MST',
        SPECIAL_LICENSE: 'Giấy phép chuyên ngành',
        BRAND_DOC: 'Tài liệu thương hiệu'
    };

    const els = {};
    let shopId = null;
    let fromPage = 'seller-applications';
    let loading = false;

    const escapeHtml = (value) => value == null ? '' : String(value)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');

    const dash = (value) => (value == null || `${value}`.trim() === '') ? '—' : value;

    const setText = (id, value) => {
        const el = document.getElementById(id);
        if (el) el.textContent = dash(value);
    };

    const formatDate = (value, withTime) => {
        if (!value) return '—';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return String(value);
        return new Intl.DateTimeFormat('vi-VN', withTime
            ? { dateStyle: 'medium', timeStyle: 'short' }
            : { dateStyle: 'medium' }).format(date);
    };

    const formatNumber = (value) => {
        if (value == null || value === '') return '—';
        const num = Number(value);
        if (Number.isNaN(num)) return String(value);
        return num.toLocaleString('vi-VN');
    };

    const statusLabel = (status) => {
        if (status === 'PENDING') return 'Chờ duyệt';
        if (status === 'APPROVED') return 'Đã duyệt';
        if (status === 'REJECTED') return 'Đã từ chối';
        return dash(status);
    };

    const statusClass = (status) => {
        if (status === 'PENDING') return 'inline-flex items-center rounded-full bg-amber-100 px-3 py-1 text-xs font-black text-amber-700';
        if (status === 'APPROVED') return 'inline-flex items-center rounded-full bg-emerald-100 px-3 py-1 text-xs font-black text-emerald-700';
        if (status === 'REJECTED') return 'inline-flex items-center rounded-full bg-rose-100 px-3 py-1 text-xs font-black text-rose-700';
        return 'inline-flex items-center rounded-full bg-gray-100 px-3 py-1 text-xs font-black text-gray-600';
    };

    const initials = (name) => {
        const parts = `${name || ''}`.trim().split(/\s+/).filter(Boolean);
        if (parts.length === 0) return 'NN';
        return parts.slice(0, 2).map((p) => p[0].toUpperCase()).join('');
    };

    const setLoading = (value) => {
        loading = value;
        if (els.loadingState) els.loadingState.classList.toggle('hidden', !value);
        if (els.detailContent && value) els.detailContent.classList.add('hidden');
        if (els.refreshBtn) {
            els.refreshBtn.disabled = value;
            els.refreshBtn.classList.toggle('opacity-60', value);
        }
    };

    const showError = (message) => {
        if (els.errorState) {
            els.errorState.textContent = message;
            els.errorState.classList.remove('hidden');
        }
        if (els.detailContent) els.detailContent.classList.add('hidden');
    };

    const clearError = () => {
        if (els.errorState) {
            els.errorState.textContent = '';
            els.errorState.classList.add('hidden');
        }
    };

    window.initAdminShopDetail = function () {
        els.context = document.getElementById('shopDetailContext');
        els.backBtn = document.getElementById('backBtn');
        els.refreshBtn = document.getElementById('refreshBtn');
        els.loadingState = document.getElementById('loadingState');
        els.errorState = document.getElementById('errorState');
        els.detailContent = document.getElementById('detailContent');

        shopId = els.context ? els.context.dataset.shopId : null;
        fromPage = (els.context && els.context.dataset.from) || 'seller-applications';
        if (els.backBtn) {
            els.backBtn.href = '/admin/seller-applications';
        }
        els.refreshBtn?.addEventListener('click', load);

        if (!shopId) {
            setLoading(false);
            showError('Thiếu mã hồ sơ gian hàng trên đường dẫn.');
            return;
        }
        load();
    };

    // ------------------------------------------------------------------
    // RENDER
    // ------------------------------------------------------------------

    const renderHeader = (data) => {
        const name = data.shopName || 'Gian hàng';
        setText('shopName', name);
        setText('shopSlugLine', data.slug ? `/shop/${data.slug}` : 'Chưa có slug');
        setText('shopDescription', data.description || 'Chưa có mô tả.');

        // Logo + fallback chữ cái đầu
        const logo = document.getElementById('shopLogo');
        const logoFallback = document.getElementById('shopLogoFallback');
        if (logo && logoFallback) {
            if (data.logoUrl) {
                logo.src = data.logoUrl;
                logo.classList.remove('hidden');
                logoFallback.classList.add('hidden');
            } else {
                logo.src = '';
                logo.classList.add('hidden');
                logoFallback.classList.remove('hidden');
                logoFallback.textContent = initials(name);
            }
        }

        const pill = document.getElementById('shopStatusPill');
        if (pill) {
            pill.className = statusClass(data.approvalStatus);
            pill.textContent = statusLabel(data.approvalStatus);
        }

        setText('metaProductCount', formatNumber(data.productCount));
        setText('metaFollowerCount', formatNumber(data.followerCount));
        setText('metaRating', data.rating == null
            ? '—'
            : `${formatNumber(data.rating)}${data.ratingCount ? ` (${formatNumber(data.ratingCount)} lượt)` : ''}`);
        setText('metaCreatedAt', formatDate(data.createdAt, true));
        setText('metaUpdatedAt', formatDate(data.updatedAt, true));

        setText('detailSummary', `Hồ sơ #${data.id} · ${data.shopName || 'Không tên'} · ${statusLabel(data.approvalStatus)}`);

        // Cảnh báo từ chối
        if (els && data.approvalStatus === 'REJECTED') {
            const card = document.getElementById('rejectionCard');
            if (card) {
                card.classList.remove('hidden');
                setText('rejectionReasonValue', data.rejectionReason || 'Không có lý do cụ thể.');
            }
        }
    };

    const renderShopInfo = (data) => {
        setText('infoShopName', data.shopName);
        setText('infoSlug', data.slug);
        setText('infoContactEmail', data.contactEmail);
        setText('infoContactPhone', data.contactPhone);
        setText('infoAddress', data.address);
        setText('infoCity', [data.city, data.province].filter((v) => v && v.trim()).join(' · ') || null);
    };

    const renderSeller = (data) => {
        const seller = data.seller || {};
        const fullName = seller.fullName || seller.username || 'Không rõ';
        setText('sellerFullName', fullName);
        setText('sellerUsername', seller.username ? `@${seller.username}` : null);
        setText('sellerIdValue', seller.id ? `#${seller.id}` : null);
        setText('sellerEmail', seller.email);
        setText('sellerPhone', seller.phone);

        const avatar = document.getElementById('sellerAvatar');
        const avatarFallback = document.getElementById('sellerAvatarFallback');
        if (avatar && avatarFallback) {
            if (seller.avatarUrl) {
                avatar.src = seller.avatarUrl;
                avatar.classList.remove('hidden');
                avatarFallback.classList.add('hidden');
            } else {
                avatar.src = '';
                avatar.classList.add('hidden');
                avatarFallback.classList.remove('hidden');
                avatarFallback.textContent = initials(fullName).slice(0, 1);
            }
        }
    };

    const renderIdentity = (data) => {
        const type = BUSINESS_TYPES.find((t) => t.code === data.businessType);
        setText('infoBusinessType', type ? `${type.label} (${data.businessType})` : data.businessType);
        setText('infoRepresentative', data.representativeName);
        setText('infoIdentityType', IDENTITY_LABELS[data.identityType] || data.identityType);
        setText('infoIdentityNumber', data.identityNumber);
    };

    const renderTax = (data) => {
        const hasTax = !!(data.taxCode && `${data.taxCode}`.trim());
        setText('infoTaxCode', hasTax ? data.taxCode : 'Chưa cập nhật');
        document.getElementById('taxWarning')?.classList.toggle('hidden', hasTax);
        document.getElementById('taxOk')?.classList.toggle('hidden', !hasTax);
    };

    const renderCarriers = (data) => {
        const list = document.getElementById('carrierList');
        const empty = document.getElementById('carrierEmpty');
        if (!list || !empty) return;
        const carriers = Array.isArray(data.shippingCarriers)
            ? data.shippingCarriers.filter((c) => c && `${c}`.trim()) : [];
        list.innerHTML = carriers.map((c) =>
            `<span class="inline-flex items-center gap-2 rounded-full border border-[#E5CBB5] bg-[#FAF5E8] px-3.5 py-1.5 text-xs font-black text-[#5D4037]">${escapeHtml(c)}</span>`
        ).join('');
        empty.classList.toggle('hidden', carriers.length > 0);
    };

    const renderLegalDocs = (data) => {
        const grid = document.getElementById('legalDocsGrid');
        const empty = document.getElementById('legalDocsEmpty');
        const counter = document.getElementById('legalDocCount');
        if (!grid || !empty) return;

        const docs = Array.isArray(data.legalDocuments)
            ? data.legalDocuments.filter((d) => d && d.imageUrl) : [];
        if (counter) counter.textContent = `${docs.length} tài liệu`;
        empty.classList.toggle('hidden', docs.length > 0);

        grid.innerHTML = docs.map((doc) => {
            const typeLabel = DOC_TYPE_LABELS[doc.type] || doc.type || 'Tài liệu';
            const title = doc.label || typeLabel;
            const url = doc.imageUrl;
            return `
                <article class="overflow-hidden rounded-xl border border-[#E5CBB5] bg-[#fbfaf8]">
                    <a href="${escapeHtml(url)}" target="_blank" rel="noopener" class="block h-44 w-full overflow-hidden bg-white">
                        <img src="${escapeHtml(url)}" alt="${escapeHtml(title)}" class="h-44 w-full object-contain" loading="lazy">
                    </a>
                    <div class="space-y-1 border-t border-[#E5CBB5] bg-white px-3 py-3">
                        <span class="inline-flex items-center rounded-full bg-[#FAF5E8] px-2.5 py-1 text-[10px] font-black uppercase tracking-wider text-[#5D4037]/80">${escapeHtml(typeLabel)}</span>
                        <p class="text-sm font-black text-[#5D4037]">${escapeHtml(title)}</p>
                        ${doc.note ? `<p class="text-xs font-bold text-[#5D4037]/70">${escapeHtml(doc.note)}</p>` : ''}
                        <p class="text-[11px] font-bold text-[#5D4037]/50">Tải lên: ${escapeHtml(formatDate(doc.uploadedAt, true))}</p>
                        <a href="${escapeHtml(url)}" target="_blank" rel="noopener" class="inline-flex text-xs font-black text-[#ea580c] hover:text-[#c2410c]">Mở ảnh gốc ↗</a>
                    </div>
                </article>`;
        }).join('');
    };

    const renderBizTypes = (data) => {
        const tbody = document.getElementById('bizTypeTableBody');
        if (!tbody) return;
        tbody.innerHTML = BUSINESS_TYPES.map((type) => {
            const matched = data.businessType === type.code;
            const rowClass = matched ? 'bg-[#FAF5E8]' : '';
            const labelClass = matched ? 'font-black text-[#ea580c]' : 'font-bold text-[#5D4037]';
            return `
                <tr class="${rowClass}">
                    <td class="px-4 py-3 ${labelClass}">${escapeHtml(type.label)}${matched ? ' <span class="ml-1 text-[10px] font-black uppercase">· đã khai báo</span>' : ''}</td>
                    <td class="px-4 py-3 font-bold text-[#5D4037]/80">${escapeHtml(type.docs)}</td>
                    <td class="px-4 py-3 font-bold text-[#5D4037]/60">${escapeHtml(type.note)}</td>
                </tr>`;
        }).join('');
    };

    const render = (data) => {
        clearError();
        renderHeader(data);
        renderShopInfo(data);
        renderSeller(data);
        renderIdentity(data);
        renderTax(data);
        renderCarriers(data);
        renderLegalDocs(data);
        renderBizTypes(data);
        els.detailContent?.classList.remove('hidden');
    };

    async function load() {
        if (loading) return;
        setLoading(true);
        clearError();
        try {
            const api = API();
            if (!api || !api.Admin || typeof api.Admin.getShopApplication !== 'function') {
                throw new Error('Không tìm thấy ApiService.Admin.getShopApplication.');
            }
            const data = await api.Admin.getShopApplication(shopId);
            render(data || {});
        } catch (error) {
            const status = error?.status;
            if (status === 404) {
                showError('Không tìm thấy hồ sơ gian hàng này (có thể đã bị xoá).');
            } else if (status === 401 || status === 403) {
                showError('Bạn cần đăng nhập bằng tài khoản ADMIN để xem hồ sơ này.');
            } else {
                showError(error?.message || 'Không tải được chi tiết hồ sơ gian hàng.');
            }
        } finally {
            setLoading(false);
        }
    }
})();
