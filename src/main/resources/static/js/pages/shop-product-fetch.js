/**
 * Shop Product Fetch - Client-side product rendering for Shop pages
 * Supports both public (buyer/guest) and seller modes:
 * - Public: fetches from /api/books/search?sellerIds=...
 * - Seller: fetches from /api/books/seller/me
 * Features: pagination (20 items per page), "Xem thêm" button, deduplication via Set.
 * Filter: category, price range, rating.
 */
(function() {
    'use strict';

    // ==========================================
    // DOM ELEMENTS
    // ==========================================
    const loadingEl = document.getElementById('products-loading');
    const emptyEl = document.getElementById('products-empty');
    const errorEl = document.getElementById('products-error');
    const containerEl = document.getElementById('products-container');
    const footerEl = document.getElementById('products-footer');
    const loadMoreBtn = document.getElementById('products-load-more');
    const loadedCountEl = document.getElementById('products-loaded-count');
    const totalCountEl = document.getElementById('products-total-count');

    // Guard: if container doesn't exist, we're not on the right page
    if (!containerEl) return;

    // Tab elements
    const tabAll = document.getElementById('tab-all');
    const tabBestSeller = document.getElementById('tab-best-seller');
    const tabNewest = document.getElementById('tab-newest');
    const tabs = [tabAll, tabBestSeller, tabNewest].filter(Boolean);

    // Filter elements
    const categoryList = document.getElementById('category-list');
    const categoryAllLink = document.getElementById('filter-category-all');
    const categoryAllCount = document.getElementById('category-all-count');
    const priceMinInput = document.getElementById('price-min');
    const priceMaxInput = document.getElementById('price-max');
    const btnApplyPrice = document.getElementById('btn-apply-price');
    const rating5Checkbox = document.getElementById('rating-5');
    const rating4Checkbox = document.getElementById('rating-4');

    // ==========================================
    // STATE
    // ==========================================
    const PAGE_SIZE = 20;
    let currentFilter = 'all';
    let currentPage = 0;
    let totalPages = 0;
    let totalElements = 0;
    let loadedIds = new Set();  // Deduplication set
    let isLoading = false;
    let sellerId = null;  // Cached sellerId for public mode

    // Filter state
    let filterState = {
        categoryIds: [],      // Mảng các category ID đã chọn (rỗng = tất cả)
        minPrice: null,
        maxPrice: null,
        minRating: null
    };

    // ==========================================
    // HELPERS
    // ==========================================

    const formatVnd = (value) => {
        if (value == null || value <= 0) return '0đ';
        return new Intl.NumberFormat('vi-VN', {
            style: 'currency',
            currency: 'VND',
            maximumFractionDigits: 0
        }).format(value);
    };

    /**
     * Render a single product card matching the style of book_card.html
     */
    const renderProductCard = (book) => {
        const id = book.id || '';
        const title = book.title || 'Chưa có tên';
        const author = book.author || 'Chưa rõ tác giả';
        const imgUrl = book.imageUrl || book.coverUrl || '';
        const price = Number(book.price || 0);
        const discount = Number(book.discountPercent || book.discount || 0);
        const finalPrice = price - (price * discount / 100);
        const href = `/books/${id}`;
        const rating = book.averageRating || book.rating || 0;
        const soldCount = book.soldCount || book.totalSold || 0;

        // Discount badge
        const discountBadge = discount > 0
            ? `<div class="absolute top-0 left-0 z-20 m-3 px-2.5 py-1 bg-brand-orange text-white text-[11px] font-bold uppercase tracking-wider rounded-full shadow-md">Giảm ${discount}%</div>`
            : '';

        // Rating stars
        const starsHtml = rating > 0
            ? `<div class="flex items-center gap-0.5 text-yellow-400">
                <svg class="w-3.5 h-3.5 fill-current" viewBox="0 0 20 20"><path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z"/></svg>
                <span class="font-semibold text-gray-600">${rating.toFixed(1)}</span>
              </div>`
            : '';

        const soldText = soldCount > 0
            ? `<span class="mx-1">·</span><span>Đã bán ${soldCount >= 1000 ? (soldCount / 1000).toFixed(1) + 'k' : soldCount}</span>`
            : '';

        // Price display
        let priceHtml;
        if (discount > 0) {
            priceHtml = `
                <span class="text-[11px] text-gray-400 line-through">${formatVnd(price)}</span>
                <span class="text-lg font-extrabold text-brand-dark">${formatVnd(finalPrice)}</span>
            `;
        } else {
            priceHtml = `<span class="text-lg font-extrabold text-brand-dark">${formatVnd(price)}</span>`;
        }

        return `
            <article class="product-card group relative bg-white rounded-xl shadow-soft transition-all duration-300 hover:shadow-hover"
                     data-detail-url="${href}"
                     style="perspective: 1000px;">
                <div class="product-card-inner relative w-full h-full rounded-xl transition-transform duration-500 ease-out group-hover:[transform:rotateY(var(--rotateX))_rotateX(var(--rotateY))]">
                    ${discountBadge}
                    <a href="${href}" class="main-link absolute inset-0 z-10" aria-label="Xem chi tiết ${title}"></a>
                    <div class="relative h-[280px] sm:h-[320px] w-full bg-gray-50 rounded-t-xl overflow-hidden p-4 flex items-center justify-center">
                        <img src="${imgUrl || 'https://via.placeholder.com/280x380?text=No+Cover'}"
                             alt="Bìa sách ${title}"
                             class="book-cover relative w-auto h-full object-contain drop-shadow-[0_15px_20px_rgba(74,59,50,0.2)] transition-all duration-500 ease-out group-hover:scale-110 group-hover:-translate-y-2"
                             loading="lazy"
                             decoding="async"
                             onerror="this.onerror=null;this.src='https://via.placeholder.com/280x380?text=No+Cover'" />
                    </div>
                    <div class="p-4 flex flex-col">
                        <p class="text-xs text-gray-500 font-medium mb-1 line-clamp-1">${author}</p>
                        <h3 class="text-base font-bold text-brand-dark leading-snug line-clamp-2 h-12 mb-2">
                            <a href="${href}" class="hover:text-brand-orange transition-colors">${title}</a>
                        </h3>
                        <div class="flex items-center gap-2 text-xs text-gray-500 mb-3">
                            ${starsHtml}
                            ${soldText}
                        </div>
                        <div class="mt-auto pt-3 border-t border-gray-100">
                            <div class="flex items-center justify-between">
                                <div class="flex flex-col">
                                    ${priceHtml}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </article>
        `;
    };

    /**
     * Show one state element, hide others
     */
    const showState = (showEl) => {
        [loadingEl, emptyEl, errorEl, containerEl, footerEl].forEach(el => {
            if (el) el.classList.add('hidden');
        });
        if (showEl) showEl.classList.remove('hidden');
    };

    /**
     * Update the progress counter and load-more button visibility
     */
    const updateFooter = () => {
        if (!footerEl || !loadedCountEl || !totalCountEl || !loadMoreBtn) return;

        const loadedCount = loadedIds.size;
        loadedCountEl.textContent = loadedCount.toLocaleString('vi-VN');
        totalCountEl.textContent = totalElements.toLocaleString('vi-VN');

        // Show footer only when there are products loaded
        if (loadedCount > 0) {
            footerEl.classList.remove('hidden');
        } else {
            footerEl.classList.add('hidden');
            return;
        }

        // Show/hide load-more button
        if (currentPage >= totalPages - 1 || loadedCount >= totalElements) {
            loadMoreBtn.classList.add('hidden');
        } else {
            loadMoreBtn.classList.remove('hidden');
        }
    };

    /**
     * Activate a tab (update visual style)
     */
    const activateTab = (activeTab) => {
        tabs.forEach(tab => {
            if (!tab) return;
            const span = tab.querySelector('span');
            if (tab === activeTab) {
                tab.classList.remove('text-gray-600');
                tab.classList.add('text-brand-brown');
                if (span) {
                    span.classList.remove('scale-x-0');
                    span.classList.add('scale-x-100');
                }
            } else {
                tab.classList.remove('text-brand-brown');
                tab.classList.add('text-gray-600');
                if (span) {
                    span.classList.remove('scale-x-100');
                    span.classList.add('scale-x-0');
                }
            }
        });
    };

    /**
     * Get auth headers for API calls
     */
    const getAuthHeaders = () => {
        const token = localStorage.getItem('accessToken') || localStorage.getItem('token') || localStorage.getItem('jwt');
        const userId = localStorage.getItem('userId');
        const headers = { 'Content-Type': 'application/json' };
        if (token) headers['Authorization'] = `Bearer ${token}`;
        if (userId) headers['X-User-Id'] = userId;
        return headers;
    };

    /**
     * Fetch sellerId from shop slug (for public mode)
     */
    const resolveSellerId = async () => {
        if (sellerId) return sellerId;
        
        const host = document.getElementById('shop-host');
        if (!host || !host.dataset.slug) return null;
        
        try {
            const res = await fetch(`/api/shops/${encodeURIComponent(host.dataset.slug)}`);
            if (!res.ok) return null;
            const shop = await res.json();
            sellerId = shop.sellerId || (shop.seller && shop.seller.id) || shop.id;
            return sellerId;
        } catch (e) {
            console.error('Không thể lấy sellerId:', e);
            return null;
        }
    };

    /**
     * Highlight category đang được chọn
     */
    const highlightCategory = (selectedId) => {
        // Reset tất cả
        const allLinks = categoryList ? categoryList.querySelectorAll('a[data-category-id]') : [];
        allLinks.forEach(link => {
            link.classList.remove('text-brand-brown');
            link.classList.add('hover:text-brand-brown');
            const dot = link.querySelector('span.w-1\\.5');
            if (dot) {
                dot.classList.remove('bg-brand-brown');
                dot.classList.add('bg-brand-border');
            }
        });

        // Highlight selected
        let selectedLink;
        if (selectedId === 'all') {
            selectedLink = categoryAllLink;
        } else {
            selectedLink = categoryList ? categoryList.querySelector(`a[data-category-id="${selectedId}"]`) : null;
        }
        if (selectedLink) {
            selectedLink.classList.add('text-brand-brown');
            selectedLink.classList.remove('hover:text-brand-brown');
            const dot = selectedLink.querySelector('span.w-1\\.5');
            if (dot) {
                dot.classList.remove('bg-brand-border');
                dot.classList.add('bg-brand-brown');
            }
        }
    };

    /**
     * Load categories từ API và render vào sidebar
     */
    const loadCategories = async () => {
        const host = document.getElementById('shop-host');
        if (!host || !host.dataset.slug) return;

        try {
            const res = await fetch(`/api/shops/${encodeURIComponent(host.dataset.slug)}/categories`);
            if (!res.ok) return;
            const categories = await res.json();

            // Cập nhật tổng số sản phẩm
            let totalCount = 0;
            if (categoryAllCount) {
                categories.forEach(c => { totalCount += c.count; });
                categoryAllCount.textContent = totalCount.toLocaleString('vi-VN');
            }

            // Render từng category
            if (categoryList) {
                categories.forEach(cat => {
                    const li = document.createElement('li');
                    li.innerHTML = `
                        <a href="#"
                           class="flex items-center justify-between group hover:text-brand-brown transition-colors"
                           data-category-id="${cat.id}">
                            <span class="flex items-center gap-3">
                                <span class="w-1.5 h-1.5 rounded-full bg-brand-border group-hover:bg-brand-brown transition-colors"></span>
                                ${cat.name}
                            </span>
                            <span class="text-xs bg-brand-hero px-2 py-0.5 rounded-full">${cat.count.toLocaleString('vi-VN')}</span>
                        </a>
                    `;
                    categoryList.appendChild(li);

                    // Gắn event click cho từng category
                    const link = li.querySelector('a');
                    link.addEventListener('click', (e) => {
                        e.preventDefault();
                        const catId = link.dataset.categoryId;
                        // Toggle category: nếu đã chọn thì bỏ chọn, nếu chưa thì chọn
                        const idx = filterState.categoryIds.indexOf(Number(catId));
                        if (idx >= 0) {
                            filterState.categoryIds.splice(idx, 1);
                        } else {
                            filterState.categoryIds = [Number(catId)]; // Chỉ cho chọn 1 category
                        }
                        highlightCategory(filterState.categoryIds.length > 0 ? catId : 'all');
                        fetchAndRenderProducts(currentFilter, true);
                    });
                });
            }
        } catch (e) {
            console.error('Không thể tải danh mục:', e);
        }
    };

    /**
     * Fetch products from API and render
     * @param {string} filter - 'all', 'best-seller', 'newest'
     * @param {boolean} reset - true to reset pagination, false to load next page
     */
    const fetchAndRenderProducts = async (filter, reset = true) => {
        if (isLoading) return;
        isLoading = true;

        if (reset) {
            currentPage = 0;
            loadedIds = new Set();
            containerEl.innerHTML = '';
            showState(loadingEl);
        }

        try {
            const userRole = localStorage.getItem('userRole');
            const isSeller = userRole === 'SELLER';

            let books = [];
            let total = 0;
            let totalPg = 1;

            if (isSeller) {
                // Seller mode: use seller API
                const response = await ApiService.Book.getSellerBooks('', null, currentPage, PAGE_SIZE);
                const pageData = response;
                books = Array.isArray(pageData) ? pageData : (pageData.content || []);
                total = pageData.totalElements != null ? pageData.totalElements : books.length;
                totalPg = pageData.totalPages != null ? pageData.totalPages : 1;
            } else {
                // Public mode: use public search API
                const sid = await resolveSellerId();
                if (!sid) {
                    if (reset) showState(emptyEl);
                    isLoading = false;
                    return;
                }

                // Build sort parameter based on filter
                let sortParam = 'latest';
                if (filter === 'best-seller') sortParam = 'latest';
                if (filter === 'newest') sortParam = 'latest';

                // Build params với filter
                const params = new URLSearchParams({
                    sellerIds: sid,
                    page: currentPage,
                    size: PAGE_SIZE,
                    sort: sortParam
                });

                // Thêm category filter
                if (filterState.categoryIds && filterState.categoryIds.length > 0) {
                    filterState.categoryIds.forEach(id => {
                        params.append('categoryIds', id);
                    });
                }

                // Thêm price range filter
                if (filterState.minPrice != null && filterState.minPrice > 0) {
                    params.set('minPrice', filterState.minPrice);
                }
                if (filterState.maxPrice != null && filterState.maxPrice > 0) {
                    params.set('maxPrice', filterState.maxPrice);
                }

                // Thêm rating filter
                if (filterState.minRating != null && filterState.minRating > 0) {
                    params.set('minRating', filterState.minRating);
                }

                const response = await fetch(`/api/books/search?${params}`, {
                    headers: getAuthHeaders()
                });

                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }

                const pageData = await response.json();
                books = pageData.content || [];
                total = pageData.totalElements != null ? pageData.totalElements : books.length;
                totalPg = pageData.totalPages != null ? pageData.totalPages : 1;
            }

            totalElements = total;
            totalPages = totalPg;

            if (books.length === 0 && reset) {
                showState(emptyEl);
                isLoading = false;
                return;
            }

            // Filter out duplicates using loadedIds Set
            const newBooks = books.filter(book => {
                if (!book || book.id == null) return false;
                if (loadedIds.has(book.id)) return false;
                loadedIds.add(book.id);
                return true;
            });

            if (newBooks.length > 0) {
                // Append new books to container
                containerEl.insertAdjacentHTML('beforeend', newBooks.map(renderProductCard).join(''));
            }

            // Show container
            showState(containerEl);

            // Update footer (progress + load-more button)
            updateFooter();

            // Increment page for next load
            currentPage++;

        } catch (err) {
            console.error('Failed to load products:', err);
            if (reset) {
                showState(errorEl);
            }
        } finally {
            isLoading = false;
        }
    };

    // ==========================================
    // TAB CLICK HANDLERS
    // ==========================================

    const handleTabClick = (tab, filter) => (event) => {
        event.preventDefault();
        if (currentFilter === filter) return;
        currentFilter = filter;
        activateTab(tab);
        fetchAndRenderProducts(filter, true);
    };

    if (tabAll) {
        tabAll.addEventListener('click', handleTabClick(tabAll, 'all'));
    }
    if (tabBestSeller) {
        tabBestSeller.addEventListener('click', handleTabClick(tabBestSeller, 'best-seller'));
    }
    if (tabNewest) {
        tabNewest.addEventListener('click', handleTabClick(tabNewest, 'newest'));
    }

    // ==========================================
    // FILTER HANDLERS
    // ==========================================

    // Click "Tất cả sản phẩm" - reset tất cả filters
    if (categoryAllLink) {
        categoryAllLink.addEventListener('click', (e) => {
            e.preventDefault();
            // Reset filter state
            filterState.categoryIds = [];
            filterState.minPrice = null;
            filterState.maxPrice = null;
            filterState.minRating = null;

            // Reset UI
            highlightCategory('all');
            if (priceMinInput) priceMinInput.value = '';
            if (priceMaxInput) priceMaxInput.value = '';
            if (rating5Checkbox) rating5Checkbox.checked = false;
            if (rating4Checkbox) rating4Checkbox.checked = false;

            fetchAndRenderProducts(currentFilter, true);
        });
    }

    // Áp dụng price filter
    if (btnApplyPrice) {
        btnApplyPrice.addEventListener('click', () => {
            const minVal = priceMinInput ? parseFloat(priceMinInput.value) : NaN;
            const maxVal = priceMaxInput ? parseFloat(priceMaxInput.value) : NaN;

            filterState.minPrice = (!isNaN(minVal) && minVal >= 0) ? minVal : null;
            filterState.maxPrice = (!isNaN(maxVal) && maxVal >= 0) ? maxVal : null;

            fetchAndRenderProducts(currentFilter, true);
        });
    }

    // Rating filter
    const handleRatingChange = () => {
        if (rating5Checkbox && rating5Checkbox.checked) {
            filterState.minRating = 5;
            if (rating4Checkbox) rating4Checkbox.checked = false;
        } else if (rating4Checkbox && rating4Checkbox.checked) {
            filterState.minRating = 4;
            if (rating5Checkbox) rating5Checkbox.checked = false;
        } else {
            filterState.minRating = null;
        }
        fetchAndRenderProducts(currentFilter, true);
    };

    if (rating5Checkbox) {
        rating5Checkbox.addEventListener('change', handleRatingChange);
    }
    if (rating4Checkbox) {
        rating4Checkbox.addEventListener('change', handleRatingChange);
    }

    // ==========================================
    // LOAD MORE HANDLER
    // ==========================================

    if (loadMoreBtn) {
        loadMoreBtn.addEventListener('click', () => {
            fetchAndRenderProducts(currentFilter, false);
        });
    }

    // ==========================================
    // BOOTSTRAP
    // ==========================================
    const init = () => {
        // Ensure "Trang chủ" tab is visually active
        if (tabAll) {
            activateTab(tabAll);
        }
        // Load categories
        loadCategories();
        // Load products
        fetchAndRenderProducts('all', true);
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
