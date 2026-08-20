/**
 * 📋 ValidationUtils - Shared Validation Module
 * Dùng chung cho Buyer_Profile_Dashboard, Checkout_Page, Cart_Page
 * 
 * Các ràng buộc:
 * 1. Họ và Tên: 2-100 ký tự, chỉ chữ cái + khoảng trắng, tự động chuẩn hóa
 * 2. Ngày sinh: không tương lai, >= 1900, tùy chọn minAge
 * 3. Số điện thoại: 10 số, đầu 03/05/07/08/09
 */
var ValidationUtils = window.ValidationUtils || (() => {

    // ==========================================
    // 1. HỌ VÀ TÊN (Full Name)
    // ==========================================

    /**
     * Chuẩn hóa họ tên: trim, xóa khoảng trắng thừa
     */
    const sanitizeFullName = (value) => {
        if (!value || typeof value !== 'string') return '';
        return value.trim().replace(/\s+/g, ' ');
    };

    /**
     * Validate họ tên
     * @param {string} value - Giá trị cần validate
     * @param {string} fieldName - Tên hiển thị (VD: "Tên", "Họ", "Người nhận")
     * @returns {{ valid: boolean, sanitized?: string, error?: string }}
     */
    const validateFullName = (value, fieldName = 'Tên') => {
        const sanitized = sanitizeFullName(value || '');

        if (!sanitized) {
            return { valid: false, error: `${fieldName} không được để trống` };
        }
        if (sanitized.length < 2) {
            return { valid: false, error: `${fieldName} phải có ít nhất 2 ký tự` };
        }
        if (sanitized.length > 100) {
            return { valid: false, error: `${fieldName} không được vượt quá 100 ký tự` };
        }
        // Chỉ cho phép chữ cái (có dấu tiếng Việt) và khoảng trắng
        if (!/^[a-zA-ZÀÁÂÃÈÉÊÌÍÒÓÔÕÙÚĂĐĨŨƠàáâãèéêìíòóôõùúăđĩũơƯĂẠẢẤẦẨẪẬẮẰẲẴẶẸẺẼỀỀỂưăạảấầẩẫậắằẳẵặẹẻẽềềểỄỆỈỊỌỎỐỒỔỖỘỚỜỞỠỢỤỦỨỪễệỉịọỏốồổỗộớờởỡợụủứừỬỮỰỲỴÝỶỸửữựỳỵỷỹ\s]+$/.test(sanitized)) {
            return { valid: false, error: `${fieldName} chỉ được chứa chữ cái và khoảng trắng` };
        }

        return { valid: true, sanitized };
    };

    // ==========================================
    // 2. NGÀY THÁNG NĂM SINH (Date of Birth)
    // ==========================================

    /**
     * Validate ngày sinh
     * @param {string} value - Giá trị date string (YYYY-MM-DD)
     * @param {number} minAge - Tuổi tối thiểu (0 = không kiểm tra)
     * @returns {{ valid: boolean, error?: string }}
     */
    const validateDateOfBirth = (value, minAge = 0) => {
        if (!value) {
            return { valid: false, error: 'Ngày sinh không được để trống' };
        }

        const dob = new Date(value);
        const today = new Date();

        if (isNaN(dob.getTime())) {
            return { valid: false, error: 'Ngày sinh không hợp lệ' };
        }

        // Ngày sinh không được ở tương lai
        if (dob >= today) {
            return { valid: false, error: 'Ngày sinh không được ở tương lai' };
        }

        // Năm sinh không quá xa thực tế
        if (dob.getFullYear() < 1900) {
            return { valid: false, error: 'Năm sinh không hợp lệ (phải từ 1900 trở đi)' };
        }

        // Kiểm tra tuổi tối thiểu
        if (minAge > 0) {
            let age = today.getFullYear() - dob.getFullYear();
            const monthDiff = today.getMonth() - dob.getMonth();
            if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < dob.getDate())) {
                age--;
            }
            if (age < minAge) {
                return { valid: false, error: `Bạn phải đủ ${minAge} tuổi để thực hiện thao tác này` };
            }
        }

        return { valid: true };
    };

    // ==========================================
    // 3. SỐ ĐIỆN THOẠI (Phone Number)
    // ==========================================

    /**
     * Chuẩn hóa số điện thoại: chỉ giữ lại số
     */
    const normalizePhone = (value) => {
        if (!value || typeof value !== 'string') return '';
        return value.replace(/[^0-9]/g, '');
    };

    /**
     * Validate số điện thoại Việt Nam
     * @param {string} value
     * @returns {{ valid: boolean, normalized?: string, error?: string }}
     */
    const validatePhone = (value) => {
        const digits = normalizePhone(value || '');

        if (!digits) {
            return { valid: false, error: 'Số điện thoại không được để trống' };
        }
        if (digits.length !== 10) {
            return { valid: false, error: 'Số điện thoại phải có đúng 10 chữ số' };
        }
        if (!/^0[35789]/.test(digits)) {
            return { valid: false, error: 'Số điện thoại không đúng đầu số Việt Nam (03, 05, 07, 08, 09)' };
        }
        if (!/^0[35789][0-9]{8}$/.test(digits)) {
            return { valid: false, error: 'Số điện thoại không hợp lệ' };
        }

        return { valid: true, normalized: digits };
    };

    // ==========================================
    // 4. KIỂM TRA TÍNH TOÀN VẸN SỐ ĐIỆN THOẠI (Phone Uniqueness)
    // ==========================================

    /**
     * Kiểm tra số điện thoại không trùng với user khác (async)
     * Gọi API backend: GET /api/auth/check-phone?phone=...
     * 
     * @param {string} phone - Số điện thoại (đã normalize 10 số)
     * @param {string|number} [excludeUserId] - ID user cần loại trừ (khi edit profile của chính mình)
     * @param {function} [apiCall] - Hàm fetch tuỳ chỉnh (cho testing), mặc định gọi /api/auth/check-phone
     * @returns {Promise<{ valid: boolean, error?: string }>}
     */
    const validatePhoneUnique = async (phone, excludeUserId = null, apiCall = null) => {
        if (!phone) {
            return { valid: false, error: 'Số điện thoại không được để trống' };
        }

        try {
            const fetchFn = apiCall || window.fetch;
            const res = await fetchFn(`/api/auth/check-phone?phone=${encodeURIComponent(phone)}`);
            if (!res.ok) {
                // Nếu API lỗi, fail-open để không chặn user
                console.warn('validatePhoneUnique: API không khả dụng, bỏ qua kiểm tra uniqueness');
                return { valid: true };
            }
            const data = await res.json();

            if (data.exists) {
                // Nếu có excludeUserId, kiểm tra xem phone này có thuộc về user đó không
                // Backend hiện tại chưa hỗ trợ exclude, nên nếu exists thì báo lỗi
                return { valid: false, error: 'Số điện thoại đã được sử dụng bởi người dùng khác' };
            }

            return { valid: true };
        } catch (error) {
            console.warn('validatePhoneUnique: Lỗi mạng, bỏ qua kiểm tra uniqueness:', error);
            return { valid: true }; // fail-open
        }
    };

    // ==========================================
    // 5. UI HELPERS (Hiển thị / Xóa lỗi)
    // ==========================================

    /**
     * Hiển thị lỗi cho một field
     * @param {string} elementId - ID của input field
     * @param {string} message - Thông báo lỗi
     */
    const showError = (elementId, message) => {
        const input = document.getElementById(elementId);
        const errorEl = document.getElementById(elementId + '-error');

        if (input) {
            input.classList.add('error', 'border-red-500', 'bg-red-50');
        }
        if (errorEl) {
            errorEl.textContent = message;
            errorEl.classList.remove('hidden');
        }
    };

    /**
     * Xóa lỗi cho một field
     * @param {string} elementId - ID của input field
     */
    const clearError = (elementId) => {
        const input = document.getElementById(elementId);
        const errorEl = document.getElementById(elementId + '-error');

        if (input) {
            input.classList.remove('error', 'border-red-500', 'bg-red-50');
        }
        if (errorEl) {
            errorEl.textContent = '';
            errorEl.classList.add('hidden');
        }
    };

    /**
     * Xóa tất cả lỗi trên trang
     */
    const clearAllErrors = () => {
        document.querySelectorAll('.field-error').forEach(el => {
            el.textContent = '';
            el.classList.add('hidden');
        });
        document.querySelectorAll('.input-field.error, .floating-input.error').forEach(el => {
            el.classList.remove('error', 'border-red-500', 'bg-red-50');
        });
    };

    /**
     * Lọc chỉ giữ số khi nhập điện thoại (dùng cho oninput)
     */
    const filterPhoneInput = (inputElement) => {
        if (!inputElement) return;
        inputElement.value = inputElement.value.replace(/[^0-9]/g, '');
    };

    // ==========================================
    // PUBLIC API
    // ==========================================

    return {
        sanitizeFullName,
        validateFullName,
        validateDateOfBirth,
        normalizePhone,
        validatePhone,
        validatePhoneUnique,
        showError,
        clearError,
        clearAllErrors,
        filterPhoneInput,
    };
})();

window.ValidationUtils = ValidationUtils;
