document.addEventListener('DOMContentLoaded', () => {
    if (!window.ApiService) {
        console.error('ApiService not available');
        return;
    }

    const searchForm = document.getElementById('search-result-search-form');
    const searchInput = document.getElementById('search-keyword-input');
    const suggestionPanel = document.getElementById('search-suggestion-panel');
    const resultsGrid = document.getElementById('search-results-grid');
    const paginationContainer = document.getElementById('search-pagination');
    const resultTotal = document.getElementById('search-result-total');
    const resultQuery = document.getElementById('search-result-query');
    const resultSummary = document.getElementById('search-result-summary');
    const resultRange = document.getElementById('search-result-range');
    const sortSelect = document.getElementById('search-sort-select');
    const authorInput = document.getElementById('search-author-input');
    const minPriceInput = document.getElementById('search-min-price-input');
    const maxPriceInput = document.getElementById('search-max-price-input');
    const ratingSlider = document.getElementById('search-rating-slider');
    const ratingValue = document.getElementById('search-rating-value');
    const inStockToggle = document.getElementById('search-instock-toggle');
    const yearFromInput = document.getElementById('search-year-from-input');
    const yearToInput = document.getElementById('search-year-to-input');
    const categoryList = document.getElementById('search-category-list');
    const sellerList = document.getElementById('search-seller-list');
    const applyFiltersBtn = document.getElementById('search-apply-filters-btn');
    const resetFiltersBtn = document.getElementById('search-reset-filters-btn');
    const bestSellersContainer = document.getElementById('search-best-sellers');
    const trendingContainer = document.getElementById('search-trending');

    if (!resultsGrid) {
        return;
    }

    const defaultSize = 12;
    const url = new URL(window.location.href);

    const state = {
        q: url.searchParams.get('q') || '',
        categoryIds: (() => {
            // Handle both single categoryId from discovery page and multiple from filters
            const ids = new Set(url.searchParams.getAll('categoryIds[]'));
            const singleId = url.searchParams.get('categoryId');
            if (singleId) {
                ids.add(singleId);
            }
            return Array.from(ids);
        })(),
        sellerIds: url.searchParams.getAll('sellerIds[]') || [],
        author: url.searchParams.get('author') || '',
        minPrice: url.searchParams.get('minPrice') || '',
        maxPrice: url.searchParams.get('maxPrice') || '',
        minRating: url.searchParams.get('minRating') || 0,
        inStock: url.searchParams.get('inStock') === 'true',
        publishYearFrom: url.searchParams.get('publishYearFrom') || '',
        publishYearTo: url.searchParams.get('publishYearTo') || '',
        sort: url.searchParams.get('sort') || 'latest',
        page: Number(url.searchParams.get('page') || 0),
        size: Number(url.searchParams.get('size') || defaultSize)
    };

    const categoryLabels = new Map();
    const sellerLabels = new Map();

    const formatVND = (price) => {
        return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price || 0);
    };

    const escapeHtml = (text) => {
        const div = document.createElement('div');
        div.textContent = text ?? '';
        return div.innerHTML;
    };

    const applyWishlistButtonState = (button, isSaved) => {
        if (!button) {
            return;
        }

        button.dataset.wishlistSaved = isSaved ? 'true' : 'false';
        button.setAttribute('aria-pressed', isSaved ? 'true' : 'false');
        button.setAttribute('title', isSaved ? 'Xoa khoi wishlist' : 'Them vao wishlist');
        button.className = isSaved
            ? 'w-10 h-10 bg-red-500 text-white rounded-full shadow-lg flex items-center justify-center transition-colors'
            : 'w-10 h-10 bg-white text-brand-dark rounded-full shadow-lg flex items-center justify-center hover:bg-brand-biscuit hover:text-white transition-colors';
        button.innerHTML = isSaved
            ? '<svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"></path></svg>'
            : '<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"></path></svg>';
    };

    const getWishlistBookPayload = (button) => ({
        id: Number(button.getAttribute('data-book-id')),
        title: button.getAttribute('data-book-title') || '',
        author: button.getAttribute('data-book-author') || '',
        price: Number(button.getAttribute('data-book-price') || 0),
        imageUrl: button.getAttribute('data-book-image') || '',
        categoryName: button.getAttribute('data-book-category') || 'Sach'
    });

    const ensureBuyer = (message) => {
        const { userId, role } = ApiService.getAuth();
        if (!userId || role !== 'BUYER') {
            alert(message);
            window.location.href = '/main/auth';
            return null;
        }
        return userId;
    };

    const addToCart = async (bookId, quantity = 1) => {
        const userId = ensureBuyer('Vui long dang nhap tai khoan BUYER de them vao gio hang.');
        if (!userId) {
            return;
        }

        try {
            await ApiService.Cart.addItem(userId, {
                bookId: Number(bookId),
                quantity
            });
            alert('Da them san pham vao gio hang.');
        } catch (error) {
            alert(error?.message || 'Them vao gio hang that bai.');
        }
    };

    const renderCompactBooks = (container, books, theme = 'light') => {
        if (!container) {
            return;
        }

        if (!books || books.length === 0) {
            container.innerHTML = `<div class="rounded-2xl border border-dashed ${theme === 'dark' ? 'border-white/20 text-white/70 bg-white/5' : 'border-brand-accent text-brand-dark/60 bg-brand-cream/40'} px-4 py-5 text-sm italic">Chua co du lieu phu hop.</div>`;
            return;
        }

        container.innerHTML = books.slice(0, 4).map((book, index) => {
            const badgeClass = theme === 'dark' ? 'bg-white/10 text-brand-peach' : 'bg-brand-cream text-brand-biscuit';
            const textClass = theme === 'dark' ? 'text-white' : 'text-brand-dark';
            const mutedClass = theme === 'dark' ? 'text-white/70' : 'text-gray-500';
            return `
                <a href="/book/${book.id}" class="flex items-center gap-4 rounded-2xl ${theme === 'dark' ? 'bg-white/5 hover:bg-white/10' : 'bg-brand-cream/60 hover:bg-brand-cream'} p-3 transition-colors group">
                    <div class="w-12 h-16 rounded-xl ${badgeClass} flex items-center justify-center font-black text-sm shrink-0">${index + 1}</div>
                    <div class="min-w-0 flex-1">
                        <h4 class="font-bold ${textClass} line-clamp-2 group-hover:underline">${escapeHtml(book.title || '')}</h4>
                        <p class="text-xs ${mutedClass} mt-1 line-clamp-1">${escapeHtml(book.author || 'Dang cap nhat')}</p>
                    </div>
                    <div class="font-black ${theme === 'dark' ? 'text-brand-peach' : 'text-brand-orange'} text-sm whitespace-nowrap">${formatVND(book.price)}</div>
                </a>
            `;
        }).join('');
    };

    const renderCheckboxList = (container, items, stateIds, type, nameMap) => {
        if (!container) return;
        if (!Array.isArray(items) || items.length === 0) {
            container.innerHTML = `<div class="px-3 py-2 text-xs text-gray-500">Không có lựa chọn.</div>`;
            return;
        }

        container.innerHTML = items.map(item => {
            const id = String(item.id);
            const name = escapeHtml(item.name || item.shopName || 'N/A');
            nameMap.set(id, name);
            return `
                <label class="flex items-center gap-3 p-2 rounded-lg hover:bg-gray-50 cursor-pointer">
                    <input type="checkbox" value="${id}" data-filter-type="${type}" class="h-4 w-4 rounded border-gray-300 text-brand-brown focus:ring-brand-brown" ${stateIds.includes(id) ? 'checked' : ''}>
                    <span class="text-sm font-medium text-gray-700">${name}</span>
                </label>
            `;
        }).join('');
    };

    const loadSellers = async () => {
        if (!sellerList) return;
        sellerList.innerHTML = `<div class="px-3 py-2 text-xs text-gray-500">Đang tải...</div>`;
        try {
            // Assuming an API endpoint to get all sellers/shops exists
            const sellers = await ApiService.Shop.getAll();
            renderCheckboxList(sellerList, sellers, state.sellerIds, 'seller', sellerLabels);
        } catch (error) {
            console.error('Load sellers failed:', error);
            sellerList.innerHTML = `<div class="px-3 py-2 text-xs text-red-500">Lỗi tải nhà bán.</div>`;
        }
    };

    const loadCategories = async () => {
        if (!categoryList) {
            return;
        }
        categoryList.innerHTML = `<div class="rounded-lg border border-dashed border-brand-border bg-brand-cream/30 px-3 py-2 text-xs text-gray-500">Đang tải danh mục...</div>`;

        try {
            const categories = await ApiService.Category.getAll();
            renderCheckboxList(categoryList, categories, state.categoryIds, 'category', categoryLabels);
        } catch (error) {
            console.error('Load categories failed:', error);
            categoryList.innerHTML = `<div class="rounded-lg border border-dashed border-red-200 bg-red-50 px-3 py-2 text-xs text-red-600">Không tải được danh mục.</div>`;
        }
    };

    const createWishlistButton = (book) => {
        const isSaved = ApiService.Wishlist.isSaved(book.id);
        const title = escapeHtml(book.title || 'Chua co ten sach');
        const author = escapeHtml(book.author || 'Chua co tac gia');
        const imageUrl = escapeHtml(book.imageUrl || '');
        const categoryName = escapeHtml(book.category?.name || 'Sach');

        return `
            <button type="button" data-toggle-wishlist="true" data-book-id="${book.id}" data-book-title="${title}" data-book-author="${author}" data-book-price="${book.price ?? ''}" data-book-image="${imageUrl}" data-book-category="${categoryName}" aria-pressed="${isSaved ? 'true' : 'false'}" title="${isSaved ? 'Xoa khoi wishlist' : 'Them vao wishlist'}" class="${isSaved ? 'w-10 h-10 bg-red-500 text-white rounded-full shadow-lg flex items-center justify-center transition-colors' : 'w-10 h-10 bg-white text-brand-dark rounded-full shadow-lg flex items-center justify-center hover:bg-brand-biscuit hover:text-white transition-colors'}">
                ${isSaved ? '<svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"></path></svg>' : '<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"></path></svg>'}
            </button>
        `;
    };

    const buildCard = (book) => {
        const coverUrl = book.imageUrl ? book.imageUrl : 'https://via.placeholder.com/400x560?text=No+Cover';
        return `
            <article class="bg-white rounded-[1.75rem] p-4 border border-brand-accent shadow-sm hover:shadow-lg transition-all group h-full flex flex-col" data-detail-url="/book/${book.id}">
                <a href="/book/${book.id}" class="block relative overflow-hidden rounded-[1.25rem] border border-brand-accent bg-brand-cream mb-4">
                    <div class="aspect-3/4 overflow-hidden">
                        <img src="${coverUrl}" alt="${escapeHtml(book.title || '')}" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" onerror="this.src='https://via.placeholder.com/400x560?text=Error'" />
                    </div>
                    <!-- Quick View Button -->
                    <div class="absolute inset-0 bg-black bg-opacity-0 group-hover:bg-opacity-30 transition-all duration-300 flex items-center justify-center opacity-0 group-hover:opacity-100">
                        <button onclick="event.preventDefault(); event.stopPropagation(); openQuickView(${book.id})" class="bg-white text-brand-dark p-3 rounded-full shadow-lg transform hover:scale-110 transition-transform" title="Xem nhanh">
                            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path></svg>
                        </button>
                    </div>
                </a>
                <div class="flex flex-col flex-1">
                    <p class="text-[10px] uppercase tracking-[0.22em] text-gray-400 font-black mb-1 line-clamp-1">${escapeHtml(book.author || 'Dang cap nhat')}</p>
                    <a href="/book/${book.id}" class="font-black text-brand-dark text-sm line-clamp-2 group-hover:text-brand-brown transition leading-snug mb-2">${escapeHtml(book.title || '')}</a>
                    <div class="mt-auto flex items-center justify-between gap-3">
                        <div>
                            <div class="text-brand-orange font-black text-lg">${formatVND(book.price)}</div>
                            <div class="text-[11px] text-gray-400">${escapeHtml(book.publishYear || 'Nam xb dang cap nhat')}</div>
                        </div>
                        <div class="flex items-center gap-2">
                            ${createWishlistButton(book)}
                            <button type="button" data-add-to-cart="true" data-book-id="${book.id}" class="w-10 h-10 bg-brand-biscuit text-white rounded-full shadow-lg flex items-center justify-center hover:bg-brand-dark transition-colors">
                                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"></path></svg>
                            </button>
                        </div>
                    </div>
                </div>
            </article>
        `;
    };

    const renderLoading = () => {
        resultsGrid.innerHTML = Array.from({ length: 8 }).map(() => `
            <div class="bg-white rounded-[1.75rem] p-4 border border-brand-accent shadow-sm animate-pulse">
                <div class="aspect-3/4 bg-brand-cream rounded-[1.25rem] mb-4"></div>
                <div class="h-3 bg-gray-200 rounded w-1/2 mb-2"></div>
                <div class="h-4 bg-gray-200 rounded w-4/5 mb-3"></div>
                <div class="h-5 bg-gray-200 rounded w-1/3"></div>
            </div>
        `).join('');
        if (paginationContainer) {
            paginationContainer.innerHTML = '';
        }
    };

    const renderEmpty = (message = 'Khong tim thay sach phu hop.') => {
        resultsGrid.innerHTML = `
            <div class="col-span-full bg-white rounded-[1.75rem] border border-brand-border p-10 text-center shadow-sm">
                <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-brand-cream flex items-center justify-center text-brand-biscuit">
                    <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"></path></svg>
                </div>
                <h3 class="text-lg font-black text-brand-dark mb-2">${message}</h3>
                <p class="text-sm text-gray-500 mb-6">Thu dieu chinh bo loc hoac thay doi tu khoa tim kiem.</p>
                <button id="search-empty-reset" type="button" class="bg-brand-biscuit text-white font-bold px-6 py-2.5 rounded-full hover:bg-brand-dark transition-colors">Xoa bo loc</button>
            </div>
        `;
        document.getElementById('search-empty-reset')?.addEventListener('click', resetFilters);
        if (paginationContainer) {
            paginationContainer.innerHTML = '';
        }
    };

    const renderError = (message = 'Loi ket noi may chu. Vui long thu lai.') => {
        resultsGrid.innerHTML = `
            <div class="col-span-full bg-red-50 border border-red-200 text-red-700 rounded-[1.75rem] px-6 py-6 text-center">
                <div class="font-bold mb-2">${message}</div>
                <button id="search-error-retry" type="button" class="mt-2 bg-red-600 text-white px-5 py-2 rounded-full font-bold hover:bg-red-700 transition-colors">Thu lai</button>
            </div>
        `;
        document.getElementById('search-error-retry')?.addEventListener('click', loadBooks);
        if (paginationContainer) {
            paginationContainer.innerHTML = '';
        }
    };

    const renderPagination = (current, total) => {
        if (!paginationContainer) {
            return;
        }

        if (!total || total <= 1) {
            paginationContainer.innerHTML = '';
            return;
        }

        let html = '';
        html += `<button type="button" data-page="${Math.max(0, current - 1)}" class="page-btn relative inline-flex items-center px-4 py-2 rounded-full text-sm font-medium text-gray-500 bg-white border border-brand-border hover:border-brand-brown hover:text-brand-brown transition-colors ${current === 0 ? 'opacity-50 cursor-not-allowed' : ''}" ${current === 0 ? 'disabled' : ''}>Prev</button>`;

        const start = Math.max(0, current - 2);
        const end = Math.min(total - 1, current + 2);
        for (let i = start; i <= end; i++) {
            const active = i === current;
            html += `<button type="button" data-page="${i}" class="page-btn relative inline-flex items-center px-4 py-2 rounded-full text-sm mx-1 border ${active ? 'font-black bg-brand-brown text-white border-brand-brown' : 'font-medium text-brand-dark bg-white border-brand-border hover:border-brand-brown transition-colors'}">${i + 1}</button>`;
        }

        html += `<button type="button" data-page="${Math.min(total - 1, current + 1)}" class="page-btn relative inline-flex items-center px-4 py-2 rounded-full text-sm font-medium text-gray-500 bg-white border border-brand-border hover:border-brand-brown hover:text-brand-brown transition-colors ${current >= total - 1 ? 'opacity-50 cursor-not-allowed' : ''}" ${current >= total - 1 ? 'disabled' : ''}>Next</button>`;
        paginationContainer.innerHTML = html;

        paginationContainer.querySelectorAll('.page-btn').forEach((btn) => {
            btn.addEventListener('click', () => {
                const nextPage = Number(btn.getAttribute('data-page'));
                if (!Number.isNaN(nextPage) && nextPage !== state.page) {
                    state.page = nextPage;
                    syncUrl();
                    loadBooks();
                }
            });
        });
    };

    const syncUrl = () => {
        const nextUrl = new URL(window.location.href);
        const setOrRemove = (key, value) => {
            if (value !== null && value !== undefined && `${value}`.trim() !== '') {
                nextUrl.searchParams.set(key, value);
            } else {
                nextUrl.searchParams.delete(key);
            }
        };

        nextUrl.searchParams.delete('categoryIds[]');
        state.categoryIds.forEach(id => nextUrl.searchParams.append('categoryIds[]', id));

        nextUrl.searchParams.delete('sellerIds[]');
        state.sellerIds.forEach(id => nextUrl.searchParams.append('sellerIds[]', id));

        setOrRemove('q', state.q);
        setOrRemove('author', state.author);
        setOrRemove('minPrice', state.minPrice);
        setOrRemove('maxPrice', state.maxPrice);
        setOrRemove('minRating', state.minRating > 0 ? state.minRating : '');
        setOrRemove('inStock', state.inStock ? 'true' : '');
        setOrRemove('publishYearFrom', state.publishYearFrom);
        setOrRemove('publishYearTo', state.publishYearTo);
        setOrRemove('sort', state.sort);
        nextUrl.searchParams.set('page', state.page);
        nextUrl.searchParams.set('size', state.size);
        window.history.replaceState({}, '', nextUrl);
    };

    const updateResultMeta = (response) => {
        const totalElements = Number(response?.totalElements || 0);
        const from = totalElements === 0 ? 0 : state.page * state.size + 1;
        const to = Math.min(totalElements, state.page * state.size + Number(response?.numberOfElements || 0));
        if (resultTotal) resultTotal.textContent = String(totalElements);
        if (resultQuery) resultQuery.textContent = `"${state.q || 'Tat ca san pham'}"`;
        if (resultSummary) {
            const activeFilters = document.querySelectorAll('#search-filter-chips > button').length;
            resultSummary.textContent = activeFilters > 0
                ? `Đang áp dụng ${activeFilters} bộ lọc.`
                : 'Lọc theo giá, tác giả và các tiêu chí khác để thu hẹp kết quả nhanh hơn.';
        }
        if (resultRange) resultRange.textContent = `Hiển thị ${from} - ${to} của ${totalElements} kết quả`;
        renderFilterChips(); // Render chips on every update
    };

    const renderSearchSuggestions = (books) => {
        if (!suggestionPanel) {
            return;
        }

        if (!state.q || !books || books.length === 0) {
            suggestionPanel.classList.add('hidden');
            suggestionPanel.innerHTML = '';
            return;
        }

        suggestionPanel.innerHTML = books.slice(0, 6).map((book) => `
            <button type="button" data-book-id="${book.id}" class="w-full text-left px-5 py-4 hover:bg-brand-cream transition-colors border-b border-brand-border last:border-b-0">
                <div class="flex items-start gap-4">
                    <div class="w-12 h-16 rounded-xl bg-brand-dark text-white font-black text-[10px] leading-tight flex items-center justify-center text-center px-1 shrink-0">${escapeHtml((book.title || 'BOOK').slice(0, 24))}</div>
                    <div class="min-w-0 flex-1">
                        <div class="font-bold text-brand-dark line-clamp-2">${escapeHtml(book.title || '')}</div>
                        <div class="text-sm text-gray-500 mt-1 line-clamp-1">${escapeHtml(book.author || '')}</div>
                    </div>
                    <div class="text-brand-orange font-black text-sm whitespace-nowrap">${formatVND(book.price)}</div>
                </div>
            </button>
        `).join('');
        suggestionPanel.classList.remove('hidden');
    };

    const loadDiscoverySidebar = async () => {
        if (bestSellersContainer) bestSellersContainer.innerHTML = '<div class="rounded-2xl border border-dashed border-brand-border px-4 py-5 text-sm text-gray-500">Dang tai...</div>';
        if (trendingContainer) trendingContainer.innerHTML = '<div class="rounded-2xl border border-dashed border-brand-border px-4 py-5 text-sm text-gray-500">Dang tai...</div>';

        try {
            const [bestSellers, trending] = await Promise.all([
                ApiService.Book.bestSellers(4),
                ApiService.Book.trending(4, 30)
            ]);
            renderCompactBooks(bestSellersContainer, Array.isArray(bestSellers) ? bestSellers : [], 'dark');
            renderCompactBooks(trendingContainer, Array.isArray(trending) ? trending : [], 'light');
        } catch (error) {
            console.error('Load discovery sidebar failed:', error);
            renderCompactBooks(bestSellersContainer, [], 'dark');
            renderCompactBooks(trendingContainer, [], 'light');
        }
    };

    const loadBooks = async () => {
        renderLoading();
        try {
            const response = await ApiService.Book.search(
                state.q,
                state.categoryIds,
                state.page,
                state.size,
                {
                    sellerIds: state.sellerIds,
                    author: state.author,
                    minPrice: state.minPrice,
                    maxPrice: state.maxPrice,
                    minRating: state.minRating,
                    inStock: state.inStock,
                    publishYearFrom: state.publishYearFrom,
                    publishYearTo: state.publishYearTo,
                    sort: state.sort
                }
            );

            const books = Array.isArray(response?.content) ? response.content : [];
            updateResultMeta(response);

            if (books.length === 0) {
                renderEmpty();
                renderPagination(0, 0);
                return;
            }

            resultsGrid.innerHTML = books.map(buildCard).join('');
            renderPagination(response?.number ?? state.page, response?.totalPages ?? 0);
        } catch (error) {
            console.error('Load search results failed:', error);
            renderError();
        }
    };

    const applyFiltersFromInputs = () => {
        state.q = (searchInput?.value || '').trim();
        state.author = (authorInput?.value || '').trim();
        state.minPrice = (minPriceInput?.value || '').trim();
        state.maxPrice = (maxPriceInput?.value || '').trim();
        state.publishYearFrom = (yearFromInput?.value || '').trim();
        state.publishYearTo = (yearToInput?.value || '').trim();
        state.sort = sortSelect?.value || 'latest';
        state.minRating = ratingSlider ? Number(ratingSlider.value) : 0;
        state.inStock = inStockToggle ? inStockToggle.checked : false;

        state.categoryIds = Array.from(document.querySelectorAll('input[data-filter-type="category"]:checked'))
            .map(el => el.value);

        state.sellerIds = Array.from(document.querySelectorAll('input[data-filter-type="seller"]:checked'))
            .map(el => el.value);

        state.page = 0;
        syncUrl();
        loadBooks();
    };

    const resetFilters = () => {
        state.author = '';
        state.minPrice = '';
        state.maxPrice = '';
        state.minRating = 0;
        state.inStock = false;
        state.categoryIds = [];
        state.sellerIds = [];
        state.publishYearFrom = '';
        state.publishYearTo = '';
        state.sort = 'latest';
        state.page = 0;

        if (authorInput) authorInput.value = '';
        if (minPriceInput) minPriceInput.value = '';
        if (maxPriceInput) maxPriceInput.value = '';
        if (ratingSlider) ratingSlider.value = 0;
        if (ratingValue) ratingValue.textContent = '0 sao';
        if (inStockToggle) inStockToggle.checked = false;
        if (yearFromInput) yearFromInput.value = '';
        if (yearToInput) yearToInput.value = '';
        if (sortSelect) sortSelect.value = 'latest';
        document.querySelectorAll('input[data-filter-type]:checked').forEach(el => el.checked = false);
        syncUrl();
        loadBooks();
    };

    const bindSearchSuggestions = () => {
        if (!searchInput || !searchForm) {
            return;
        }

        let searchTimeout;
        searchInput.addEventListener('input', () => {
            state.q = searchInput.value.trim();
            state.page = 0;
            syncUrl();

            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(async () => {
                if (!state.q) {
                    renderSearchSuggestions([]);
                    return;
                }

                try {
                    const suggestions = await ApiService.Book.suggestions(state.q, 6);
                    renderSearchSuggestions(Array.isArray(suggestions) ? suggestions : []);
                } catch (error) {
                    console.error('Suggestion request failed:', error);
                    renderSearchSuggestions([]);
                }
            }, 180);
        });

        searchForm.addEventListener('submit', (event) => {
            event.preventDefault();
            state.q = (searchInput.value || '').trim();
            state.page = 0;
            syncUrl();
            loadBooks();
        });

        document.addEventListener('click', (event) => {
            if (!suggestionPanel) {
                return;
            }
            if (!searchForm.contains(event.target) && !suggestionPanel.contains(event.target)) {
                suggestionPanel.classList.add('hidden');
            }
        });

        suggestionPanel?.addEventListener('click', (event) => {
            const suggestionButton = event.target.closest('button[data-book-id]');
            if (!suggestionButton) {
                return;
            }
            const bookId = suggestionButton.getAttribute('data-book-id');
            if (bookId) {
                window.location.href = `/book/${bookId}`;
            }
        });
    };

    const renderFilterChips = () => {
        const container = document.getElementById('search-filter-chips');
        if (!container) return;

        let chipsHtml = '';

        state.categoryIds.forEach(id => {
            const label = categoryLabels.get(id) || `Category ${id}`;
            chipsHtml += `<button data-chip-type="category" data-chip-value="${id}" class="chip">${label} &times;</button>`;
        });
        state.sellerIds.forEach(id => {
            const label = sellerLabels.get(id) || `Seller ${id}`;
            chipsHtml += `<button data-chip-type="seller" data-chip-value="${id}" class="chip">${label} &times;</button>`;
        });
        if (state.minRating > 0) {
            chipsHtml += `<button data-chip-type="minRating" class="chip">Từ ${state.minRating} sao &times;</button>`;
        }
        if (state.inStock) {
            chipsHtml += `<button data-chip-type="inStock" class="chip">Còn hàng &times;</button>`;
        }

        container.innerHTML = chipsHtml.length > 0
            ? `<style>.chip { background-color: #f3eade; color: #4a3b32; padding: 4px 10px; border-radius: 99px; font-size: 12px; font-weight: bold; transition: all 0.2s; } .chip:hover { background-color: #cda27a; color: white; }</style>` + chipsHtml
            : '';
    };

    document.getElementById('search-filter-chips')?.addEventListener('click', (e) => {
        if (e.target.tagName !== 'BUTTON') return;
        const type = e.target.dataset.chipType;
        const value = e.target.dataset.chipValue;

        switch (type) {
            case 'category':
                state.categoryIds = state.categoryIds.filter(id => id !== value);
                break;
            case 'seller':
                state.sellerIds = state.sellerIds.filter(id => id !== value);
                break;
            case 'minRating':
                state.minRating = 0;
                break;
            case 'inStock':
                state.inStock = false;
                break;
        }
        state.page = 0;
        syncUrl();
        updateFilterControlsFromState();
        loadBooks();
    });

    const initAccordion = () => {
        document.querySelectorAll('.filter-toggle').forEach(button => {
            button.addEventListener('click', () => {
                const content = button.nextElementSibling;
                const icon = button.querySelector('svg');
                content.classList.toggle('hidden');
                icon.classList.toggle('rotate-180');
            });
        });
    };

    resultsGrid.addEventListener('click', (event) => {
        const card = event.target.closest('.product-card[data-detail-url], article[data-detail-url]');
        if (card && !event.target.closest('button, a, input, label, select, textarea')) {
            window.location.href = card.getAttribute('data-detail-url');
            return;
        }

        const wishlistButton = event.target.closest('button[data-toggle-wishlist="true"]');
        if (wishlistButton) {
            event.preventDefault();
            event.stopPropagation();

            const buyerId = ApiService.getAuth().userId;
            const role = ApiService.getAuth().role;
            if (!buyerId || role !== 'BUYER') {
                alert('Vui long dang nhap tai khoan BUYER de luu sach yeu thich.');
                window.location.href = '/main/auth';
                return;
            }

            (async () => {
                const book = getWishlistBookPayload(wishlistButton);
                const result = await ApiService.Wishlist.toggle(book, buyerId);
                applyWishlistButtonState(wishlistButton, result.saved);
                alert(result.saved ? 'Da luu vao Wishlist.' : 'Da xoa khoi Wishlist.');
            })().catch((error) => {
                alert(error?.message || 'Khong the cap nhat Wishlist.');
            });
            return;
        }

        const targetButton = event.target.closest('button[data-add-to-cart="true"]');
        if (!targetButton) {
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        const bookId = targetButton.getAttribute('data-book-id');
        if (bookId) {
            addToCart(bookId, 1);
        }
    });

    applyFiltersBtn?.addEventListener('click', applyFiltersFromInputs);
    resetFiltersBtn?.addEventListener('click', resetFilters);
    sortSelect?.addEventListener('change', () => {
        state.sort = sortSelect.value;
        state.page = 0;
        syncUrl();
        loadBooks();
    });

    if (ratingSlider && ratingValue) {
        ratingSlider.addEventListener('input', () => {
            ratingValue.textContent = `${ratingSlider.value} sao`;
        });
    }

    const updateFilterControlsFromState = () => {
        if (sortSelect) sortSelect.value = state.sort;
        if (authorInput) authorInput.value = state.author;
        if (minPriceInput) minPriceInput.value = state.minPrice;
        if (maxPriceInput) maxPriceInput.value = state.maxPrice;
        if (yearFromInput) yearFromInput.value = state.publishYearFrom;
        if (yearToInput) yearToInput.value = state.publishYearTo;
        if (searchInput) searchInput.value = state.q;
        if (ratingSlider) ratingSlider.value = state.minRating;
        if (ratingValue) ratingValue.textContent = `${state.minRating} sao`;
        if (inStockToggle) inStockToggle.checked = state.inStock;

        document.querySelectorAll('input[data-filter-type="category"]').forEach(el => el.checked = state.categoryIds.includes(el.value));
        document.querySelectorAll('input[data-filter-type="seller"]').forEach(el => el.checked = state.sellerIds.includes(el.value));
    };

    (async () => {
        await ApiService.Wishlist.bootstrap().catch(() => {});
        initAccordion();
        updateFilterControlsFromState();
        await loadCategories();
        await loadSellers();
        bindSearchSuggestions();
        await loadDiscoverySidebar();
        await loadBooks();
    })();
});
