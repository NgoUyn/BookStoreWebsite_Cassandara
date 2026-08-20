const initDiscoveryPage = () => {
    if (!window.ApiService) {
        console.error('ApiService not available');
        return;
    }

    const bookGrid = document.getElementById('book-grid');
    const paginationContainer = document.querySelector('.mt-16.text-center');
    const discoverySearchForm = document.getElementById('discovery-search-form');
    const discoverySearchInput = document.getElementById('discovery-search-input');
    const discoverySuggestionPanel = document.getElementById('discovery-search-suggestions');
    const discoveryCategoryChips = document.getElementById('discovery-category-chips');
    const discoveryCategoryGrid = document.getElementById('discovery-category-grid');
    const bestSellersGrid = document.getElementById('best-sellers-grid');
    const trendingGrid = document.getElementById('trending-grid');
    if (!bookGrid) {
        return;
    }

    const pageSize = 20;
    let currentPage = 0;

    const formatVND = (price) => {
        return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price || 0);
    };

    const escapeHtml = (text) => {
        const div = document.createElement('div');
        div.textContent = text ?? '';
        return div.innerHTML;
    };

    const navigateToSearch = (query) => {
        const url = new URL('/main/search', window.location.origin);
        if (query && query.trim()) {
            url.searchParams.set('q', query.trim());
        }
        window.location.href = url.toString();
    };

    const navigateToCategory = (categoryId) => {
        const url = new URL('/main/search', window.location.origin);
        if (categoryId) {
            url.searchParams.set('categoryId', categoryId);
        }
        window.location.href = url.toString();
    };

    const renderCompactBooks = (container, books, theme = 'light') => {
        if (!container) {
            return;
        }

        if (!books || books.length === 0) {
            container.innerHTML = `<div class="rounded-2xl border border-dashed ${theme === 'dark' ? 'border-white/20 text-white/70 bg-white/5' : 'border-brand-accent text-brand-dark/60 bg-brand-cream/40'} px-4 py-5 text-sm italic">Chưa có dữ liệu phù hợp.</div>`;
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
                        <p class="text-xs ${mutedClass} mt-1 line-clamp-1">${escapeHtml(book.author || 'Đang cập nhật')}</p>
                    </div>
                    <div class="font-black ${theme === 'dark' ? 'text-brand-peach' : 'text-brand-orange'} text-sm whitespace-nowrap">${formatVND(book.price)}</div>
                </a>
            `;
        }).join('');
    };

    const renderCategoryChips = (categories) => {
        if (!discoveryCategoryChips) {
            return;
        }

        if (!Array.isArray(categories) || categories.length === 0) {
            discoveryCategoryChips.innerHTML = `<div class="px-4 py-2 rounded-full bg-white border border-brand-accent text-sm font-bold text-gray-500">Chua co danh muc</div>`;
            return;
        }

        discoveryCategoryChips.innerHTML = categories.slice(0, 6).map((category) => `
            <button type="button" data-category-id="${category.id}" class="px-4 py-2 rounded-full bg-white border border-brand-accent text-sm font-bold text-brand-dark hover:border-brand-biscuit hover:text-brand-biscuit transition-colors">${escapeHtml(category.name || 'Danh mục')}</button>
        `).join('');
    };

    const renderCategoryGrid = (categories) => {
        if (!discoveryCategoryGrid) {
            return;
        }

        if (!Array.isArray(categories) || categories.length === 0) {
            discoveryCategoryGrid.innerHTML = `<div class="rounded-2xl border border-dashed border-brand-accent bg-brand-cream/30 px-6 py-4 text-sm text-gray-500 font-semibold">Chua co danh muc</div>`;
            return;
        }

        discoveryCategoryGrid.innerHTML = categories.slice(0, 12).map((category) => {
            const name = category.name || 'Danh mục';
            const initial = name.trim().charAt(0).toUpperCase() || '•';
            return `
                <button type="button" data-category-id="${category.id}" class="category-pill bg-brand-cream border border-brand-accent rounded-2xl px-6 py-4 flex flex-col items-center justify-center min-w-32.5 group hover:border-brand-biscuit transition-colors">
                    <div class="w-12 h-12 rounded-full bg-brand-peach flex items-center justify-center text-brand-dark font-black text-xl mb-3 shadow-inner">${escapeHtml(initial)}</div>
                    <span class="font-bold text-brand-dark text-sm group-hover:text-brand-biscuit transition-colors">${escapeHtml(name)}</span>
                </button>
            `;
        }).join('');
    };

    const hideDiscoverySuggestions = () => {
        if (!discoverySuggestionPanel) return;
        discoverySuggestionPanel.classList.add('hidden');
        discoverySuggestionPanel.innerHTML = '';
    };

    const renderDiscoverySuggestions = (books, query) => {
        if (!discoverySuggestionPanel) {
            return;
        }

        const trimmed = (query || '').trim();
        if (!trimmed || !books || books.length === 0) {
            hideDiscoverySuggestions();
            return;
        }

        discoverySuggestionPanel.innerHTML = books.slice(0, 6).map((book) => `
            <button type="button" data-book-id="${book.id}" data-book-title="${escapeHtml(book.title || '')}" class="w-full text-left px-5 py-4 hover:bg-brand-cream transition-colors border-b border-brand-accent last:border-b-0">
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
        discoverySuggestionPanel.classList.remove('hidden');
    };

    const loadDiscoveryHighlights = async () => {
        const fallbackCard = `<div class="rounded-2xl border border-dashed border-brand-accent px-4 py-5 text-sm text-gray-500 bg-brand-cream/30">Đang tải...</div>`;
        const fallbackCategory = `<div class="rounded-2xl border border-dashed border-brand-accent bg-brand-cream/30 px-6 py-4 text-sm text-gray-500 font-semibold">Đang tải danh mục...</div>`;
        if (discoveryCategoryChips) discoveryCategoryChips.innerHTML = fallbackCategory;
        if (discoveryCategoryGrid) discoveryCategoryGrid.innerHTML = fallbackCategory;
        if (bestSellersGrid) bestSellersGrid.innerHTML = fallbackCard;
        if (trendingGrid) trendingGrid.innerHTML = fallbackCard;

        try {
            const [categories, bestSellers, trending] = await Promise.all([
                ApiService.Category.getAll(),
                ApiService.Book.bestSellers(4),
                ApiService.Book.trending(4, 30)
            ]);
            renderCategoryChips(Array.isArray(categories) ? categories : []);
            renderCategoryGrid(Array.isArray(categories) ? categories : []);
            renderCompactBooks(bestSellersGrid, Array.isArray(bestSellers) ? bestSellers : [], 'dark');
            renderCompactBooks(trendingGrid, Array.isArray(trending) ? trending : [], 'light');
        } catch (error) {
            console.error('Loi tai danh sach discovery:', error);
            renderCategoryChips([]);
            renderCategoryGrid([]);
            renderCompactBooks(bestSellersGrid, [], 'dark');
            renderCompactBooks(trendingGrid, [], 'light');
        }
    };

    const addToCart = async (bookId, quantity = 1) => {
        const { userId, role } = ApiService.getAuth();
        if (!userId || role !== 'BUYER') {
            alert('Vui long dang nhap tai khoan BUYER de them vao gio hang.');
            window.location.href = '/main/auth';
            return;
        }

        try {
            await ApiService.Cart.addItem(userId, {
                bookId: Number(bookId),
                quantity
            });
            alert('Da them san pham vao gio hang.');
        } catch (error) {
            const message = error?.message || 'Them vao gio hang that bai.';
            alert(message);
        }
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

    const createWishlistButton = (book) => {
        const isSaved = ApiService.Wishlist.isSaved(book.id);
        const title = book.title || 'Chua co ten sach';
        const author = book.author || 'Chua co tac gia';
        const categoryName = book.category?.name || 'Sach';
        const imageUrl = book.imageUrl || '';

        return `
            <button type="button" data-toggle-wishlist="true" data-book-id="${book.id}" data-book-title="${title}" data-book-author="${author}" data-book-price="${book.price ?? ''}" data-book-image="${imageUrl}" data-book-category="${categoryName}" aria-pressed="${isSaved ? 'true' : 'false'}" title="${isSaved ? 'Xoa khoi wishlist' : 'Them vao wishlist'}" class="${isSaved ? 'w-10 h-10 bg-red-500 text-white rounded-full shadow-lg flex items-center justify-center transition-colors' : 'w-10 h-10 bg-white text-brand-dark rounded-full shadow-lg flex items-center justify-center hover:bg-brand-biscuit hover:text-white transition-colors'}">
                ${isSaved ? '<svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"></path></svg>' : '<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"></path></svg>'}
            </button>
        `;
    };

    const getWishlistBookPayload = (button) => ({
        id: Number(button.getAttribute('data-book-id')),
        title: button.getAttribute('data-book-title') || '',
        author: button.getAttribute('data-book-author') || '',
        price: Number(button.getAttribute('data-book-price') || 0),
        imageUrl: button.getAttribute('data-book-image') || '',
        categoryName: button.getAttribute('data-book-category') || 'Sach'
    });


    const setGridBusy = (isBusy) => {
        bookGrid.setAttribute('aria-busy', isBusy ? 'true' : 'false');
    };

    const renderBooksLoading = () => {
        setGridBusy(true);
        const skeleton = `
            <div class="bg-white border border-brand-accent rounded-2xl p-4 animate-pulse">
                <div class="w-full aspect-3/4 bg-brand-cream rounded-xl mb-4"></div>
                <div class="h-3 bg-gray-200 rounded w-2/3 mb-2"></div>
                <div class="h-3 bg-gray-200 rounded w-1/2 mb-4"></div>
                <div class="h-4 bg-gray-200 rounded w-1/3"></div>
            </div>
        `;
        bookGrid.innerHTML = Array.from({ length: 10 }).map(() => skeleton).join('');
        renderPaginationLoading();
    };

    const renderBooksEmpty = () => {
        setGridBusy(false);
        bookGrid.innerHTML = `
            <div class="col-span-full">
                <div class="bg-white border border-brand-accent rounded-2xl p-10 text-center shadow-soft">
                    <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-brand-cream flex items-center justify-center text-brand-biscuit">
                        <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"/></svg>
                    </div>
                    <h3 class="text-lg font-bold text-brand-dark mb-2">Chua co sach de hien thi</h3>
                    <p class="text-sm text-gray-500 mb-6">Cua hang dang cap nhat them san pham moi.</p>
                    <button type="button" id="discovery-retry-btn" class="bg-brand-biscuit text-white font-bold px-6 py-2.5 rounded-full hover:bg-brand-dark transition-colors">Thu lai</button>
                </div>
            </div>
        `;
        document.getElementById('discovery-retry-btn')?.addEventListener('click', () => fetchBooks(currentPage));
    };

    const renderBooksError = (message = 'Loi ket noi may chu. Vui long thu lai.') => {
        setGridBusy(false);
        bookGrid.innerHTML = `
            <div class="col-span-full">
                <div class="bg-red-50 border border-red-200 text-red-700 rounded-2xl px-6 py-6 text-center">
                    <div class="font-bold mb-2">${message}</div>
                    <button id="discovery-retry-btn" class="mt-2 bg-red-600 text-white px-5 py-2 rounded-full font-bold hover:bg-red-700 transition-colors">Thu lai</button>
                </div>
            </div>
        `;
        document.getElementById('discovery-retry-btn')?.addEventListener('click', () => fetchBooks(currentPage));
    };

    const renderPaginationLoading = () => {
        if (!paginationContainer) {
            return;
        }
        paginationContainer.innerHTML = `
            <div class="flex justify-center items-center gap-4 animate-pulse">
                <div class="h-10 w-28 bg-brand-cream rounded-full"></div>
                <div class="h-4 w-24 bg-brand-cream rounded-full"></div>
                <div class="h-10 w-28 bg-brand-cream rounded-full"></div>
            </div>
        `;
    };

    const renderPagination = (current, total) => {
        if (!paginationContainer) {
            return;
        }

        if (!total || total <= 1) {
            paginationContainer.innerHTML = '';
            return;
        }

        let paginationHtml = `<div class="flex justify-center items-center gap-4">`;

        if (current > 0) {
            paginationHtml += `<button onclick="window.changePage(${current - 1})" class="bg-white border-2 border-brand-biscuit text-brand-biscuit font-bold px-6 py-2 rounded-full hover:bg-brand-biscuit hover:text-white transition-colors">Trang truoc</button>`;
        }

        paginationHtml += `<span class="font-bold text-brand-dark">Trang ${current + 1} / ${total}</span>`;

        if (current < total - 1) {
            paginationHtml += `<button onclick="window.changePage(${current + 1})" class="bg-white border-2 border-brand-biscuit text-brand-biscuit font-bold px-6 py-2 rounded-full hover:bg-brand-biscuit hover:text-white transition-colors">Trang sau</button>`;
        }

        paginationHtml += `</div>`;
        paginationContainer.innerHTML = paginationHtml;
    };

    const fetchBooks = async (page) => {
        renderBooksLoading();
        try {
            const response = await ApiService.Book.search('', null, page, pageSize);
            const books = Array.isArray(response?.content) ? response.content : [];
            const totalPages = response?.totalPages ?? 0;

            if (books.length === 0) {
                renderBooksEmpty();
                renderPagination(0, 0);
                return;
            }

            setGridBusy(false);
            bookGrid.innerHTML = books.map((book) => {
                const coverUrl = book.imageUrl ? book.imageUrl : 'https://via.placeholder.com/150x200?text=No+Cover';
                return `
                    <a href="/book/${book.id}" data-detail-url="/book/${book.id}" class="product-card group relative h-full flex flex-col bg-white rounded-2xl p-4 shadow-sm border border-brand-accent cursor-pointer">
                        <div class="relative w-full aspect-3/4 bg-brand-cream rounded-xl mb-4 flex items-center justify-center overflow-hidden border border-brand-accent">
                            <img src="${coverUrl}" alt="${book.title}" class="book-cover w-full h-full object-cover shadow-md transition-transform duration-500" onerror="this.src='https://via.placeholder.com/150x200?text=Error'"/>
                            <div class="absolute inset-0 bg-black bg-opacity-0 group-hover:bg-opacity-30 transition-all duration-300 flex items-center justify-center opacity-0 group-hover:opacity-100">
                                <button onclick="event.preventDefault(); event.stopPropagation(); openQuickView(${book.id})" class="bg-white text-brand-dark p-3 rounded-full shadow-lg transform hover:scale-110 transition-transform" title="Xem nhanh">
                                    <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path></svg>
                                </button>
                            </div>
                            <div class="absolute bottom-3 left-3 right-3 flex justify-between gap-2">
                                ${createWishlistButton(book)}
                                <button type="button" data-add-to-cart="true" data-book-id="${book.id}" class="cart-btn-hover w-10 h-10 bg-brand-biscuit text-white rounded-full shadow-lg flex items-center justify-center hover:bg-brand-dark transition-colors">
                                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"></path></svg>
                                </button>
                            </div>
                        </div>
                        <div class="flex flex-col grow">
                            <h3 class="text-sm font-bold text-brand-dark leading-snug mb-1 line-clamp-2 group-hover:text-brand-biscuit transition-colors">${book.title}</h3>
                            <div class="text-xs text-gray-500 font-medium mb-3">${book.author}</div>
                            <div class="mt-auto flex justify-between items-end">
                                <div><span class="text-brand-orange font-black text-lg">${formatVND(book.price)}</span></div>
                            </div>
                        </div>
                    </a>
                `;
            }).join('');

            renderPagination(page, totalPages);
        } catch (error) {
            console.error('Loi tai sach:', error);
            renderBooksError();
            renderPagination(0, 0);
        }
    };

    bookGrid.addEventListener('click', (event) => {
        const card = event.target.closest('.product-card[data-detail-url]');
        if (card && !event.target.closest('button, a, input, label, select, textarea')) {
            window.location.href = card.getAttribute('data-detail-url');
            return;
        }

        const wishlistButton = event.target.closest('button[data-toggle-wishlist="true"]');
        if (wishlistButton) {
            event.preventDefault();
            event.stopPropagation();

            const { userId, role } = ApiService.getAuth();
            if (!userId || role !== 'BUYER') {
                alert('Vui long dang nhap tai khoan BUYER de luu sach yeu thich.');
                window.location.href = '/main/auth';
                return;
            }

            (async () => {
                const book = getWishlistBookPayload(wishlistButton);
                const result = await ApiService.Wishlist.toggle(book, userId);
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

    if (discoverySearchForm && discoverySearchInput) {
        let suggestionTimeout;

        discoverySearchInput.addEventListener('input', () => {
            clearTimeout(suggestionTimeout);
            suggestionTimeout = setTimeout(async () => {
                const query = discoverySearchInput.value.trim();
                if (!query) {
                    hideDiscoverySuggestions();
                    return;
                }

                try {
                    const suggestions = await ApiService.Book.suggestions(query, 6);
                    renderDiscoverySuggestions(Array.isArray(suggestions) ? suggestions : [], query);
                } catch (error) {
                    console.error('Loi goi y tim kiem:', error);
                    hideDiscoverySuggestions();
                }
            }, 180);
        });

        discoverySearchForm.addEventListener('submit', (event) => {
            event.preventDefault();
            navigateToSearch(discoverySearchInput.value);
        });

        document.querySelectorAll('[data-discovery-query]').forEach((button) => {
            button.addEventListener('click', () => {
                discoverySearchInput.value = button.getAttribute('data-discovery-query') || '';
                navigateToSearch(discoverySearchInput.value);
            });
        });

        document.addEventListener('click', (event) => {
            const categoryButton = event.target.closest('button[data-category-id]');
            if (!categoryButton) {
                return;
            }

            const categoryId = categoryButton.getAttribute('data-category-id');
            if (categoryId) {
                navigateToCategory(categoryId);
            }
        });

        document.addEventListener('click', (event) => {
            if (!discoverySuggestionPanel) {
                return;
            }
            if (!discoverySearchForm.contains(event.target) && !discoverySuggestionPanel.contains(event.target)) {
                hideDiscoverySuggestions();
            }
        });

        discoverySuggestionPanel?.addEventListener('click', (event) => {
            const suggestionButton = event.target.closest('button[data-book-id]');
            if (!suggestionButton) {
                return;
            }
            const bookId = suggestionButton.getAttribute('data-book-id');
            if (bookId) {
                window.location.href = `/book/${bookId}`;
            }
        });
    }

    window.changePage = (newPage) => {
        currentPage = newPage;
        fetchBooks(currentPage);
        document.getElementById('sach-moi-nhat')?.scrollIntoView({ behavior: 'smooth' });
    };

    (async () => {
        await ApiService.Wishlist.bootstrap().catch(() => {});
        await loadDiscoveryHighlights();
        await fetchBooks(currentPage);
    })();};
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initDiscoveryPage);
} else {
    initDiscoveryPage();
}
