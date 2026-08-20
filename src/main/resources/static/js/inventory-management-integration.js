/**
 * 📦 Inventory Management Integration
 * File: inventory-management-integration.js
 * * Để sử dụng, thêm vào Inventory_Management.html:
 * <script src="/js/api-service.js"></script>
 * <script src="/js/inventory-management-integration.js"></script>
 */

(function () {
    // ==========================================
    // 1. CHECK AUTHENTICATION
    // ==========================================

    if (!ApiService.isAuthenticated() || !ApiService.isSeller()) {
        alert('Chỉ seller mới có quyền truy cập');
        window.location.href = '/';
        return;
    }

    // ==========================================
    // 2. ELEMENT REFERENCES
    // ==========================================

    const booksTableEl = document.getElementById('inventory-books-table') ||
                        document.querySelector('tbody[data-books-list]');
    const addBookBtn = document.getElementById('add-book-btn') ||
                      document.querySelector('button[data-action="add-book"]');
    const addBookForm = document.getElementById('add-book-form') ||
                       document.querySelector('form[data-form="add-book"]');
    const searchBooksInput = document.getElementById('inv-q');

    // Form inputs
    const formInputs = {
        title: document.getElementById('book-title') || document.querySelector('input[name="title"]'),
        author: document.getElementById('book-author') || document.querySelector('input[name="author"]'),
        price: document.getElementById('book-price') || document.querySelector('input[name="price"]'),
        stock: document.getElementById('book-stock') || document.querySelector('input[name="stock"]'),
        categoryId: document.getElementById('book-category') || document.querySelector('select[name="categoryId"]'),
        description: document.getElementById('book-description') || document.querySelector('textarea[name="description"]'),
        // THÊM: Bắt thẻ input file ảnh
        image: document.getElementById('book-image') || document.querySelector('input[type="file"]')
    };

    // ==========================================
    // 3. STATE MANAGEMENT
    // ==========================================

    let allBooks = [];
    let currentEditingBookId = null;
    let categories = [];

    // ==========================================
    // 4. UTILITY FUNCTIONS
    // ==========================================

    const formatDate = (dateString) => {
        if (!dateString) return '-';
        return new Date(dateString).toLocaleDateString('vi-VN');
    };

    const formatCurrency = (price) => {
        // Fallback nếu ApiService chưa có hàm formatVND
        return ApiService.formatVND ? ApiService.formatVND(price) : new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price);
    };

    const getStatusBadge = (status) => {
        const badges = {
            'APPROVED': '<span class="bg-green-100 text-green-800 px-3 py-1 rounded-full text-xs font-bold">✓ Duyệt</span>',
            'PENDING': '<span class="bg-yellow-100 text-yellow-800 px-3 py-1 rounded-full text-xs font-bold">⏳ Chờ</span>',
            'REJECTED': '<span class="bg-red-100 text-red-800 px-3 py-1 rounded-full text-xs font-bold">✗ Từ chối</span>',
        };
        return badges[status] || `<span class="bg-gray-100 text-gray-800 px-3 py-1 rounded-full text-xs font-bold">${status}</span>`;
    };

    const getStockStatus = (stock) => {
        if (stock === 0) return '<span class="text-red-600 font-bold">❌ Hết hàng</span>';
        if (stock < 10) return '<span class="text-orange-600 font-bold">⚠️ Sắp hết</span>';
        return '<span class="text-green-600 font-bold">✓ Còn hàng</span>';
    };

    const resetForm = () => {
        if (addBookForm) {
            addBookForm.reset();
        } else {
            Object.values(formInputs).forEach(el => {
                if (el) {
                    if (el.type === 'file') el.value = ''; // Reset input file an toàn
                    else el.value = '';
                }
            });
        }
        currentEditingBookId = null;

        const submitBtn = addBookForm?.querySelector('button[type="submit"]') ||
                         document.querySelector('button[data-action="submit-book"]');
        if (submitBtn) {
            submitBtn.textContent = '➕ Thêm sách';
        }
    };

    const showSpinner = (element) => {
        if (!element) return () => {};
        const originalText = element.textContent;
        element.innerHTML = '⏳ Xử lý...';
        element.disabled = true;
        return () => {
            element.textContent = originalText;
            element.disabled = false;
        };
    };

    // ==========================================
    // 5. FETCH & RENDER FUNCTIONS
    // ==========================================

    const loadCategories = async () => {
        try {
            const cats = await ApiService.Category.getAll();
            categories = Array.isArray(cats) ? cats : [];

            if (formInputs.categoryId && categories.length > 0) {
                formInputs.categoryId.innerHTML = `
                    <option value="">-- Chọn danh mục --</option>
                    ${categories.map(c => `<option value="${c.id}">${c.name}</option>`).join('')}
                `;
            }
        } catch (error) {
            console.error('❌ Lỗi tải danh mục:', error);
        }
    };

    const loadBooks = async (query = '') => {
        try {
            // Use new API endpoint for seller's books
            // This returns books with ALL statuses (PENDING, APPROVED, REJECTED)
            const result = await ApiService.Book.getSellerBooks(query, null, 0, 500);
            // Handle cả paginated response (result.content) và array response
            allBooks = result.content || result || [];
            console.log(`✅ Loaded ${allBooks.length} books from server for seller`);
            renderBooksTable(allBooks);
        } catch (error) {
            console.error('❌ Lỗi tải danh sách sách:', error);
            allBooks = [];
            renderBooksTable([]);
        }
    };

    const renderBooksTable = (books) => {
        if (!booksTableEl) return;

        if (books.length === 0) {
            booksTableEl.innerHTML = `<tr><td colspan="8" class="px-4 py-6 text-center text-gray-500 font-bold">Chưa có sách nào trong kho</td></tr>`;
            return;
        }

        booksTableEl.innerHTML = books.map(book => `
            <tr class="border-b hover:bg-gray-50 transition">
                <td class="px-4 py-3">
                    <div class="font-bold text-brand-dark line-clamp-2">${book.title}</div>
                    <div class="text-xs text-gray-500">ID: ${book.id}</div>
                </td>
                <td class="px-4 py-3">${book.author || '-'}</td>
                <td class="px-4 py-3 text-sm">${book.category?.name || 'N/A'}</td>
                <td class="px-4 py-3 text-right font-bold text-brand-orange">
                    ${formatCurrency(book.price || 0)}
                </td>
                <td class="px-4 py-3 text-center">
                    <div class="font-bold text-lg text-brand-dark">${book.stockQuantity || 0}</div>
                    <div class="text-xs">${getStockStatus(book.stockQuantity)}</div>
                </td>
                <td class="px-4 py-3">
                    ${getStatusBadge(book.approvalStatus)}
                </td>
                <td class="px-4 py-3 text-xs text-gray-500">
                    ${formatDate(book.createdAt)}
                </td>
                <td class="px-4 py-3">
                    <div class="flex gap-2 justify-end">
                        <button
                            onclick="window.editBook(${book.id})"
                            class="px-3 py-1 bg-blue-100 text-blue-700 rounded hover:bg-blue-200 transition font-bold text-sm"
                        >
                            ✏️ Sửa
                        </button>
                        <button
                            onclick="window.deleteBook(${book.id})"
                            class="px-3 py-1 bg-red-100 text-red-700 rounded hover:bg-red-200 transition font-bold text-sm"
                        >
                            🗑️ Xóa
                        </button>
                    </div>
                </td>
            </tr>
        `).join('');
    };

    // ==========================================
    // 6. ACTION HANDLERS
    // ==========================================

    window.editBook = (bookId) => {
        window.location.href = `/seller/book/form?id=${bookId}`;
    };

    window.deleteBook = async (bookId) => {
        if (!confirm('⚠️ Bạn chắc chắn muốn xóa sách này?')) {
            return;
        }

        try {
            await ApiService.Book.delete(bookId);
            alert('✓ Xóa sách thành công');
            await loadBooks();
        } catch (error) {
            alert('❌ Lỗi xóa sách: ' + (error.message || 'Thử lại sau'));
            console.error(error);
        }
    };

    // ==========================================
    // 7. FORM SUBMISSION (UPDATE FORMDATA)
    // ==========================================

    const handleFormSubmit = async (e) => {
        e.preventDefault();

        // Validate cơ bản
        const title = (formInputs.title?.value || '').trim();
        const author = (formInputs.author?.value || '').trim();
        const price = parseFloat(formInputs.price?.value || 0);
        const stock = parseInt(formInputs.stock?.value || 0);
        const categoryId = parseInt(formInputs.categoryId?.value || 0);
        const description = (formInputs.description?.value || '').trim();

        if (!title) { alert('❌ Tiêu đề sách không được để trống'); return; }
        if (!author) { alert('❌ Tác giả sách không được để trống'); return; }
        if (price <= 0) { alert('❌ Giá sách phải lớn hơn 0'); return; }

        const submitBtn = e.target.querySelector('button[type="submit"]');
        const revert = showSpinner(submitBtn);

        try {
                    // BƯỚC 1: GÓI DATA TEXT THÀNH JSON CHUẨN
                    const bookData = {
                        title: title,
                        author: author,
                        price: price,
                        stockQuantity: stock, // Đổi 'stock' thành 'stockQuantity' cho khớp model Book.java
                        categoryId: categoryId > 0 ? categoryId : null,
                        description: description
                    };

                    let savedBookId = currentEditingBookId;

                    // Xử lý tạo mới / cập nhật (CHỈ GỬI JSON)
                    if (currentEditingBookId) {
                        await ApiService.Book.update(currentEditingBookId, bookData);
                    } else {
                        const response = await ApiService.Book.create(bookData); // API này phải gọi vào /api/books/seller
                        savedBookId = response.id; // Lấy ID sách vừa tạo
                    }

                    // BƯỚC 2: UPLOAD ẢNH NẾU CÓ CHỌN FILE
                    const imageFile = formInputs.image?.files[0];
                    if (imageFile && savedBookId) {
                        const formData = new FormData();
                        formData.append('file', imageFile);

                        // Cần thêm hàm này vào ApiService của bro (gọi POST tới /api/books/seller/{id}/upload-cover)
                        await ApiService.Book.uploadCover(savedBookId, formData);
                    }

                    alert('✓ Lưu sách thành công. Sách cần duyệt trước khi hiển thị.');
                    resetForm();
                    await loadBooks(); // Bảng sẽ load lại bình thường vì Backend đã fix lỗi Auth

        } catch (error) {
            const msg = error.response?.data?.message || error.message || 'Thử lại sau';
            alert('❌ Lỗi: ' + msg);
            console.error('📍 Error details:', error);
        } finally {
            revert();
        }
    };

    if (addBookForm) {
        addBookForm.addEventListener('submit', handleFormSubmit);
    }

    // ==========================================
    // 8. SEARCH FUNCTIONALITY
    // ==========================================

    if (searchBooksInput) {
        let searchTimeout;
        searchBooksInput.addEventListener('input', (e) => {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                loadBooks(e.target.value.trim());
            }, 300);
        });
    }

    // ==========================================
    // 9. INITIALIZATION
    // ==========================================

    console.log('📦 Inventory Management loaded (Hacker Mũ Hồng Edition)');
    Promise.all([loadCategories(), loadBooks()]);

})();