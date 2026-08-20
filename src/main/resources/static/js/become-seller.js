(function(){
    const form = document.getElementById('becomeSellerForm');
    const submitBtn = document.getElementById('submitBtn');
    const cancelBtn = document.getElementById('cancelBtn');

    const getValues = () => ({
        shopName: document.getElementById('shopName').value.trim(),
        slug: document.getElementById('slug').value.trim() || undefined,
        address: document.getElementById('address').value.trim() || undefined,
        city: document.getElementById('city').value.trim() || undefined,
        province: document.getElementById('province')?.value?.trim() || undefined,
        contactEmail: document.getElementById('contactEmail').value.trim() || undefined,
        contactPhone: document.getElementById('contactPhone').value.trim() || undefined
    });

    // Prefill when editing/resubmitting
    const prefillFromSession = () => {
        try {
            const data = sessionStorage.getItem('sellerApplicationDraft');
            if (!data) return;
            const obj = JSON.parse(data);
            if (obj.shopName) document.getElementById('shopName').value = obj.shopName;
            if (obj.slug) document.getElementById('slug').value = obj.slug;
            if (obj.address) document.getElementById('address').value = obj.address;
            if (obj.contactEmail) document.getElementById('contactEmail').value = obj.contactEmail;
            if (obj.contactPhone) document.getElementById('contactPhone').value = obj.contactPhone;
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