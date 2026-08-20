document.addEventListener('DOMContentLoaded', async () => {
    // 1. Lấy Elements theo ID chuẩn
    const el = {
        title: document.getElementById('book-title'),
        author: document.getElementById('book-author'),
        categoryId: document.getElementById('book-category'),
        description: document.getElementById('book-description'),
        price: document.getElementById('book-price'),
        stock: document.getElementById('book-stock'),
        saveBtn: document.getElementById('btn-save-book'),
        pageTitle: document.getElementById('page-title'),
        skuDisplay: document.getElementById('sku-display'), // THÊM DẤU PHẨY Ở ĐÂY
        image: document.getElementById('book-image')
    };

    // MẠNG XỬ LÝ PREVIEW ẢNH KHI CHỌN FILE
    const imagePreview = document.getElementById('image-preview');
    const imagePlaceholder = document.getElementById('image-placeholder');

    if (el.image) {
        el.image.addEventListener('change', function(e) {
            const file = e.target.files[0];
            if (file) {
                const reader = new FileReader();
                reader.onload = function(e) {
                    if (imagePreview) {
                        imagePreview.src = e.target.result;
                        imagePreview.classList.remove('hidden');
                    }
                    if (imagePlaceholder) {
                        imagePlaceholder.classList.add('hidden');
                    }
                }
                reader.readAsDataURL(file);
            }
        });
    }

    const urlParams = new URLSearchParams(window.location.search);
    const bookId = urlParams.get('id');

    // 2. Load danh mục từ API
    async function initCategories() {
        try {
            const categories = await ApiService.Category.getAll();
            if (el.categoryId && categories.length > 0) {
                el.categoryId.innerHTML = '<option value="">-- Chọn danh mục --</option>' +
                    categories.map(c => `<option value="${c.id}">${c.name}</option>`).join('');
            }
        } catch (e) {
            console.error("❌ Không load được danh mục");
        }
    }

    // 3. Xử lý chế độ SỬA hoặc THÊM
    async function initForm() {
        await initCategories();

        if (bookId) {
            el.pageTitle.textContent = "Chỉnh sửa sách";
            el.skuDisplay.textContent = `Mã sách (ID): ${bookId}`;
            try {
                // Gọi API secure để lấy sách của seller hiện tại
                // Endpoint kiểm tra seller sở hữu sách trước khi trả về (IDOR Protection)
                const book = await ApiService.Book.getOwnBook(bookId);

                // Đổ data text vào
                el.title.value = book.title || '';
                el.author.value = book.author || '';
                el.price.value = book.price || 0;
                el.stock.value = book.stockQuantity || 0;
                el.categoryId.value = book.categoryId || '';
                el.description.value = book.description || '';

                // Đổ ảnh cũ vào thẻ <img> để preview (không được gán vào thẻ input file)
                if (book.imageUrl && imagePreview && imagePlaceholder) {
                    imagePreview.src = book.imageUrl;
                    imagePreview.classList.remove('hidden');
                    imagePlaceholder.classList.add('hidden');
                }
                
                console.log('✅ Đã load thông tin sách thành công');
                console.log('📦 Seller ID của sách:', book.seller?.id);
                
            } catch (error) {
                console.error('❌ Lỗi khi load sách:', error);
                const errorMsg = error.data?.error || error.message || 'Không lấy được thông tin sách';
                alert('❌ Lỗi: ' + errorMsg);
                window.location.href = '/seller/inventory';
            }
        } else {
            el.pageTitle.textContent = "Thêm sách mới";
            el.skuDisplay.textContent = "Hệ thống sẽ tự cấp ID";
        }
    }

    // 4. Sự kiện Lưu bằng JSON (Tạo sách) + FormData (Up ảnh)
        if (el.saveBtn) {
            el.saveBtn.addEventListener('click', async (e) => {
                e.preventDefault();

                // Validate cơ bản
                if (!el.title.value.trim() || !el.price.value || el.stock.value === '') {
                    alert("⚠️ Vui lòng nhập đủ: Tên sách, Giá và Tồn kho!");
                    return;
                }

                // Xử lý ảnh: Nếu là thêm mới mà không có ảnh thì chặn
                const imageFile = el.image.files[0];
                if (!bookId && !imageFile) {
                    alert("⚠️ Vui lòng chọn ảnh bìa cho sách mới!");
                    return;
                }

                const originalBtnText = el.saveBtn.textContent;
                el.saveBtn.textContent = "⌛ Đang xử lý...";
                el.saveBtn.disabled = true;

                try {
                    // BƯỚC 1: TẠO OBJECT JSON ĐỂ LƯU SÁCH
                    const bookData = {
                        title: el.title.value.trim(),
                        author: el.author.value.trim() || "Chưa rõ",
                        price: parseFloat(el.price.value),
                        stockQuantity: parseInt(el.stock.value),
                        description: el.description.value.trim()
                    };

                    // Xử lý categoryId (Tùy backend cài đặt, thường là gửi thẳng ID hoặc object)
                    if (el.categoryId.value) {
                        // Nếu Entity Book của bro map là @ManyToOne private Category category;
                        // bookData.category = { id: parseInt(el.categoryId.value) };
                        // Nếu map thẳng ID:
                        bookData.categoryId = parseInt(el.categoryId.value);
                    }

                    let savedBook;

                    // GỌI API LƯU/CẬP NHẬT THÔNG TIN SÁCH
                    if (bookId) {
                        savedBook = await ApiService.Book.update(bookId, bookData);
                        alert("✅ Cập nhật thông tin sách thành công!");
                    } else {
                        savedBook = await ApiService.Book.create(bookData);
                    }

                    // BƯỚC 2: NẾU CÓ CHỌN ẢNH THÌ GỌI API UPLOAD ẢNH RIÊNG
                    if (imageFile) {
                        // Lấy ID sách vừa tạo (hoặc ID sách đang sửa)
                        const targetBookId = bookId ? bookId : savedBook.id;

                        const fileFormData = new FormData();
                        fileFormData.append('file', imageFile);

                        await ApiService.Book.uploadCover(targetBookId, fileFormData);
                        if (!bookId) alert("✅ Thêm sách và tải ảnh bìa thành công!");
                    }

                    // Trở về trang quản lý kho
                    window.location.href = '/seller/inventory';

                } catch (err) {
                    alert("❌ Lỗi: " + (err.response?.data?.message || "Không thể lưu sách. Vui lòng thử lại!"));
                    console.error(err);
                } finally {
                    el.saveBtn.textContent = originalBtnText;
                    el.saveBtn.disabled = false;
                }
            });
        }

    initForm();
});