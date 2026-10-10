(function(){
    const form = document.getElementById('becomeSellerForm');
    const submitBtn = document.getElementById('submitBtn');
    const cancelBtn = document.getElementById('cancelBtn');

    // Đơn vị vận chuyển gợi ý (giữ đồng bộ với danh sách trong admin-shop-detail.js)
    const CARRIERS = ['GHN', 'GHTK', 'Viettel Post', 'VNPost', 'J&T Express', 'Ninja Van'];

    // Danh sách ảnh tài liệu pháp lý đã upload: [{ type, label, imageUrl }]
    const legalDocs = [];

    const val = (id) => document.getElementById(id)?.value?.trim() || undefined;

    const escapeHtml = (value) => value == null ? '' : String(value)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');

    const getValues = () => ({
        shopName: val('shopName'),
        slug: val('slug'),
        description: val('description'),
        address: val('address'),
        city: val('city'),
        province: val('province'),
        contactEmail: val('contactEmail'),
        contactPhone: val('contactPhone'),
        businessType: val('businessType'),
        representativeName: val('representativeName'),
        identityType: val('identityType'),
        identityNumber: val('identityNumber'),
        taxCode: val('taxCode'),
        shippingCarriers: Array.from(document.querySelectorAll('#carrierGroup input[type=checkbox]:checked'))
            .map((input) => input.value),
        legalDocuments: legalDocs.map((doc) => ({ ...doc }))
    });

    // ==========================================
    // ĐƠN VỊ VẬN CHUYỂN (checkbox dạng chip)
    // ==========================================
    const renderCarriers = () => {
        const group = document.getElementById('carrierGroup');
        if (!group) return;
        group.innerHTML = CARRIERS.map((name, idx) => `
            <label class="chip${idx === 0 ? ' on' : ''}">
                <input type="checkbox" value="${escapeHtml(name)}" ${idx === 0 ? 'checked' : ''}>
                <span>${escapeHtml(name)}</span>
            </label>`).join('');
        group.querySelectorAll('input[type=checkbox]').forEach((input) => {
            input.addEventListener('change', () => {
                input.closest('.chip')?.classList.toggle('on', input.checked);
            });
        });
    };

    // ==========================================
    // ẢNH TÀI LIỆU PHÁP LÝ (upload ngay khi chọn)
    // ==========================================
    const DOC_TYPE_LABELS = {
        IDENTITY: 'Giấy tờ tuỳ thân',
        BUSINESS_REGISTRATION: 'Đăng ký kinh doanh',
        TAX: 'Thuế / MST',
        SPECIAL_LICENSE: 'Giấy phép chuyên ngành',
        BRAND_DOC: 'Tài liệu thương hiệu'
    };

    const renderLegalDocs = () => {
        const list = document.getElementById('legalDocsList');
        const empty = document.getElementById('legalDocsEmpty');
        if (!list || !empty) return;
        empty.style.display = legalDocs.length ? 'none' : 'block';
        list.innerHTML = legalDocs.map((doc, index) => `
            <div class="doc-row">
                <img class="doc-thumb" src="${escapeHtml(doc.imageUrl)}" alt="${escapeHtml(doc.label || doc.type)}">
                <div style="flex:1; min-width:0">
                    <div style="font-weight:700">${escapeHtml(doc.label || DOC_TYPE_LABELS[doc.type] || doc.type)}</div>
                    <span class="doc-tag">${escapeHtml(DOC_TYPE_LABELS[doc.type] || doc.type)}</span>
                </div>
                <button type="button" class="btn btn-danger" data-index="${index}">Xoá</button>
            </div>`).join('');
        list.querySelectorAll('button[data-index]').forEach((btn) => {
            btn.addEventListener('click', () => {
                legalDocs.splice(Number(btn.dataset.index), 1);
                renderLegalDocs();
            });
        });
    };

    const setUploadingDoc = (busy) => {
        const btn = document.getElementById('docChooseBtn');
        const fileName = document.getElementById('docFileName');
        if (btn) {
            btn.disabled = busy;
            btn.textContent = busy ? 'Đang tải lên…' : 'Chọn ảnh tài liệu';
        }
        if (fileName && !busy) fileName.textContent = 'Chưa chọn ảnh';
    };

    const uploadDocument = async (file) => {
        if (!file) return;
        const allowed = ['image/jpeg', 'image/png', 'image/webp'];
        if (!allowed.includes(file.type)) {
            Swal.fire({ icon: 'warning', title: 'Sai định dạng', text: 'Chỉ nhận ảnh JPG, PNG hoặc WEBP.' });
            document.getElementById('docFileName').textContent = 'Chưa chọn ảnh';
            return;
        }
        if (file.size > 5 * 1024 * 1024) {
            Swal.fire({ icon: 'warning', title: 'Ảnh quá lớn', text: 'Mỗi ảnh tối đa 5MB.' });
            document.getElementById('docFileName').textContent = 'Chưa chọn ảnh';
            return;
        }

        const type = document.getElementById('docType').value;
        const label = document.getElementById('docLabel').value.trim() || DOC_TYPE_LABELS[type] || 'Tài liệu';
        try {
            setUploadingDoc(true);
            const url = await ApiService.Auth.uploadLegalDocument(file, type);
            legalDocs.push({ type, label, imageUrl: url });
            document.getElementById('docLabel').value = '';
            renderLegalDocs();
        } catch (err) {
            Swal.fire({ icon: 'error', title: 'Không tải được tài liệu', text: err?.message || 'Vui lòng thử lại.' });
        } finally {
            setUploadingDoc(false);
        }
    };

    const bindLegalDocs = () => {
        const chooseBtn = document.getElementById('docChooseBtn');
        const fileInput = document.getElementById('docFile');
        if (!chooseBtn || !fileInput) return;
        chooseBtn.addEventListener('click', () => fileInput.click());
        fileInput.addEventListener('change', async () => {
            const file = fileInput.files && fileInput.files[0];
            const fileName = document.getElementById('docFileName');
            if (file && fileName) fileName.textContent = file.name;
            fileInput.value = '';
            await uploadDocument(file);
        });
    };

    renderCarriers();
    renderLegalDocs();
    bindLegalDocs();

    // Prefill when editing/resubmitting
    const PREFILL_FIELDS = ['shopName', 'slug', 'description', 'address', 'city', 'province',
        'contactEmail', 'contactPhone', 'businessType', 'representativeName', 'identityType',
        'identityNumber', 'taxCode'];

    const prefillFromSession = () => {
        try {
            const data = sessionStorage.getItem('sellerApplicationDraft');
            if (!data) return;
            const obj = JSON.parse(data);
            PREFILL_FIELDS.forEach((field) => {
                const el = document.getElementById(field);
                if (el && obj[field]) el.value = obj[field];
            });

            // Đơn vị vận chuyển
            if (Array.isArray(obj.shippingCarriers)) {
                document.querySelectorAll('#carrierGroup input[type=checkbox]').forEach((input) => {
                    input.checked = obj.shippingCarriers.includes(input.value);
                    input.closest('.chip')?.classList.toggle('on', input.checked);
                });
            }

            // Tài liệu pháp lý đã upload trước đó (khi người dùng bấm gửi lần 2)
            if (Array.isArray(obj.legalDocuments) && obj.legalDocuments.length) {
                obj.legalDocuments.forEach((doc) => {
                    if (doc && doc.imageUrl) {
                        legalDocs.push({ type: doc.type, label: doc.label, imageUrl: doc.imageUrl });
                    }
                });
                renderLegalDocs();
            }
        } catch (e) { console.warn('prefill error', e); }
    };

    // Save draft before submit (useful for resubmit flow)
    const saveDraft = (payload) => {
        try { sessionStorage.setItem('sellerApplicationDraft', JSON.stringify(payload)); } catch (e) {}
    };

    const setSubmitting = (isSubmitting) => {
        submitBtn.disabled = isSubmitting;
        submitBtn.textContent = isSubmitting ? 'Đang gửi…' : 'Gửi yêu cầu';
    };

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const payload = getValues();
        if (!payload.shopName) {
            Swal.fire({icon:'warning', title:'Thiếu thông tin', text: 'Vui lòng nhập tên cửa hàng.'});
            return;
        }
        if (!payload.city) {
            Swal.fire({icon:'warning', title:'Thiếu thông tin', text: 'Vui lòng nhập Tỉnh / Thành phố.'});
            return;
        }
        if (!payload.legalDocuments.length) {
            const confirm = await Swal.fire({
                icon: 'warning',
                title: 'Chưa có tài liệu pháp lý',
                text: 'Hồ sơ không có ảnh tài liệu sẽ mất nhiều thời gian duyệt hơn. Bạn vẫn muốn gửi?',
                showCancelButton: true,
                confirmButtonText: 'Vẫn gửi',
                cancelButtonText: 'Bổ sung tài liệu'
            });
            if (!confirm.isConfirmed) return;
        }

        try {
            setSubmitting(true);
            saveDraft(payload);
            const res = await ApiService.Auth.becomeSeller(payload);
            if (res && res.status === 202) {
                await Swal.fire({icon:'success', title:'Đã gửi', text: res.message || 'Yêu cầu của bạn đã được gửi.'});
                // Redirect to seller dashboard or profile
                window.location.href = '/';
                return;
            }

            // If backend returned normal object, show it
            await Swal.fire({icon:'success', title:'Thành công', text: 'Yêu cầu đã được gửi.'});
            window.location.href = '/';
        } catch (err) {
            const msg = err?.message || 'Không thể gửi yêu cầu. Vui lòng thử lại.';
            Swal.fire({icon:'error', title:'Lỗi', text: msg});
        } finally {
            setSubmitting(false);
        }
    });

    cancelBtn.addEventListener('click', () => {
        window.history.back();
    });

    // run prefill on load
    document.addEventListener('DOMContentLoaded', prefillFromSession);
})();