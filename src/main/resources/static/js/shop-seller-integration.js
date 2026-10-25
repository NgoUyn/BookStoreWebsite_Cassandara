/**
 * 🏬 Shop Seller Management Integration
 * File: shop-seller-integration.js
 * 
 * Để sử dụng, thêm vào Shop_Seller.html:
 * <script src="/js/api-service.js"></script>
 * <script src="/js/shop-seller-integration.js"></script>
 */

(function () {
    // ==========================================
    // 1. FORM ELEMENTS REFERENCES
    // ==========================================

const shopNameInput =
    document.getElementById('shopName') ||
    document.getElementById('shop-name');

const shopDescriptionInput =
    document.getElementById('description') ||
    document.getElementById('shop-description');

const shopPhoneInput =
    document.getElementById('contactPhone') ||
    document.getElementById('shop-phone');
    const shopStatusDisplay = document.getElementById('shop-status') || 
                             document.querySelector('[data-shop-status]');
    const shopSlugDisplay = document.getElementById('shop-slug') || 
                           document.querySelector('[data-shop-slug]');
    const shopProductCount = document.getElementById('shop-product-count') || 
                            document.querySelector('[data-shop-product-count]');

const saveShopBtn =
    document.getElementById('shop-panel-save') ||
    document.getElementById('save-shop-btn') ||
    document.querySelector('button[data-action="save-shop"]');
    const editShopBtn = document.getElementById('edit-shop-btn') || 
                       document.querySelector('button[data-action="edit-shop"]');
    const cancelEditBtn = document.getElementById('cancel-edit-btn') || 
                         document.querySelector('button[data-action="cancel-edit"]');

    // ==========================================
    // 3. STATE MANAGEMENT
    // ==========================================

    let currentShopData = null;
    let isEditMode = false;

    // ==========================================
    // 4. UTILITY FUNCTIONS
    // ==========================================

    const setEditMode = (mode) => {
    isEditMode = mode;

    // Bật/tắt các ô nhập
    if (shopNameInput) shopNameInput.disabled = false;
    if (shopDescriptionInput) shopDescriptionInput.disabled = false;
    if (shopPhoneInput) shopPhoneInput.disabled = false;

    // Hiển thị các nút
if (saveShopBtn) {
    saveShopBtn.style.display = 'inline-block';
    saveShopBtn.disabled = false;
}

    if (editShopBtn) {
        editShopBtn.style.display = mode ? 'none' : 'inline-block';
    }

    if (cancelEditBtn) {
        cancelEditBtn.style.display = mode ? 'inline-block' : 'none';
    }

    // Cập nhật giao diện input
    const enabledClasses = [
        'opacity-100',
        'bg-white',
        'cursor-text'
    ];

    const disabledClasses = [
        'opacity-60',
        'bg-gray-100',
        'cursor-not-allowed'
    ];

    [shopNameInput, shopDescriptionInput, shopPhoneInput].forEach(el => {
        if (!el) return;

        el.classList.remove(
            ...enabledClasses,
            ...disabledClasses
        );

        el.classList.add(
            ...enabledClasses
        );
    });
};

    const showSpinner = (element) => {
        if (!element) return;
        const originalText = element.textContent;
        element.innerHTML = '⏳ Đang xử lý...';
        element.disabled = true;
        return () => {
            element.textContent = originalText;
            element.disabled = false;
        };
    };

    // ==========================================
    // 5. FETCH & RENDER FUNCTIONS
    // ==========================================

    const loadProductCount = async () => {
        try {
            // Gọi API với size=1 chỉ để lấy totalElements (tổng số sản phẩm)
            const response = await ApiService.Book.getSellerBooks('', null, 0, 1);
            const total = response?.totalElements ?? response?.total ?? 0;
            if (shopProductCount) {
                shopProductCount.textContent = total.toLocaleString('vi-VN');
            }
            console.log('✓ Số lượng sản phẩm:', total);
        } catch (error) {
            console.error('❌ Lỗi tải số lượng sản phẩm:', error);
            // Log thêm thông tin chi tiết để debug
            if (error.data) {
                console.error('   Response data:', error.data);
            }
            if (error.status) {
                console.error('   HTTP status:', error.status);
            }
            // Giữ nguyên giá trị mặc định từ Thymeleaf (không ghi đè)
        }
    };

    const loadShopInfo = async () => {
        try {
            const shop = await ApiService.SellerShop.getMyShop();
            currentShopData = shop;

            // Populate form
            if (shopNameInput) shopNameInput.value = shop.shopName || '';
            if (shopDescriptionInput) shopDescriptionInput.value = shop.description || '';
            if (shopPhoneInput) shopPhoneInput.value = shop.phoneNumber || '';
            const setValue = (id, value) => {
    const element = document.getElementById(id);
    if (element) {
        element.value = value ?? '';
    }
};

setValue('slug', shop.slug);
setValue('contactEmail', shop.contactEmail);
setValue('address', shop.address);
setValue('city', shop.city);
setValue('province', shop.province);
setValue('logoUrl', shop.logoUrl);
setValue('bannerUrl', shop.bannerUrl);
            if (shopStatusDisplay) shopStatusDisplay.textContent = shop.status || 'N/A';
            if (shopSlugDisplay) shopSlugDisplay.textContent = shop.slug || 'N/A';

            setEditMode(false);
            console.log('✓ Thông tin shop tải thành công');

            // Tải số lượng sản phẩm sau khi có thông tin shop
            await loadProductCount();

            // Dispatch event để các script khác (panel form) nhận dữ liệu
            window.dispatchEvent(new CustomEvent('shop:loaded', { detail: shop }));

            return shop;

        } catch (error) {
            console.error('❌ Lỗi tải shop:', error);
            if (error.status === 404) {
                // Shop chưa tồn tại, cho phép tạo mới
                alert('Bạn chưa có shop. Hãy tạo shop mới.');
                setEditMode(true);
                currentShopData = null;
            } else {
                alert('❌ Lỗi tải thông tin shop');
            }
            // Dispatch event với lỗi để panel form biết
            window.dispatchEvent(new CustomEvent('shop:loaded', { detail: null }));
            return null;
        }
    };

    // ==========================================
    // 6. ACTION HANDLERS
    // ==========================================

    window.enterEditMode = () => {
        setEditMode(true);
    };

    window.cancelEdit = () => {
        if (currentShopData) {
            // Restore original values
            if (shopNameInput) shopNameInput.value = currentShopData.shopName || '';
            if (shopDescriptionInput) shopDescriptionInput.value = currentShopData.description || '';
            if (shopPhoneInput) shopPhoneInput.value = currentShopData.phoneNumber || '';
        }
        setEditMode(false);
    };

    window.saveShop = async () => {
    const shopName = (shopNameInput?.value || '').trim();
    const description = (shopDescriptionInput?.value || '').trim();
    const phoneNumber = (shopPhoneInput?.value || '').trim();

    if (!shopName) {
        alert('Tên cửa hàng không được để trống');
        shopNameInput?.focus();
        return;
    }

    if (shopName.length < 3) {
        alert('Tên cửa hàng phải có ít nhất 3 ký tự');
        return;
    }

    if (phoneNumber && !/^\d{10,11}$/.test(phoneNumber)) {
        alert('Số điện thoại không hợp lệ');
        return;
    }

    let revert = null;

    try {
        revert = showSpinner(saveShopBtn);

        const shopData = {
            shopName,
            slug: document.getElementById('slug')?.value?.trim(),
            description,
            phoneNumber,
            contactEmail: document.getElementById('contactEmail')?.value?.trim(),
            address: document.getElementById('address')?.value?.trim(),
            city: document.getElementById('city')?.value?.trim(),
            province: document.getElementById('province')?.value?.trim(),
            logoUrl: document.getElementById('logoUrl')?.value || null,
            bannerUrl: document.getElementById('bannerUrl')?.value || null
        };

        console.log('Dữ liệu cập nhật cửa hàng:', shopData);

        let response;

        if (currentShopData) {
            response = await ApiService.SellerShop.update(shopData);
        } else {
            response = await ApiService.SellerShop.create(shopData);
        }

        currentShopData = response;

        alert('Cập nhật thông tin cửa hàng thành công!');

        await loadShopInfo();

    } catch (error) {
        console.error('Lỗi lưu thông tin cửa hàng:', error);

        alert(
            'Không thể lưu thông tin cửa hàng: ' +
            (error.message || 'Lỗi không xác định')
        );
    } finally {
        if (typeof revert === 'function') {
            revert();
        }
    }
};

    // ==========================================
    // 7. EVENT LISTENERS
    // ==========================================

    if (editShopBtn) {
        editShopBtn.addEventListener('click', window.enterEditMode);
    }

    if (cancelEditBtn) {
        cancelEditBtn.addEventListener('click', window.cancelEdit);
    }

const shopPanelForm = document.getElementById('shop-panel-form');

if (shopPanelForm) {
    shopPanelForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        await window.saveShop();
    });
} else if (saveShopBtn) {
    saveShopBtn.addEventListener('click', async (event) => {
        event.preventDefault();
        await window.saveShop();
    });
}

    // Allow Enter key to save
    [shopNameInput, shopDescriptionInput, shopPhoneInput].forEach(el => {
        if (el) {
            el.addEventListener('keypress', (e) => {
                if (e.key === 'Enter' && !el.matches('textarea') && !shopPanelForm) {
                    e.preventDefault();
                    window.saveShop();
                }
            });
        }
    });

    // ==========================================
    // 8. INITIALIZATION
    // ==========================================

    console.log('🏬 Shop Seller Management loaded');
    loadShopInfo();

})();
