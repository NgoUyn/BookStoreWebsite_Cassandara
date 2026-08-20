document.addEventListener('DOMContentLoaded', () => {
    if (!window.ApiService) {
        console.error('ApiService not available');
        return;
    }

    // ============================================================
    // 1. DOM ELEMENTS & STATE (GIỮ NGUYÊN BỘ LỌC CỦA BẠN)
    // ============================================================
    const booksGrid = document.getElementById('books-grid');
    const homeCategoryGrid = document.getElementById('home-category-grid');
    const sidebarCategoryList = document.getElementById('sidebar-category-list');
    const paginationNav = document.getElementById('books-pagination');
    const searchForm = document.getElementById('index-search-form');
    const searchInput = document.getElementById('index-search-input');
    const sortSelect = document.getElementById('sort-select');
    const priceMinInput = document.getElementById('price-min-input');
    const priceMaxInput = document.getElementById('price-max-input');
    const applyPriceFilterBtn = document.getElementById('apply-price-filter-btn');
    const publisherListEl = document.getElementById('publisher-filter-list');
    const ratingFilterList = document.getElementById('rating-filter-list');
    const flashSaleGrid = document.getElementById('flash-sale-grid');
    const bestSellerGrid = document.getElementById('index-best-sellers-grid');

    const state = {
        page: 0, size: 12, q: '', categoryId: null, minPrice: null, maxPrice: null, minRating: null, publishers: null, sort: 'latest'
    };

    // ============================================================
    // 2. HELPER FUNCTIONS (TRẢ LẠI ẢNH CHO AMAZON)
    // ============================================================
    const formatPriceVnd = (value) => {
        if (value === null || value === undefined || Number.isNaN(Number(value))) return 'Liên hệ';
        return new Intl.NumberFormat('vi-VN').format(Number(value)) + ' đ';
    };

    const escapeHtml = (text) => {
        const div = document.createElement('div');
        div.textContent = text ?? '';
        return div.innerHTML;
    };

    // Hàm này giữ lại ảnh thật của Amazon trong file CSV, chỉ thay ảnh lỗi placeholder
    const getSafeImage = (url) => {
        if (!url || url.trim() === '' || url.includes('placeholder.com')) {
            return '/images/no-cover.png';
        }
        return url;
    };

    // ============================================================
    // 3. WISHLIST LOGIC (PHỤC HỒI HOÀN TOÀN)
    // ============================================================
    const applyWishlistButtonState = (button, isSaved) => {
        if (!button) return;
        button.dataset.wishlistSaved = isSaved ? 'true' : 'false';
        button.setAttribute('aria-pressed', isSaved ? 'true' : 'false');
        button.setAttribute('title', isSaved ? 'Xóa khỏi wishlist' : 'Thêm vào wishlist');
        button.className = isSaved
            ? 'bg-red-500 text-white p-2.5 rounded-full shadow-md transition-colors w-10 h-10 flex items-center justify-center'
            : 'bg-white text-brand-dark p-2.5 rounded-full shadow-md hover:bg-brand-brown hover:text-white transition-colors w-10 h-10 flex items-center justify-center border border-gray-200';
        button.innerHTML = isSaved
            ? '<svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"></path></svg>'
            : '<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"></path></svg>';
    };

    const createWishlistButton = (book, isSaved) => {
        const title = escapeHtml(book.title || '');
        const author = escapeHtml(book.author || '');
        const imageUrl = escapeHtml(book.imageUrl || '');
        const categoryName = escapeHtml(book.category?.name || 'Sách');
        const btnClass = isSaved
            ? 'bg-red-500 text-white p-2.5 rounded-full shadow-md transition-colors w-10 h-10 flex items-center justify-center'
            : 'bg-white text-brand-dark p-2.5 rounded-full shadow-md hover:bg-brand-brown hover:text-white transition-colors w-10 h-10 flex items-center justify-center border border-gray-200';
        const svgIcon = isSaved
            ? '<svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"></path></svg>'
            : '<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"></path></svg>';

        return `
            <button type="button" title="${isSaved ? 'Xóa khỏi wishlist' : 'Thêm vào wishlist'}" aria-pressed="${isSaved}" data-toggle-wishlist="true" data-book-id="${book.id}" data-book-title="${title}" data-book-author="${author}" data-book-price="${book.price ?? ''}" data-book-image="${imageUrl}" data-book-category="${categoryName}" class="${btnClass}">
                ${svgIcon}
            </button>
        `;
    };

    const getWishlistBookPayload = (button) => ({
        id: Number(button.getAttribute('data-book-id')),
        title: button.getAttribute('data-book-title') || '',
        author: button.getAttribute('data-book-author') || '',
        price: Number(button.getAttribute('data-book-price') || 0),
        imageUrl: button.getAttribute('data-book-image') || '',
        categoryName: button.getAttribute('data-book-category') || 'Sách'
    });

    // ============================================================
    // 4. CATEGORY & PUBLISHER LOGIC (PHỤC HỒI HOÀN TOÀN)
    // ============================================================
    const loadCategories = async () => {
        try {
            const categories = await ApiService.Category.getAll();
            if (Array.isArray(categories) && categories.length > 0) {
                // Render Home Categories (Scroll ngang)
                if (homeCategoryGrid) {
                    homeCategoryGrid.innerHTML = categories.map((c) => `
                        <button type="button" data-category-id="${c.id}" class="home-category-btn snap-start min-w-[144px] h-36 bg-white border border-brand-border rounded-xl flex flex-col items-center justify-center text-brand-dark hover:border-brand-brown hover:text-brand-brown transition-all duration-300 shadow-sm group">
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10 mb-3 text-brand-brown group-hover:scale-110 transition-transform" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" /></svg>
                            <span class="font-bold text-sm">${escapeHtml(c.name)}</span>
                        </button>
                    `).join('');
                }
                // Render Sidebar Categories
                if (sidebarCategoryList) {
                    sidebarCategoryList.innerHTML = categories.map((c) => `
                        <li>
                            <button type="button" data-category-id="${c.id}" class="sidebar-category-btn w-full text-left flex items-center justify-between group">
                                <span class="group-hover:text-brand-brown transition-colors">${escapeHtml(c.name)}</span>
                            </button>
                        </li>
                    `).join('');
                }

                document.querySelectorAll('.home-category-btn, .sidebar-category-btn').forEach(btn => {
                    btn.addEventListener('click', () => {
                        state.categoryId = Number(btn.getAttribute('data-category-id'));
                        state.page = 0;
                        loadBooks();
                        document.getElementById('goi-y-cho-ban')?.scrollIntoView({ behavior: 'smooth' });
                    });
                });
            }
        } catch (error) {
            console.error('Load categories failed:', error);
        }
    };

    const loadPublishers = async () => {
        try {
            const resp = await ApiService.Book.search('', null, 0, 200);
            const books = Array.isArray(resp?.content) ? resp.content : [];
            // Lọc: chỉ giữ publisher là tên NXB thật (không phải URL ảnh)
            const isImageUrl = (val) => /^(https?:\/\/|data:image)/i.test(val) || /\.(jpg|jpeg|png|gif|webp|svg|bmp|tiff?)(\?|$)/i.test(val);
            const pubs = Array.from(new Set(books.map(b => b.publisher).filter(Boolean)))
                .filter(p => !isImageUrl(p) && p.length < 100) // Loại URL ảnh và chuỗi quá dài
                .slice(0, 10);
            if (publisherListEl && pubs.length > 0) {
                publisherListEl.innerHTML = pubs.map(p => `
                    <li>
                        <label class="flex items-center gap-3 cursor-pointer group">
                            <input type="checkbox" class="publisher-filter-checkbox custom-cb" value="${escapeHtml(p)}">
                            <span class="group-hover:text-brand-brown transition-colors">${escapeHtml(p)}</span>
                        </label>
                    </li>
                `).join('');
            }
        } catch (err) {
            console.warn('Không tải được danh sách NXB', err);
        }
    };

    // ============================================================
    // 5. HTML BUILDERS (CARD LỚN, CARD NHỎ, PHÂN TRANG)
    // ============================================================

    // Card Lớn cho Lưới sách Gợi ý (Có Wishlist)
    const buildCard = (book, index = 0) => {
        const isSaved = window.ApiService.Wishlist.isSaved(book.id);
        const wishlistBtnHtml = createWishlistButton(book, isSaved);
        const title = escapeHtml(book.title || 'Chưa có tên sách');
        const author = escapeHtml(book.author || 'Chưa có tác giả');
        const categoryName = escapeHtml(book.category?.name || 'Sách');
        const safeImg = getSafeImage(book.mediumImageUrl || book.imageUrl);
        const detailUrl = `/book/${book.id}`;

        const discountAmount = book.discountAmount || 0;
        const finalPrice = discountAmount > 0 ? (book.price * (100 - discountAmount) / 100) : book.price;

        const priceHtml = discountAmount > 0
            ? `<span class="text-[11px] text-gray-400 line-through">${formatPriceVnd(book.price)}</span>
               <span class="text-lg font-extrabold text-brand-dark">${formatPriceVnd(finalPrice)}</span>`
            : `<span class="text-lg font-extrabold text-brand-dark">${formatPriceVnd(book.price)}</span>`;

        const discountBadgeHtml = discountAmount > 0
            ? `<div class="absolute top-0 left-0 z-20 m-3 px-2.5 py-1 bg-brand-orange text-white text-[11px] font-bold uppercase tracking-wider rounded-full shadow-md">Giảm <span>${discountAmount}</span>%</div>`
            : '';

        return `
            <article class="product-card group relative bg-white rounded-xl shadow-soft transition-all duration-300 hover:shadow-hover h-full flex flex-col" data-aos="fade-up" data-aos-delay="${(index % 4) * 100}" style="perspective: 1000px;">
                <div class="product-card-inner relative w-full h-full rounded-xl transition-transform duration-500 ease-out group-hover:[transform:rotateY(var(--rotateX))_rotateX(var(--rotateY))] flex flex-col">
                    
                    ${discountBadgeHtml}
                    <div class="absolute top-0 right-0 z-20 m-3">
                        ${wishlistBtnHtml}
                    </div>

                    <div class="relative h-[280px] sm:h-[320px] w-full bg-gray-50 rounded-t-xl overflow-hidden p-4 flex items-center justify-center cursor-pointer main-link-trigger" data-url="${detailUrl}">
                        <img src="${safeImg}" onerror="this.src='/images/no-cover.png'" class="book-cover relative w-auto h-full object-contain drop-shadow-[0_15px_20px_rgba(74,59,50,0.2)] transition-all duration-500 ease-out group-hover:scale-110 group-hover:-translate-y-2">
                    </div>

                    <div class="p-4 flex flex-col flex-grow bg-white rounded-b-xl z-20">
                        <p class="text-xs text-gray-500 font-medium mb-1 line-clamp-1">${categoryName} - ${author}</p>
                        <h3 class="text-base font-bold text-brand-dark leading-snug line-clamp-2 h-12 mb-2 group-hover:text-brand-orange transition-colors cursor-pointer main-link-trigger" data-url="${detailUrl}">${title}</h3>

                        <div class="flex items-center gap-2 text-xs text-gray-500 mb-3">
                            <div class="flex items-center gap-0.5 text-yellow-400">
                                <svg class="w-3.5 h-3.5 fill-current" viewBox="0 0 20 20"><path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z"/></svg>
                            </div>
                            <span class="font-semibold text-gray-600">${book.averageRating ? book.averageRating.toFixed(1) : '5.0'}</span>
                        </div>

                        <div class="mt-auto pt-3 border-t border-gray-100">
                            <div class="flex items-center justify-between">
                                <div class="flex flex-col">
                                    ${priceHtml}
                                </div>
                                <div class="flex items-center gap-2 z-30">
                                    <button type="button" data-quick-view="true" data-book-id="${book.id}" class="w-10 h-10 flex items-center justify-center bg-white text-brand-dark border border-gray-200 rounded-full hover:bg-brand-brown hover:text-white transition-all shadow-sm">
                                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" /></svg>
                                    </button>
                                    <button type="button" data-add-to-cart="true" data-book-id="${book.id}" class="w-10 h-10 flex items-center justify-center bg-brand-dark text-white rounded-full hover:bg-brand-orange transition-all transform hover:scale-110">
                                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"></path></svg>
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </article>
        `;
    };

    // Card Nhỏ cho Flash Sale / Best Seller
    const buildSmallCard = (book, index, isTop = false) => {
        const finalPrice = book.discountAmount > 0 ? (book.price * (100 - book.discountAmount) / 100) : book.price;
        const safeImg = getSafeImage(book.imageUrl);

        if (isTop) {
            const badgeColor = index === 0 ? 'bg-yellow-400' : (index === 1 ? 'bg-gray-400' : (index === 2 ? 'bg-amber-700' : 'bg-brand-brown'));
            return `
            <div class="snap-start min-w-[300px] bg-white border border-gray-200 rounded-xl p-4 relative shadow-sm hover:shadow-lg transform hover:-translate-y-2 transition-all cursor-pointer group main-link-trigger" data-url="/book/${book.id}">
                <div class="absolute -top-3 -left-3 w-10 h-10 ${badgeColor} text-white rounded-full flex items-center justify-center font-black text-lg shadow-md border-2 border-white z-10">#${index + 1}</div>
                <div class="flex gap-4 h-full">
                    <div class="relative w-24 h-36 flex-shrink-0 rounded overflow-hidden">
                        <img src="${safeImg}" onerror="this.src='/images/no-cover.png'" class="w-full h-full object-cover group-hover:scale-110 transition-transform duration-300">
                        <div class="absolute inset-0 bg-black bg-opacity-40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity z-20">
                            <button type="button" data-quick-view="true" data-book-id="${book.id}" class="bg-white text-brand-dark p-2 rounded-full hover:bg-brand-orange hover:text-white transform hover:scale-110 transition shadow-md">
                               <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /><path d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" /></svg>
                            </button>
                        </div>
                    </div>
                    <div class="flex flex-col justify-center flex-1">
                        <h3 class="font-bold text-brand-dark leading-snug line-clamp-2 group-hover:text-brand-orange transition-colors">${book.title}</h3>
                        <p class="text-xs text-gray-500 mt-1">NXB: ${book.publisher || 'Chưa rõ'}</p>
                        <p class="text-brand-orange font-black text-lg mt-2">${formatPriceVnd(finalPrice)}</p>
                    </div>
                </div>
            </div>`;
        }

        return `
        <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-3 relative group cursor-pointer main-link-trigger" data-url="/book/${book.id}">
            <div class="absolute top-0 left-0 bg-red-500 text-white text-xs font-bold px-2 py-1 rounded-br-lg z-10">- ${book.discountAmount || 0}%</div>
            <div class="h-48 overflow-hidden rounded mb-3 relative">
                <img src="${safeImg}" onerror="this.src='/images/no-cover.png'" class="w-full h-full object-contain group-hover:scale-110 transition-transform duration-300">
                <div class="absolute inset-0 bg-black bg-opacity-40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity z-20">
                    <button type="button" data-quick-view="true" data-book-id="${book.id}" class="bg-white text-brand-dark p-2 rounded-full hover:bg-brand-orange hover:text-white transform hover:scale-110 transition shadow-md">
                       <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /><path d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" /></svg>
                    </button>
                </div>
            </div>
            <h3 class="font-bold text-sm line-clamp-2 mb-1">${book.title}</h3>
            <p class="text-brand-orange font-bold">${formatPriceVnd(finalPrice)}</p>
        </div>`;
    };

    // Phục hồi phân trang đẹp mắt
    const renderPagination = (currentPage, totalPages) => {
        if (!paginationNav) return;
        if (!totalPages || totalPages <= 1) { paginationNav.innerHTML = ''; return; }

        let html = '';
        const prevDisabled = currentPage === 0;

        html += `<button type="button" data-page="${Math.max(0, currentPage - 1)}" class="page-btn relative inline-flex items-center px-4 py-2 rounded-md text-sm font-medium ${prevDisabled ? 'text-gray-300 cursor-not-allowed' : 'text-gray-500 hover:bg-brand-bg transition-colors'}" ${prevDisabled ? 'disabled' : ''}>
                    <svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M12.707 5.293a1 1 0 010 1.414L9.414 10l3.293 3.293a1 1 0 01-1.414 1.414l-4-4a1 1 0 010-1.414l4-4a1 1 0 011.414 0z" clip-rule="evenodd" /></svg>
                 </button>`;

        const start = Math.max(0, currentPage - 2);
        const end = Math.min(totalPages - 1, currentPage + 2);

        if (start > 0) {
            html += `<button type="button" data-page="0" class="page-btn relative inline-flex items-center px-4 py-2 rounded-md text-sm font-medium text-brand-dark hover:bg-brand-bg mx-1">1</button>`;
            if (start > 1) html += `<span class="relative inline-flex items-center px-4 py-2 text-sm font-medium text-gray-400">...</span>`;
        }

        for (let i = start; i <= end; i++) {
            const active = i === currentPage;
            html += `<button type="button" data-page="${i}" class="page-btn relative inline-flex items-center px-4 py-2 rounded-md text-sm mx-1 ${active ? 'font-bold bg-brand-brown text-white' : 'font-medium text-brand-dark hover:bg-brand-bg transition-colors'}">${i + 1}</button>`;
        }

        if (end < totalPages - 1) {
            if (end < totalPages - 2) html += `<span class="relative inline-flex items-center px-4 py-2 text-sm font-medium text-gray-400">...</span>`;
            html += `<button type="button" data-page="${totalPages - 1}" class="page-btn relative inline-flex items-center px-4 py-2 rounded-md text-sm font-medium text-brand-dark hover:bg-brand-bg mx-1">${totalPages}</button>`;
        }

        const nextDisabled = currentPage >= totalPages - 1;
        html += `<button type="button" data-page="${Math.min(totalPages - 1, currentPage + 1)}" class="page-btn relative inline-flex items-center px-4 py-2 rounded-md text-sm font-medium ${nextDisabled ? 'text-gray-300 cursor-not-allowed' : 'text-gray-500 hover:bg-brand-bg transition-colors'}" ${nextDisabled ? 'disabled' : ''}>
                    <svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd" /></svg>
                 </button>`;

        paginationNav.innerHTML = html;
        paginationNav.querySelectorAll('.page-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const p = Number(btn.getAttribute('data-page'));
                if (!isNaN(p) && p !== state.page) {
                    state.page = p;
                    loadBooks();
                    document.getElementById('goi-y-cho-ban')?.scrollIntoView({ behavior: 'smooth' });
                }
            });
        });
    };

    // ============================================================
    // 6. API CALLS & RENDER (LOAD SÁCH GỢI Ý & HIGHLIGHTS)
    // ============================================================
    const loadBooks = async () => {
        if (!booksGrid) return;
        booksGrid.innerHTML = '<div class="col-span-full text-center py-10"><div class="animate-spin rounded-full h-10 w-10 border-4 border-brand-brown border-t-transparent mx-auto"></div></div>';
        try {
            const response = await ApiService.Book.search(state.q, state.categoryId, state.page, state.size, {
                minPrice: state.minPrice, maxPrice: state.maxPrice, sort: state.sort, minRating: state.minRating, publishers: state.publishers
            });
            const books = Array.isArray(response?.content) ? response.content : [];

            if (books.length === 0) {
                booksGrid.innerHTML = '<p class="col-span-full text-center py-10 text-gray-500 font-bold text-lg">Không tìm thấy sách phù hợp với bộ lọc.</p>';
                if(paginationNav) paginationNav.innerHTML = '';
                return;
            }

            booksGrid.innerHTML = books.map((b, i) => buildCard(b, i)).join('');
            if (paginationNav && response) renderPagination(state.page, response.totalPages || 0);

            // Xử lý hiệu ứng 3D nghiêng
            document.querySelectorAll('.product-card').forEach(card => {
                card.addEventListener('mousemove', (e) => {
                    const rect = card.getBoundingClientRect();
                    const x = e.clientX - rect.left; const y = e.clientY - rect.top;
                    const rotateX = ((y - rect.height / 2) / (rect.height / 2)) * -6;
                    const rotateY = ((x - rect.width / 2) / (rect.width / 2)) * 6;
                    card.style.setProperty('--rotateX', `${rotateY}deg`); card.style.setProperty('--rotateY', `${rotateX}deg`);
                });
                card.addEventListener('mouseleave', () => { card.style.setProperty('--rotateX', '0deg'); card.style.setProperty('--rotateY', '0deg'); });
            });

            if (typeof AOS !== 'undefined') setTimeout(() => AOS.refresh(), 100);
        } catch (error) {
            booksGrid.innerHTML = '<p class="col-span-full text-center text-red-500 py-10">Lỗi khi tải bộ lọc sách.</p>';
        }
    };

    const loadHomeHighlights = async () => {
        try {
            if (flashSaleGrid) {
                const fResp = await ApiService.Book.trending(5, 30).catch(() => ApiService.Book.search('', null, 0, 5, { sort: 'priceAsc' }));
                const fBooks = Array.isArray(fResp) ? fResp : (fResp?.content || []);
                flashSaleGrid.innerHTML = fBooks.length ? fBooks.slice(0,5).map((b, i) => buildSmallCard(b, i, false)).join('') : '<p class="col-span-full text-center py-4">Chưa có Flash Sale</p>';
            }
            if (bestSellerGrid) {
                const bResp = await ApiService.Book.bestSellers(10).catch(() => ApiService.Book.search('', null, 0, 10, { sort: 'latest' }));
                const bBooks = Array.isArray(bResp) ? bResp : (bResp?.content || []);
                bestSellerGrid.innerHTML = bBooks.length ? bBooks.slice(0,10).map((b, i) => buildSmallCard(b, i, true)).join('') : '<p class="w-full text-center py-4">Chưa có Best Seller</p>';
            }
        } catch (error) {
            if (bestSellerGrid) bestSellerGrid.innerHTML = '<p class="text-red-500 w-full text-center py-4">Lỗi tải dữ liệu.</p>';
            if (flashSaleGrid) flashSaleGrid.innerHTML = '<p class="text-red-500 col-span-full text-center">Lỗi tải dữ liệu.</p>';
        }
    };

    // ============================================================
    // 7. CÁC SỰ KIỆN TOÀN CỤC (CLICK, FILTER, MODAL, TIMER)
    // ============================================================

    // Tạo Modal Quick View
    // let modal = document.getElementById('quick-view-modal');
    // if (!modal) {
    //     modal = document.createElement('div');
    //     modal.id = 'quick-view-modal';
    //     modal.className = 'fixed inset-0 z-[100] flex items-center justify-center bg-black bg-opacity-60 backdrop-blur-sm transition-all duration-300 opacity-0 hidden';
    //     modal.innerHTML = `
    //         <div class="bg-white rounded-2xl shadow-2xl w-[95%] max-w-4xl max-h-[90vh] overflow-hidden flex flex-col md:flex-row relative transform scale-95 transition-transform duration-300">
    //             <button type="button" id="close-quick-view" class="absolute top-4 right-4 z-10 bg-gray-100 text-gray-600 hover:bg-red-500 hover:text-white rounded-full p-2.5 transition-colors shadow-sm">
    //                 <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
    //             </button>
    //             <div id="quick-view-content" class="w-full flex flex-col md:flex-row h-full overflow-y-auto"></div>
    //         </div>
    //     `;
    //     document.body.appendChild(modal);
    //
    //     const hideModal = () => {
    //         modal.classList.add('opacity-0'); modal.children[0].classList.add('scale-95');
    //         setTimeout(() => modal.classList.add('hidden'), 300);
    //     };
    //     document.getElementById('close-quick-view').addEventListener('click', hideModal);
    //     modal.addEventListener('click', (ev) => { if (ev.target === modal) hideModal(); });
    //     document.addEventListener('keydown', (ev) => { if (ev.key === 'Escape' && !modal.classList.contains('hidden')) hideModal(); });
    // }

    // Đổ dữ liệu vào Modal
    window.addEventListener('open-quick-view', async (e) => {
        const bookId = e.detail.bookId;
        const modalContent = document.getElementById('quick-view-content');

        modal.classList.remove('hidden');
        setTimeout(() => { modal.classList.remove('opacity-0'); modal.children[0].classList.remove('scale-95'); }, 10);
        modalContent.innerHTML = `<div class="w-full p-32 flex justify-center items-center"><div class="animate-spin rounded-full h-12 w-12 border-4 border-brand-brown border-t-transparent"></div></div>`;

        try {
            const book = await ApiService.Book.getById(bookId);
            const discountAmount = book.discountAmount || 0;
            const finalPrice = discountAmount > 0 ? (book.price * (100 - discountAmount) / 100) : book.price;
            const oldPriceHtml = discountAmount > 0 ? `<span class="text-sm text-gray-400 line-through mr-3">${formatPriceVnd(book.price)}</span>` : '';
            const safeImg = getSafeImage(book.imageUrl);

            modalContent.innerHTML = `
                <div class="w-full md:w-2/5 bg-gray-50 flex items-center justify-center p-8 border-r border-gray-100">
                    <img src="${safeImg}" onerror="this.src='/images/no-cover.png'" class="max-h-[400px] object-contain drop-shadow-xl rounded">
                </div>
                <div class="w-full md:w-3/5 p-8 flex flex-col">
                    <h2 class="text-3xl font-extrabold text-brand-dark mb-3">${escapeHtml(book.title || 'Chưa có tên')}</h2>
                    <p class="text-gray-500 mb-4">Tác giả: <span class="text-brand-dark">${escapeHtml(book.author || 'Chưa rõ')}</span></p>
                    <div class="flex items-center mb-6 bg-orange-50 p-4 rounded-xl border border-orange-100">
                        ${oldPriceHtml}<span class="text-4xl font-extrabold text-brand-orange">${formatPriceVnd(finalPrice)}</span>
                    </div>
                    <div class="mt-auto flex gap-4 pt-4">
                        <button type="button" data-add-to-cart="true" data-book-id="${book.id}" class="flex-1 bg-brand-dark text-white font-bold py-4 rounded-xl hover:bg-brand-orange transition shadow-md">Thêm vào giỏ</button>
                        <a href="/book/${book.id}" class="flex-1 text-center bg-white border border-gray-200 text-brand-dark font-bold py-4 rounded-xl hover:text-brand-brown transition shadow-sm">Xem chi tiết</a>
                    </div>
                </div>
            `;
        } catch (error) {
            modalContent.innerHTML = '<div class="w-full p-20 text-center text-red-500 font-bold">Lỗi tải dữ liệu!</div>';
        }
    });

    // Lắng nghe TẤT CẢ click (Fix triệt để lỗi đè link)
    document.addEventListener('click', function(event) {
        // 1. Quick View
        const quickViewBtn = event.target.closest('button[data-quick-view="true"]');
        if (quickViewBtn) {
            event.preventDefault(); event.stopImmediatePropagation();
            const bookId = quickViewBtn.getAttribute('data-book-id');
            if (bookId) {
                window.dispatchEvent(new CustomEvent('open-quick-view', { detail: { bookId: Number(bookId) } }));
            }
            return;
        }

        // 2. Add To Cart
        const addToCartBtn = event.target.closest('button[data-add-to-cart="true"]');
        if (addToCartBtn) {
            event.preventDefault(); event.stopImmediatePropagation();
            const bookId = addToCartBtn.getAttribute('data-book-id');
            const { userId, role } = ApiService.getAuth();
            if (!userId || role !== 'BUYER') {
                alert('Vui lòng đăng nhập tài khoản BUYER.');
                window.location.href = '/main/auth';
                return;
            }
            const origHtml = addToCartBtn.innerHTML;
            addToCartBtn.innerHTML = '...'; addToCartBtn.disabled = true;
            ApiService.Cart.addItem(userId, { bookId: Number(bookId), quantity: 1 })
                .then(() => alert('Đã thêm vào giỏ hàng!'))
                .catch(() => alert('Thêm thất bại!'))
                .finally(() => { addToCartBtn.innerHTML = origHtml; addToCartBtn.disabled = false; });
            return;
        }

        // 3. Wishlist
        const wishlistBtn = event.target.closest('button[data-toggle-wishlist="true"]');
        if (wishlistBtn) {
            event.preventDefault(); event.stopImmediatePropagation();
            const buyerId = ApiService.getAuth().userId;
            if (!buyerId || ApiService.getAuth().role !== 'BUYER') {
                alert('Vui lòng đăng nhập tài khoản BUYER để lưu sách.');
                window.location.href = '/main/auth';
                return;
            }
            (async () => {
                const book = getWishlistBookPayload(wishlistBtn);
                const result = await ApiService.Wishlist.toggle(book, buyerId);
                applyWishlistButtonState(wishlistBtn, result.saved);
            })();
            return;
        }

        // 4. Click chuyển trang (Cho thẻ sách)
        const mainLinkTrigger = event.target.closest('.main-link-trigger');
        if (mainLinkTrigger && !event.target.closest('button, a, input')) {
            const url = mainLinkTrigger.getAttribute('data-url');
            if (url) window.location.href = url;
        }
    });

    // Lắng nghe Form và Bộ lọc
    searchForm?.addEventListener('submit', (e) => { e.preventDefault(); state.q = searchInput?.value || ''; state.page = 0; loadBooks(); });
    sortSelect?.addEventListener('change', () => { state.sort = sortSelect.value; loadBooks(); });
    applyPriceFilterBtn?.addEventListener('click', () => {
        state.minPrice = Number(priceMinInput?.value) || null;
        state.maxPrice = Number(priceMaxInput?.value) || null;
        state.page = 0; loadBooks();
    });

    document.addEventListener('change', (e) => {
        if (e.target.matches('.publisher-filter-checkbox') || e.target.matches('.rating-filter-checkbox')) {
            const pubs = Array.from(document.querySelectorAll('.publisher-filter-checkbox:checked')).map(cb => cb.value);
            const rats = Array.from(document.querySelectorAll('.rating-filter-checkbox:checked')).map(cb => Number(cb.getAttribute('data-min-rating')));
            state.publishers = pubs.length ? pubs : null;
            state.minRating = rats.length ? Math.min(...rats) : null;
            state.page = 0; loadBooks();
        }
    });

    const startFlashSaleTimer = () => {
        const hEl = document.getElementById('fs-hours'), mEl = document.getElementById('fs-minutes'), sEl = document.getElementById('fs-seconds');
        if (!hEl || !mEl || !sEl) return;
        let end = new Date().getTime() + (4 * 3600 * 1000);
        setInterval(() => {
            let dist = end - new Date().getTime();
            if (dist < 0) return;
            let h = Math.floor((dist % (86400000)) / 3600000), m = Math.floor((dist % 3600000) / 60000), s = Math.floor((dist % 60000) / 1000);
            hEl.innerText = h < 10 ? "0"+h : h; mEl.innerText = m < 10 ? "0"+m : m; sEl.innerText = s < 10 ? "0"+s : s;
        }, 1000);
    };

    // ============================================================
    // 8. KHỞI CHẠY TẤT CẢ KHI LOAD TRANG
    // ============================================================
    (async () => {
        await ApiService.Wishlist.bootstrap().catch(() => {});
        loadCategories();
        loadPublishers();
        loadHomeHighlights();
        loadBooks();
        startFlashSaleTimer();
    })();

    if (typeof AOS === 'undefined') {
        const scriptAOS = document.createElement('script');
        scriptAOS.src = "https://unpkg.com/aos@2.3.1/dist/aos.js";
        scriptAOS.onload = () => AOS.init({ duration: 600, once: true, offset: 50 });
        document.body.appendChild(scriptAOS);
    }


    // ============================================================
    // CẬP NHẬT GIAO DIỆN HEADER DỰA TRÊN TRẠNG THÁI ĐĂNG NHẬP
    // ============================================================
    const setupHeaderAccountLink = () => {
        const accountLink = document.getElementById('header-account-link');
        const accountText = document.getElementById('header-account-text');

        // Dùng ApiService để kiểm tra xem có ai đang đăng nhập không
        if (accountLink && window.ApiService.isAuthenticated()) {
            const role = window.ApiService.getAuth().role;

            // Bẻ lái đường link tùy theo Role
            if (role === 'BUYER') {
                accountLink.href = '/buyer/dashboard'; // Trỏ về trang Profile tĩnh
                if (accountText) accountText.innerText = 'Hồ sơ';
            }
            else if (role === 'SELLER') {
                accountLink.href = '/seller/dashboard';
                if (accountText) accountText.innerText = 'Kênh người bán';
            }
            else if (role === 'ADMIN') {
                accountLink.href = '/admin';
                if (accountText) accountText.innerText = 'Quản trị';
            }
        }
    };

    // Chạy hàm này
    setupHeaderAccountLink();
});