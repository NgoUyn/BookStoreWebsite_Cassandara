(() => {
    if (!window.ApiService) {
        return;
    }

    const liveContainer = document.getElementById('cart-items-live');
    const fallback1 = document.getElementById('cart-items-fallback-1');
    const fallback2 = document.getElementById('cart-items-fallback-2');
    const subtotalEl = document.getElementById('cart-summary-subtotal');
    const shippingEl = document.getElementById('cart-summary-shipping');
    const shipDiscountEl = document.getElementById('cart-summary-ship-discount');
    const voucherEl = document.getElementById('cart-summary-voucher');
    const totalEl = document.getElementById('cart-summary-total');
    const itemsLabelEl = document.getElementById('cart-summary-items-label');
    const checkoutBtn = document.getElementById('cart-checkout-btn');
    const recommendationsGrid = document.getElementById('cart-recommendations-grid');

    if (!liveContainer) {
        return;
    }

    const formatVnd = (value) => ApiService.formatVND(Math.max(0, Number(value) || 0));

    const buildGrouped = (items) => {
        const grouped = new Map();
        items.forEach((item) => {
            const key = item.sellerId || 'unknown';
            if (!grouped.has(key)) {
                grouped.set(key, {
                    sellerName: item.sellerName || 'Nha sach',
                    rows: []
                });
            }
            grouped.get(key).rows.push(item);
        });
        return Array.from(grouped.values());
    };

// TÃŒM VÃ€ THAY THáº¾ TOÃ€N Bá»˜ HÃ€M NÃ€Y:
    const renderCart = (cart) => {
        // 1. Xá»­ lÃ½ trÆ°á»ng há»£p giá» hÃ ng trá»‘ng
        if (!cart || !cart.items || cart.items.length === 0) {
            liveContainer.innerHTML = '';
            const emptyState = document.getElementById('cart-empty-state');
            if (emptyState) emptyState.classList.remove('hidden');
            return;
        }

        // 2. áº¨n thÃ´ng bÃ¡o trá»‘ng náº¿u cÃ³ hÃ ng
        const emptyState = document.getElementById('cart-empty-state');
        if (emptyState) emptyState.classList.add('hidden');

        // 3. Gom nhÃ³m sáº£n pháº©m vÃ  váº½ HTML chá»©a áº¢NH Sáº¢N PHáº¨M
        const grouped = buildGrouped(cart.items);

        liveContainer.innerHTML = grouped.map((shop) => {
            const rows = shop.rows.map((item) => {
                const currentItemId = item.id || item.itemId;
                return `
                    <div class="p-6 flex flex-col md:flex-row items-start md:items-center gap-5 md:gap-0 border-b last:border-b-0 border-gray-100 hover:bg-gray-50/50 transition-colors" data-item-id="${currentItemId}">
                        <div class="flex items-start gap-4 w-full md:w-5/12">
                            <div class="mt-4"><input type="checkbox" class="cart-checkbox row-checkbox" checked></div>
                            
                            ${item.imageUrl
                    ? `<img src="${item.imageUrl}" alt="Cover" class="w-20 aspect-[3/4] object-cover border-2 border-white rounded-md shadow-sm shrink-0">`
                    : `<div class="w-20 aspect-[3/4] bg-[#2c3e50] border-2 border-white shadow-sm shrink-0 flex items-center justify-center text-white text-center font-bold text-[8px] uppercase rounded-md overflow-hidden">BOOK</div>`
                }
                            
                            <div class="flex flex-col justify-center gap-1.5 mt-1">
                                <a href="/book/${item.bookId}" class="font-bold text-brand-dark text-sm leading-relaxed line-clamp-2 hover:text-brand-orange transition-colors">${item.title || 'KhÃ´ng cÃ³ tÃªn'}</a>
                                <span class="text-xs text-gray-500 font-medium">TÃ¡c giáº£: ${item.author || 'Äang cáº­p nháº­t'}</span>
                            </div>
                        </div>
                        <div class="w-full md:w-2/12 flex md:flex-col justify-between md:justify-center items-center md:text-center text-sm ml-8 md:ml-0">
                            <span class="md:hidden text-gray-500 font-medium">ÄÆ¡n giÃ¡:</span>
                            <div class="font-bold text-brand-dark">${formatVnd(item.unitPrice)}</div>
                        </div>
                        <div class="w-full md:w-2/12 flex justify-between md:justify-center items-center ml-8 md:ml-0">
                            <span class="md:hidden text-gray-500 text-sm font-medium">Sá»‘ lÆ°á»£ng:</span>
                            <div class="flex items-center border border-gray-200 rounded-md overflow-hidden shadow-sm bg-white">
                                <button type="button" data-action="decrease" data-item-id="${currentItemId}" data-current-qty="${item.quantity}" class="w-8 h-8 text-gray-500 hover:bg-gray-100 transition-colors font-bold outline-none border-r border-gray-200 flex items-center justify-center">-</button>
                                <input type="number" min="1" value="${item.quantity}" data-item-id="${currentItemId}" class="w-11 h-8 text-center text-sm font-bold text-brand-dark outline-none appearance-none cart-qty-input">
                                <button type="button" data-action="increase" data-item-id="${currentItemId}" data-current-qty="${item.quantity}" class="w-8 h-8 text-gray-500 hover:bg-gray-100 transition-colors font-bold outline-none border-l border-gray-200 flex items-center justify-center">+</button>
                            </div>
                        </div>
                        <div class="w-full md:w-2/12 flex justify-between md:justify-center items-center ml-8 md:ml-0">
                            <span class="md:hidden text-gray-500 text-sm font-medium">ThÃ nh tiá»n:</span>
                            <div class="font-black text-brand-orange text-base">${formatVnd(item.lineTotal)}</div>
                        </div>
                        <div class="w-full md:w-1/12 flex justify-end md:justify-center items-center">
                            <button type="button" data-action="remove" data-item-id="${currentItemId}" class="text-gray-400 hover:text-red-500 transition-colors p-2 bg-white rounded-full hover:bg-red-50" title="XÃ³a sáº£n pháº©m">
                                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                            </button>
                        </div>
                    </div>
                `;
            }).join('');

            return `
                <div class="bg-white border border-gray-200/80 rounded-2xl shadow-sm overflow-hidden mb-6">
                    <div class="bg-gray-50/80 px-6 py-4 border-b border-gray-100 flex items-center gap-4">
                        <input type="checkbox" class="cart-checkbox shop-checkbox" checked>
                        <h3 class="font-bold text-brand-dark text-sm uppercase tracking-wide">${shop.sellerName}</h3>
                    </div>
                    ${rows}
                </div>
            `;
        }).join('');
    };

    // HÃ m tÃ­nh tá»•ng tiá»n dá»±a trÃªn cÃ¡c checkbox Ä‘Æ°á»£c chá»n
    const computeSelectedTotal = (cart) => {
        if (!cart || !cart.items || cart.items.length === 0) {
            return { selectedCount: 0, selectedAmount: 0 };
        }

        let selectedCount = 0;
        let selectedAmount = 0;

        document.querySelectorAll('.row-checkbox').forEach((checkbox) => {
            if (checkbox.checked) {
                const row = checkbox.closest('[data-item-id]');
                if (row) {
                    const itemId = Number(row.getAttribute('data-item-id'));
                    const item = cart.items.find(i => (i.id || i.itemId) === itemId);
                    if (item) {
                        selectedCount += (item.quantity || 0);
                        selectedAmount += (item.lineTotal || 0);
                    }
                }
            }
        });

        return { selectedCount, selectedAmount };
    };

    const updateSummary = (cart) => {
        const { selectedCount, selectedAmount } = computeSelectedTotal(cart);
        const subtotal = selectedAmount;
        const shippingFee = selectedCount > 0 ? 35000 : 0;
        const shipDiscount = subtotal >= 250000 ? 15000 : 0;
        const voucher = 0;
        const total = Math.max(0, subtotal + shippingFee - shipDiscount - voucher);

        if (itemsLabelEl) itemsLabelEl.textContent = `Tong tien hang (${selectedCount} san pham)`;
        if (subtotalEl) subtotalEl.textContent = formatVnd(subtotal);
        if (shippingEl) shippingEl.textContent = formatVnd(shippingFee);
        if (shipDiscountEl) shipDiscountEl.textContent = `-${formatVnd(shipDiscount)}`;
        if (voucherEl) voucherEl.textContent = `-${formatVnd(voucher)}`;
        if (totalEl) totalEl.textContent = formatVnd(total);
        if (checkoutBtn) {
            checkoutBtn.classList.toggle('pointer-events-none', selectedCount === 0);
            checkoutBtn.classList.toggle('opacity-60', selectedCount === 0);
        }
    };


    const fetchCart = async () => {
        let { userId, role, token } = window.ApiService.getAuth();

        // ðŸ›¡ï¸ Bá»ŒC THÃ‰P: Náº¿u localStorage máº¥t userId, tá»± Ä‘á»™ng báº» khÃ³a JWT Token Ä‘á»ƒ láº¥y láº¡i
        if (token && (!userId || !role)) {
            try {
                // Giáº£i mÃ£ payload cá»§a JWT
                const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
                const payload = JSON.parse(atob(base64));

                // Láº¥y thÃ´ng tin tá»« bÃªn trong Token
                userId = payload.userId || payload.id;
                role = payload.roles ? payload.roles[0] : (payload.role || 'BUYER');

                // KhÃ´i phá»¥c láº¡i vÃ o bá»™ nhá»› Ä‘á»ƒ dÃ¹ng cho cÃ¡c trang khÃ¡c
                if (userId) localStorage.setItem('userId', String(userId));
                if (role) localStorage.setItem('userRole', role.replace('ROLE_', '').toUpperCase());
            } catch (e) {
                console.warn("Lá»—i báº» khÃ³a Token:", e);
            }
        }

        // Chuáº©n hÃ³a role
        const normalizedRole = role ? role.replace('ROLE_', '').toUpperCase() : '';

        // Block cháº·t cháº½ náº¿u khÃ´ng pháº£i BUYER
        if (!userId || normalizedRole !== 'BUYER') {
            liveContainer.innerHTML = `
                <div class="bg-white border border-gray-100 rounded-2xl py-16 px-6 text-center shadow-[0_4px_20px_rgba(93,64,55,0.01)]">
                    <p class="text-gray-500 text-sm font-medium mb-5">Vui lÃ²ng Ä‘Äƒng nháº­p tÃ i khoáº£n NgÆ°á»i mua Ä‘á»ƒ quáº£n lÃ½ giá» hÃ ng.</p>
                    <a href="/main/auth" class="inline-flex items-center justify-center bg-brand-dark text-white text-xs font-bold tracking-widest uppercase px-6 py-3 rounded-lg hover:bg-brand-biscuit transition-all">
                        ÄÄƒng nháº­p ngay
                    </a>
                </div>
            `;
            updateSummary({ totalItems: 0, totalAmount: 0 });
            return null;
        }

        // ÄÃ£ cÃ³ ID sá»‘ 8 -> Gá»i API
        try {
            const cart = await ApiService.Cart.get(userId);
            // LÆ°u cart data Ä‘á»ƒ checkbox change handler cÃ³ thá»ƒ dÃ¹ng
            window.__cartData = cart;
            renderCart(cart);
            updateSummary(cart);
            return cart;
        } catch (error) {
            console.error("Lá»—i fetch giá» hÃ ng:", error);
        }

    };
    const patchQuantity = async (itemId, quantity) => {
        const { userId } = ApiService.getAuth();
        await ApiService.Cart.updateItem(userId, itemId, quantity);
    };

    const removeItem = async (itemId) => {
        const { userId } = ApiService.getAuth();
        await ApiService.Cart.removeItem(userId, itemId);
    };

    const addToCart = async (bookId, quantity = 1) => {
        let { userId, role, token } = window.ApiService.getAuth();

        // ðŸ›¡ï¸ Bá»ŒC THÃ‰P: Tá»± Ä‘á»™ng láº¥y láº¡i userId tá»« Token khi ngÆ°á»i dÃ¹ng báº¥m ThÃªm sáº£n pháº©m
        if (token && (!userId || !role)) {
            try {
                const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
                const payload = JSON.parse(atob(base64));
                userId = payload.userId || payload.id;
                role = payload.roles ? payload.roles[0] : (payload.role || 'BUYER');
            } catch (e) {}
        }

        const normalizedRole = role ? role.replace('ROLE_', '').toUpperCase() : '';

        if (!userId || normalizedRole !== 'BUYER') {
            alert('Vui lÃ²ng Ä‘Äƒng nháº­p tÃ i khoáº£n NgÆ°á»i mua Ä‘á»ƒ thÃªm vÃ o giá» hÃ ng.');
            window.location.href = '/main/auth';
            return;
        }

        await ApiService.Cart.addItem(userId, { bookId: Number(bookId), quantity });
    };

    const renderRecommendations = (books) => {
        if (!recommendationsGrid) {
            return;
        }

        if (!Array.isArray(books) || books.length === 0) {
            recommendationsGrid.innerHTML = '<div class="col-span-full rounded-2xl border border-dashed border-brand-accent bg-brand-cream/30 px-5 py-6 text-sm text-gray-500 font-semibold">ChÆ°a cÃ³ gá»£i Ã½ phÃ¹ há»£p.</div>';
            return;
        }

        recommendationsGrid.innerHTML = books.slice(0, 6).map((book) => `
            <article class="product-card group relative h-full flex flex-col bg-white rounded-xl p-3 shadow-soft border border-brand-accent cursor-pointer" data-detail-url="/book/${book.id}">
                <div class="relative w-full aspect-3/4 bg-brand-cream rounded-lg mb-3 flex items-center justify-center overflow-hidden border border-brand-accent">
                    <img src="${book.imageUrl || 'https://via.placeholder.com/240x320?text=No+Cover'}" alt="${book.title || ''}" class="book-cover w-full h-full object-cover transition-transform duration-500" onerror="this.src='https://via.placeholder.com/240x320?text=Error'" />
                </div>
                <div class="flex flex-col grow">
                    <h3 class="text-xs md:text-sm font-bold text-brand-dark leading-snug mb-1 line-clamp-2 group-hover:text-brand-biscuit transition-colors">${book.title || 'KhÃ´ng rÃµ'}</h3>
                    <div class="text-[10px] text-gray-500 mb-2 line-clamp-1">${book.author || 'Äang cáº­p nháº­t'}</div>
                    <div class="mt-auto flex items-center justify-between gap-2">
                        <span class="text-brand-orange font-black text-sm">${formatVnd(book.price)}</span>
                        <button type="button" data-add-to-cart-recommendation="true" data-book-id="${book.id}" class="w-9 h-9 bg-brand-biscuit text-white rounded-full shadow-lg flex items-center justify-center hover:bg-brand-dark transition-colors">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"></path></svg>
                        </button>
                    </div>
                </div>
            </article>
        `).join('');
    };

    const loadRecommendations = async () => {
        if (!recommendationsGrid) {
            return;
        }

        recommendationsGrid.innerHTML = '<div class="col-span-full rounded-2xl border border-dashed border-brand-accent bg-brand-cream/30 px-5 py-6 text-sm text-gray-500 font-semibold">Äang táº£i gá»£i Ã½ sáº£n pháº©m...</div>';

        try {
            const books = await ApiService.Book.bestSellers(6);
            renderRecommendations(Array.isArray(books) ? books : []);
        } catch (error) {
            recommendationsGrid.innerHTML = '<div class="col-span-full rounded-2xl border border-dashed border-red-200 bg-red-50 px-5 py-6 text-sm text-red-600 font-semibold">KhÃ´ng táº£i Ä‘Æ°á»£c gá»£i Ã½ sáº£n pháº©m.</div>';
        }
    };

    liveContainer.addEventListener('click', async (event) => {
        const button = event.target.closest('button[data-action]');
        if (!button) {
            return;
        }

        const action = button.getAttribute('data-action');
        const itemId = Number(button.getAttribute('data-item-id'));
        if (!itemId) {
            return;
        }

        try {
            if (action === 'remove') {
                await removeItem(itemId);
            } else {
                const current = Number(button.getAttribute('data-current-qty') || 1);
                const nextQty = action === 'increase' ? current + 1 : Math.max(1, current - 1);
                await patchQuantity(itemId, nextQty);
            }
            await fetchCart();
        } catch (error) {
            const message = error?.message || 'Cap nhat gio hang that bai.';
            alert(message);
        }
    });

    liveContainer.addEventListener('change', async (event) => {
        const input = event.target.closest('.cart-qty-input');
        if (!input) {
            return;
        }

        const itemId = Number(input.getAttribute('data-item-id'));
        const nextQty = Math.max(1, Number(input.value || 1));
        input.value = String(nextQty);
        try {
            await patchQuantity(itemId, nextQty);
            await fetchCart();
        } catch (error) {
            const message = error?.message || 'Cap nhat gio hang that bai.';
            alert(message);
        }
    });

    recommendationsGrid?.addEventListener('click', async (event) => {
        const button = event.target.closest('button[data-add-to-cart-recommendation="true"]');
        if (button) {
            event.preventDefault();
            event.stopPropagation();
            const bookId = button.getAttribute('data-book-id');
            if (!bookId) {
                return;
            }

            try {
                await addToCart(bookId, 1);
                await fetchCart();
                if (window.UIEnhancements && window.UIEnhancements.ToastService) {
                    window.UIEnhancements.ToastService.success('ÄÃ£ thÃªm sáº£n pháº©m vÃ o giá» hÃ ng.');
                }
            } catch (error) {
                if (window.UIEnhancements && window.UIEnhancements.ApiErrorHandler) {
                    window.UIEnhancements.ApiErrorHandler.handle(error, 'ThÃªm vÃ o giá» hÃ ng tháº¥t báº¡i.');
                } else {
                    const message = error?.message || 'ThÃªm vÃ o giá» hÃ ng tháº¥t báº¡i.';
                    alert(message);
                }
            }
            return;
        }

        const card = event.target.closest('.product-card[data-detail-url]');
        if (card && !event.target.closest('button, a, input, label, select, textarea')) {
            window.location.href = card.getAttribute('data-detail-url');
        }
    });

    // HÃ m cáº­p nháº­t tá»•ng tiá»n khi checkbox thay Ä‘á»•i
    const refreshSummaryFromCheckboxes = () => {
        // Láº¥y cart tá»« biáº¿n Ä‘Ã£ lÆ°u (náº¿u cÃ³) hoáº·c tá»« DOM
        const cartData = window.__cartData;
        if (cartData) {
            updateSummary(cartData);
        }
    };

    document.getElementById('selectAll')?.addEventListener('change', (e) => {
        const checked = e.target.checked;
        document.querySelectorAll('.shop-checkbox, .row-checkbox').forEach((cb) => {
            cb.checked = checked;
        });
        refreshSummaryFromCheckboxes();
    });

    // Láº¯ng nghe sá»± kiá»‡n thay Ä‘á»•i trÃªn táº¥t cáº£ checkbox (row + shop)
    liveContainer.addEventListener('change', (event) => {
        const checkbox = event.target.closest('.row-checkbox, .shop-checkbox');
        if (!checkbox) return;

        // Náº¿u lÃ  shop-checkbox, Ä‘á»“ng bá»™ cÃ¡c row-checkbox trong cÃ¹ng shop
        if (checkbox.classList.contains('shop-checkbox')) {
            const shopSection = checkbox.closest('.bg-white.border');
            if (shopSection) {
                const isChecked = checkbox.checked;
                shopSection.querySelectorAll('.row-checkbox').forEach((cb) => {
                    cb.checked = isChecked;
                });
            }
        }

        // Cáº­p nháº­t tráº¡ng thÃ¡i "Chá»n táº¥t cáº£"
        const allRows = document.querySelectorAll('.row-checkbox');
        const checkedRows = document.querySelectorAll('.row-checkbox:checked');
        const selectAllCheckbox = document.getElementById('selectAll');
        if (selectAllCheckbox) {
            selectAllCheckbox.checked = allRows.length > 0 && allRows.length === checkedRows.length;
        }

        refreshSummaryFromCheckboxes();
    });


    // ==========================================
    // AGE CHECK BEFORE CHECKOUT
    // ==========================================

    /**
     * Kiá»ƒm tra Ä‘á»™ tuá»•i ngÆ°á»i dÃ¹ng trÆ°á»›c khi cho phÃ©p checkout
     * YÃªu cáº§u: ngÆ°á»i dÃ¹ng pháº£i tá»« 13 tuá»•i trá»Ÿ lÃªn
     * Sá»­ dá»¥ng ValidationUtils.validateDateOfBirth Ä‘á»ƒ Ä‘áº£m báº£o Ä‘á»“ng nháº¥t logic
     */
    async function checkAgeBeforeCheckout() {
        try {
            const headers = ApiService.getHeaders();
            const response = await fetch('/buyer/profile/api/profile', { headers });
            if (!response.ok) return true; // Náº¿u khÃ´ng láº¥y Ä‘Æ°á»£c profile, váº«n cho checkout

            const profile = await response.json();
            const dob = profile.dateOfBirth;
            if (!dob) {
                // Náº¿u chÆ°a cÃ³ ngÃ y sinh, cáº£nh bÃ¡o nhÆ°ng váº«n cho checkout
                Swal.fire({
                    icon: 'warning',
                    title: 'Thiáº¿u thÃ´ng tin',
                    text: 'Vui lÃ²ng cáº­p nháº­t ngÃ y sinh trong há»“ sÆ¡ Ä‘á»ƒ xÃ¡c thá»±c Ä‘á»™ tuá»•i.',
                    confirmButtonText: 'Äá»ƒ sau'
                });
                return true;
            }

            // Sá»­ dá»¥ng ValidationUtils.validateDateOfBirth vá»›i minAge = 13
            if (window.ValidationUtils) {
                const result = ValidationUtils.validateDateOfBirth(dob, 13);
                if (!result.valid) {
                    Swal.fire({
                        icon: 'error',
                        title: 'KhÃ´ng Ä‘á»§ Ä‘iá»u kiá»‡n',
                        text: result.error,
                        confirmButtonText: 'ÄÃ£ hiá»ƒu'
                    });
                    return false;
                }
                return true;
            }

            // Fallback náº¿u ValidationUtils chÆ°a load
            const birthDate = new Date(dob);
            const today = new Date();
            let age = today.getFullYear() - birthDate.getFullYear();
            const monthDiff = today.getMonth() - birthDate.getMonth();
            if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birthDate.getDate())) {
                age--;
            }

            if (age < 13) {
                Swal.fire({
                    icon: 'error',
                    title: 'KhÃ´ng Ä‘á»§ Ä‘iá»u kiá»‡n',
                    text: 'Báº¡n pháº£i tá»« 13 tuá»•i trá»Ÿ lÃªn Ä‘á»ƒ thá»±c hiá»‡n giao dá»‹ch trÃªn BOOKOM.',
                    confirmButtonText: 'ÄÃ£ hiá»ƒu'
                });
                return false;
            }

            return true;
        } catch (error) {
            console.error('Age check error:', error);
            return true; // Náº¿u cÃ³ lá»—i, váº«n cho checkout
        }
    }

    // ThÃªm age check vÃ o nÃºt checkout
    if (checkoutBtn) {
        checkoutBtn.addEventListener('click', async (e) => {
            const selectedItemIds = [];
            document.querySelectorAll('.row-checkbox:checked').forEach((cb) => {
                const row = cb.closest('[data-item-id]');
                if (row) {
                    const id = Number(row.getAttribute('data-item-id'));
                    if (id) selectedItemIds.push(id);
                }
            });

            if (selectedItemIds.length === 0) {
                if (window.Swal) {
                    Swal.fire({
                        icon: 'warning',
                        title: 'Chưa chọn sản phẩm',
                        text: 'Vui lòng chọn ít nhất một sản phẩm trong giỏ hàng để thanh toán.',
                        confirmButtonText: 'Đã hiểu'
                    });
                } else {
                    alert('Vui lòng chọn ít nhất một sản phẩm trong giỏ hàng để thanh toán.');
                }
                e.preventDefault();
                e.stopPropagation();
                return false;
            }

            sessionStorage.setItem('checkout_selected_item_ids', JSON.stringify(selectedItemIds));
            const isAllowed = await checkAgeBeforeCheckout();
            if (!isAllowed) {
                e.preventDefault();
                e.stopPropagation();
                return false;
            }
            // Náº¿u Ä‘á»§ tuá»•i, cho phÃ©p chuyá»ƒn hÆ°á»›ng bÃ¬nh thÆ°á»ng
            window.location.href = '/main/checkout';
        });
    }

    fetchCart().catch((error) => {
        const message = error?.message || 'Khong the tai gio hang.';
        liveContainer.innerHTML = `
            <div class="bg-white border border-red-200 text-red-600 rounded-xl shadow-sm p-8 text-center font-semibold">
                ${message}
            </div>
        `;
        if (fallback1) fallback1.classList.add('hidden');
        if (fallback2) fallback2.classList.add('hidden');
        updateSummary({ totalItems: 0, totalAmount: 0 });
    });

    loadRecommendations();
})();
