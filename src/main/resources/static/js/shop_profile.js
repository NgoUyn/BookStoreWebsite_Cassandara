/**
 * Shop Profile Management Form
 * Handles loading, validating, and saving shop information
 */

const ShopProfileForm = (() => {
    const el = {
        form: document.getElementById('shop-form'),
        shopName: document.getElementById('shop-name'),
        slug: document.getElementById('shop-slug'),
        description: document.getElementById('shop-description'),
        logoUrl: document.getElementById('shop-logo'),
        bannerUrl: document.getElementById('shop-banner'),
        contactEmail: document.getElementById('shop-email'),
        contactPhone: document.getElementById('shop-phone'),
        address: document.getElementById('shop-address'),
        city: document.getElementById('shop-city'),
        province: document.getElementById('shop-province'),
        submitBtn: document.getElementById('submit-btn'),
        successAlert: document.getElementById('success-alert'),
        errorAlert: document.getElementById('error-alert'),
        errorMessage: document.getElementById('error-message'),
        approvalStatus: document.getElementById('approval-status'),
        logoImg: document.getElementById('logo-img'),
        bannerImg: document.getElementById('banner-img'),
        descCount: document.getElementById('desc-count'),
    };

    let existingShop = null;
    let isCreating = false;

    // Initialize form
    async function init() {
        try {
            // Load existing shop data
            const shop = await ApiService.SellerShop.getMyShop();
            if (shop) {
                populateForm(shop);
                existingShop = shop;
                isCreating = false;
            } else {
                // Create mode
                isCreating = true;
                document.querySelector('h1').textContent = 'Tạo Cửa Hàng Mới';
            }
        } catch (error) {
            console.error('Failed to load shop:', error);
            // If 404 error (shop not found), it's create mode
            if (error.status === 404) {
                isCreating = true;
                document.querySelector('h1').textContent = 'Tạo Cửa Hàng Mới';
            } else {
                showError('Không thể tải thông tin cửa hàng');
            }
        }

        // Setup event listeners
        setupEventListeners();
    }

    function populateForm(shop) {
        el.shopName.value = shop.shopName || '';
        el.slug.value = shop.slug || '';
        el.description.value = shop.description || '';
        el.logoUrl.value = shop.logoUrl || '';
        el.bannerUrl.value = shop.bannerUrl || '';
        el.contactEmail.value = shop.contactEmail || '';
        el.contactPhone.value = shop.contactPhone || '';
        el.address.value = shop.address || '';
        el.city.value = shop.city || '';
        el.province.value = shop.province || '';

        // Disable slug if editing (only allowed on create)
        if (existingShop) {
            el.slug.disabled = true;
            el.slug.classList.add('bg-gray-100', 'cursor-not-allowed');
        }

        // Update approval status display
        updateApprovalStatusDisplay(shop.approvalStatus);

        // Load preview images
        if (shop.logoUrl) {
            el.logoImg.src = shop.logoUrl;
            el.logoImg.classList.remove('hidden');
        }
        if (shop.bannerUrl) {
            el.bannerImg.src = shop.bannerUrl;
            el.bannerImg.classList.remove('hidden');
        }

        // Update description counter
        updateDescCounter();
    }

    function setupEventListeners() {
        el.form.addEventListener('submit', handleSubmit);
        el.description.addEventListener('input', updateDescCounter);
        el.logoUrl.addEventListener('change', () => updateImagePreview(el.logoUrl, el.logoImg));
        el.bannerUrl.addEventListener('change', () => updateImagePreview(el.bannerUrl, el.bannerImg));
    }

    function updateDescCounter() {
        const count = el.description.value.length;
        el.descCount.textContent = count;
        if (count > 500) {
            el.description.value = el.description.value.substring(0, 500);
            el.descCount.textContent = 500;
        }
    }

    function updateImagePreview(inputElement, imgElement) {
        const url = inputElement.value.trim();
        if (url) {
            imgElement.src = url;
            imgElement.classList.remove('hidden');
        } else {
            imgElement.classList.add('hidden');
        }
    }

    function updateApprovalStatusDisplay(status) {
        const statusMap = {
            'PENDING': { text: 'Chờ duyệt', color: 'bg-yellow-100 text-yellow-700' },
            'APPROVED': { text: 'Đã duyệt', color: 'bg-green-100 text-green-700' },
            'REJECTED': { text: 'Bị từ chối', color: 'bg-red-100 text-red-700' }
        };
        const statusInfo = statusMap[status] || statusMap['PENDING'];
        el.approvalStatus.textContent = statusInfo.text;
        el.approvalStatus.className = `text-sm font-bold px-3 py-1 rounded-full ${statusInfo.color}`;
    }

    async function handleSubmit(e) {
        e.preventDefault();

        // Validate form
        if (!el.form.checkValidity()) {
            el.form.reportValidity();
            return;
        }

        // Validate slug format (only create)
        if (isCreating && !validateSlug(el.slug.value)) {
            showError('Slug chỉ chứa chữ cái, số, và dấu gạch ngang');
            return;
        }

        // Disable submit button during request
        el.submitBtn.disabled = true;
        el.submitBtn.classList.add('opacity-50', 'cursor-not-allowed');

        try {
            const shopData = {
                shopName: el.shopName.value.trim(),
                slug: el.slug.value.trim(),
                description: el.description.value.trim(),
                logoUrl: el.logoUrl.value.trim(),
                bannerUrl: el.bannerUrl.value.trim(),
                contactEmail: el.contactEmail.value.trim(),
                contactPhone: el.contactPhone.value.trim(),
                address: el.address.value.trim(),
                city: el.city.value.trim(),
                province: el.province.value.trim(),
            };

            let response;
            if (isCreating) {
                response = await ApiService.SellerShop.create(shopData);
            } else {
                response = await ApiService.SellerShop.update(shopData);
            }

            // Update UI with response
            existingShop = response;
            populateForm(response);
            isCreating = false;

            // Show success message
            showSuccess();

            // Redirect after 2 seconds
            setTimeout(() => {
                window.location.href = '/seller/dashboard';
            }, 2000);

        } catch (error) {
            console.error('Error saving shop:', error);
            if (error.message) {
                showError(error.message);
            } else {
                showError(error.error || 'Lỗi khi lưu thông tin cửa hàng');
            }
        } finally {
            el.submitBtn.disabled = false;
            el.submitBtn.classList.remove('opacity-50', 'cursor-not-allowed');
        }
    }

    function validateSlug(slug) {
        // Only allow alphanumeric and hyphens
        return /^[a-z0-9-]+$/.test(slug) && slug.length > 0 && slug.length <= 50;
    }

    function showSuccess() {
        el.successAlert.classList.remove('hidden');
        el.errorAlert.classList.add('hidden');
        setTimeout(() => {
            el.successAlert.classList.add('hidden');
        }, 5000);
    }

    function showError(message) {
        el.errorMessage.textContent = message;
        el.errorAlert.classList.remove('hidden');
        el.successAlert.classList.add('hidden');
    }

    return {
        init
    };
})();

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    ShopProfileForm.init();
});
