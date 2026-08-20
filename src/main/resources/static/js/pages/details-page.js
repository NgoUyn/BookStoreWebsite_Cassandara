const initDetailsPage = () => {    if (!window.ApiService) {
        console.error('ApiService not available');
        return;
    }

    const addButton = document.getElementById('detail-add-to-cart-btn');
    const wishlistButton = document.getElementById('detail-toggle-wishlist-btn');
    const qtyInput = document.getElementById('detail-qty-input');
    if (!addButton || !qtyInput) {
        return;
    }

    const applyWishlistButtonState = (button, isSaved) => {
        if (!button) {
            return;
        }

        button.dataset.wishlistSaved = isSaved ? 'true' : 'false';
        button.setAttribute('aria-pressed', isSaved ? 'true' : 'false');
        button.className = isSaved
            ? 'flex-1 border border-red-500 bg-red-500 text-white text-lg font-bold py-3.5 px-6 rounded-lg shadow-sm flex items-center justify-center gap-2'
            : 'flex-1 border border-brand-orange text-brand-orange text-lg font-bold py-3.5 px-6 rounded-lg hover:bg-brand-orange-light transition shadow-sm flex items-center justify-center gap-2';
        button.innerHTML = isSaved
            ? '<svg class="w-6 h-6" fill="currentColor" viewBox="0 0 24 24"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"></path></svg><span>Da luu</span>'
            : '<svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"></path></svg><span>Luu yeu thich</span>';
    };

    const getWishlistBookPayload = () => ({
        id: Number(wishlistButton?.getAttribute('data-book-id')),
        title: wishlistButton?.getAttribute('data-book-title') || '',
        author: wishlistButton?.getAttribute('data-book-author') || '',
        price: Number(wishlistButton?.getAttribute('data-book-price') || 0),
        imageUrl: wishlistButton?.getAttribute('data-book-image') || '',
        categoryName: wishlistButton?.getAttribute('data-book-category') || 'Sach'
    });

    const ensureBuyer = (message) => {
        const auth = ApiService.getAuth(); // Gọi qua lõi đã được bọc thép

        if (!auth.userId || auth.role !== 'BUYER') {
            alert(message);
            window.location.href = '/main/auth';
            return null;
        }
        return auth.userId;
    };

    (async () => {
        await ApiService.Wishlist.bootstrap().catch(() => {});
        if (wishlistButton) {
            applyWishlistButtonState(wishlistButton, ApiService.Wishlist.isSaved(wishlistButton.getAttribute('data-book-id')));
        }
    })();

    addButton.addEventListener('click', async () => {
        const userId = ensureBuyer('Vui long dang nhap tai khoan BUYER de them vao gio hang.');
        if (!userId) {
            return;
        }

        const bookId = Number(addButton.getAttribute('data-book-id'));
        const quantity = Math.max(1, Number(qtyInput.value || 1));

        try {
            await ApiService.Cart.addItem(userId, {
                bookId,
                quantity
            });
            alert('Da them san pham vao gio hang.');
        } catch (error) {
            const message = error?.message || 'Them vao gio hang that bai.';
            alert(message);
        }
    });

    wishlistButton?.addEventListener('click', () => {
        const userId = ensureBuyer('Vui long dang nhap tai khoan BUYER de luu sach yeu thich.');
        if (!userId) {
            return;
        }

        (async () => {
            const result = await ApiService.Wishlist.toggle(getWishlistBookPayload(), userId);
            applyWishlistButtonState(wishlistButton, result.saved);
            alert(result.saved ? 'Da luu vao Wishlist.' : 'Da xoa khoi Wishlist.');
        })().catch((error) => {
            alert(error?.message || 'Khong the cap nhat Wishlist.');
        });
    });

    // ==================== REVIEW SYSTEM ====================
    
    const bookId = Number(addButton.getAttribute('data-book-id'));
    let currentReviewPage = 0;
    const reviewSize = 10;
    let currentEditingReviewId = null;

    const fetchReviewStats = async () => {
        try {
            const response = await fetch(`/api/reviews/book/${bookId}/stats`);
            if (response.ok) {
                const stats = await response.json();
                document.getElementById('avg-rating-text').textContent = stats.averageRating.toFixed(1);
                document.getElementById('total-reviews-text').textContent = stats.totalReviews;
                
                // Update stars in summary
                const starsContainer = document.getElementById('avg-stars-container');
                starsContainer.innerHTML = '';
                const fullStars = Math.floor(stats.averageRating);
                for (let i = 0; i < 5; i++) {
                    const star = document.createElement('span');
                    star.textContent = '★';
                    if (i < fullStars) {
                        star.className = 'text-yellow-400';
                    } else {
                        star.className = 'text-gray-300';
                    }
                    starsContainer.appendChild(star);
                }
            }
        } catch (error) {
            console.error('Error fetching review stats:', error);
        }
    };

    const fetchRatingDistribution = async () => {
        try {
            const response = await fetch(`/api/reviews/book/${bookId}/distribution`);
            if (response.ok) {
                const distribution = await response.json();
                updateRatingDistributionDisplay(distribution);
            }
        } catch (error) {
            console.error('Error fetching rating distribution:', error);
        }
    };

    const updateRatingDistributionDisplay = (distribution) => {
        const filters = document.getElementById('rating-filters');
        if (!filters) return;

        // Update the rating filter display with counts
        filters.querySelectorAll('[data-filter]').forEach(btn => {
            const filter = btn.dataset.filter;
            if (filter !== 'all') {
                const rating = parseInt(filter);
                const count = distribution[rating] || 0;
                const countSpan = btn.querySelector('.rating-count');
                if (countSpan) {
                    countSpan.textContent = `(${count})`;
                } else {
                    btn.innerHTML = `${btn.innerHTML} <span class="rating-count">(${count})</span>`;
                }
            } else {
                const totalCount = Object.values(distribution).reduce((a, b) => a + b, 0);
                const countSpan = btn.querySelector('.rating-count');
                if (countSpan) {
                    countSpan.textContent = `(${totalCount})`;
                } else {
                    btn.innerHTML = `${btn.innerHTML} <span class="rating-count">(${totalCount})</span>`;
                }
            }
        });
    };

    const getCurrentUserId = () => {
        const { userId } = ApiService.getAuth();
        return userId;
    };

    const fetchReviews = async (page = 0, append = false) => {
        const container = document.getElementById('reviews-list-container');
        const loadMoreBtnContainer = document.getElementById('load-more-reviews-container');
        
        try {
            const response = await fetch(`/api/reviews/book/${bookId}?page=${page}&size=${reviewSize}`);
            if (response.ok) {
                const data = await response.json();
                const reviews = data.content;
                const currentUserId = getCurrentUserId();
                
                if (!append) {
                    container.innerHTML = '';
                } else {
                    document.getElementById('reviews-loading')?.remove();
                }

                if (reviews.length === 0 && !append) {
                    container.innerHTML = '<div class="py-10 text-center text-gray-400 italic">Chưa có đánh giá nào cho sản phẩm này.</div>';
                    loadMoreBtnContainer.classList.add('hidden');
                    return;
                }

                reviews.forEach(review => {
                    const reviewEl = document.createElement('div');
                    reviewEl.className = 'py-6 border-b border-brand-border flex gap-4 review-item';
                    reviewEl.id = `review-${review.id}`;
                    
                    const initials = review.user.username.substring(0, 1).toUpperCase();
                    const dateStr = new Date(review.createdAt).toLocaleString('vi-VN');
                    const isOwner = currentUserId && review.user.id === currentUserId;
                    
                    let actionButtons = '';
                    if (isOwner) {
                        actionButtons = `
                            <div class="flex gap-2 ml-auto">
                                <button class="edit-review-btn text-sm px-3 py-1 bg-brand-orange text-white rounded hover:bg-brand-brown transition" data-review-id="${review.id}">
                                    Chỉnh sửa
                                </button>
                                <button class="delete-review-btn text-sm px-3 py-1 bg-red-500 text-white rounded hover:bg-red-600 transition" data-review-id="${review.id}">
                                    Xóa
                                </button>
                            </div>
                        `;
                    }
                    
                    reviewEl.innerHTML = `
                        <div class="w-12 h-12 rounded-full bg-brand-hero border border-brand-border flex-shrink-0 flex items-center justify-center font-bold text-brand-brown">${initials}</div>
                        <div class="flex-grow">
                            <div class="flex items-center gap-2 mb-1 justify-between">
                                <div class="flex items-center gap-2">
                                    <span class="font-bold text-brand-dark text-sm">${review.user.username}</span>
                                    <span class="bg-green-100 text-green-700 text-[10px] font-bold px-2 py-0.5 rounded flex items-center gap-1">
                                        <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg> Đã mua hàng
                                    </span>
                                    ${isOwner ? '<span class="bg-blue-100 text-blue-700 text-[10px] font-bold px-2 py-0.5 rounded">Đánh giá của bạn</span>' : ''}
                                </div>
                                ${actionButtons}
                            </div>
                            <div class="flex text-yellow-400 text-xs mb-2">${'★'.repeat(review.rating)}${'<span class="text-gray-300">★</span>'.repeat(5 - review.rating)}</div>
                            <div class="text-xs text-gray-400 mb-3">${dateStr}</div>
                            <p class="text-sm text-brand-dark leading-relaxed">${review.comment || 'Người dùng không để lại bình luận.'}</p>
                        </div>
                    `;
                    container.appendChild(reviewEl);
                    
                    // Add event listeners for edit/delete buttons
                    const editBtn = reviewEl.querySelector('.edit-review-btn');
                    const deleteBtn = reviewEl.querySelector('.delete-review-btn');
                    
                    if (editBtn) {
                        editBtn.addEventListener('click', () => {
                            populateFormForEdit(review);
                        });
                    }
                    
                    if (deleteBtn) {
                        deleteBtn.addEventListener('click', () => {
                            if (confirm('Bạn có chắc chắn muốn xóa đánh giá này?')) {
                                deleteReview(review.id);
                            }
                        });
                    }
                });

                if (data.last) {
                    loadMoreBtnContainer.classList.add('hidden');
                } else {
                    loadMoreBtnContainer.classList.remove('hidden');
                }
            }
        } catch (error) {
            console.error('Error fetching reviews:', error);
            if (!append) {
                container.innerHTML = '<div class="py-10 text-center text-red-400">Không thể tải đánh giá.</div>';
            }
        }
    };

    const populateFormForEdit = (review) => {
        if (!review) {
            alert('Không tìm thấy đánh giá để chỉnh sửa');
            return;
        }
        // Pre-fill the form with the review data
        document.getElementById('selected-rating').value = review.rating;
        document.getElementById('review-comment').value = review.comment || '';
        
        // Update star display
        setStarsVisual(review.rating);
        
        currentEditingReviewId = review.id;
        
        // Change form title and button
        document.querySelector('#review-form-container h3').textContent = 'Chỉnh sửa đánh giá của bạn';
        const submitBtn = document.querySelector('#review-form button[type="submit"]');
        if (submitBtn) {
            submitBtn.innerHTML = '<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path></svg><span>Cập nhật đánh giá</span>';
        }
        
        // Show and scroll to form
        document.getElementById('review-form-container').classList.remove('hidden');
        document.getElementById('review-form-container').scrollIntoView({ behavior: 'smooth' });
    };

    const setStarsVisual = (value) => {
        const stars = document.querySelectorAll('#star-rating-input .star');
        const ratingInput = document.getElementById('selected-rating');
        ratingInput.value = String(value || 5);
        stars.forEach((s, i) => {
            if (i < value) {
                s.classList.add('text-yellow-400', 'is-selected');
            } else {
                s.classList.remove('text-yellow-400', 'is-selected');
            }
        });
    };

    const deleteReview = async (reviewId) => {
        try {
            const response = await ApiService.fetchWithAuth(`/api/reviews/${reviewId}`, {
                method: 'DELETE'
            });

            if (response.ok) {
                alert('Xóa đánh giá thành công');
                // Remove the review from DOM
                const reviewEl = document.getElementById(`review-${reviewId}`);
                if (reviewEl) {
                    reviewEl.remove();
                }
                // Refresh stats
                await fetchReviewStats();
                await fetchRatingDistribution();
            } else {
                const errorText = await response.text();
                alert(errorText || 'Xóa đánh giá thất bại');
            }
        } catch (error) {
            alert(error.message || 'Đã có lỗi xảy ra');
        }
    };

    const resetReviewForm = () => {
        currentEditingReviewId = null;
        document.getElementById('review-form').reset();
        document.getElementById('selected-rating').value = '5';
        document.getElementById('review-comment').value = '';
        
        // Reset stars
        const stars = document.querySelectorAll('#star-rating-input .star');
        stars.forEach((s, i) => {
            if (i < 5) {
                s.classList.add('text-yellow-400', 'is-selected');
            } else {
                s.classList.remove('text-yellow-400', 'is-selected');
            }
        });
        
        // Reset form title and button
        document.querySelector('#review-form-container h3').textContent = 'Viết đánh giá của bạn';
        const submitBtn = document.querySelector('#review-form button[type="submit"]');
        if (submitBtn) {
            submitBtn.innerHTML = '<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path></svg><span>Gửi đánh giá</span>';
        }
    };

    const checkReviewEligibility = async () => {
        const formContainer = document.getElementById('review-form-container');
        const { userId, role } = ApiService.getAuth();

        // Only buyers may see and submit the review form
        if (!userId || !role || !role.includes('BUYER')) {
            // Show a lightweight CTA prompting login (instead of the form)
            formContainer.classList.remove('hidden');
            formContainer.innerHTML = `
                <div class="p-6 text-center">
                    <div class="font-bold mb-2">Đăng nhập để đánh giá sản phẩm</div>
                    <div class="text-sm text-gray-600 mb-4">Chỉ tài khoản Buyer mới được phép đánh giá sau khi mua hàng.</div>
                    <a href="/main/auth" class="inline-block bg-brand-orange text-white font-bold px-6 py-2 rounded-lg">Đăng nhập / Đăng ký</a>
                </div>
            `;
            return;
        }

        // If buyer, check server whether user has already reviewed this book
        try {
            const response = await ApiService.fetchWithAuth(`/api/reviews/book/${bookId}/user-review`);
            if (response.ok) {
                const data = await response.json();
                if (data.hasReviewed) {
                    populateFormForEdit(data.review);
                } else {
                    // New review: ensure defaults
                    currentEditingReviewId = null;
                    document.getElementById('selected-rating').value = '5';
                    document.getElementById('review-comment').value = '';
                    setStarsVisual(5);

                    const titleEl = document.querySelector('#review-form-container h3');
                    if (titleEl) titleEl.textContent = 'Viết đánh giá của bạn';
                    const submitBtn = document.querySelector('#review-form button[type="submit"]');
                    if (submitBtn) submitBtn.innerHTML = '<span>Gửi đánh giá</span>';
                }

                // Show form for buyers
                formContainer.classList.remove('hidden');
            } else {
                console.log('Could not fetch user-review status');
            }
        } catch (error) {
            console.log('Could not check review status:', error);
            // Show form as fallback for buyers
            formContainer.classList.remove('hidden');
        }
    };

    // Star rating input logic
    const stars = document.querySelectorAll('#star-rating-input .star');
    const ratingInput = document.getElementById('selected-rating');

    stars.forEach(star => {
        star.addEventListener('mouseover', () => {
            const val = parseInt(star.dataset.value);
            stars.forEach((s, i) => {
                if (i < val) s.classList.add('text-yellow-400');
                else s.classList.remove('text-yellow-400');
            });
        });

        star.addEventListener('click', () => {
            const val = parseInt(star.dataset.value);
            ratingInput.value = val;
            stars.forEach((s, i) => {
                if (i < val) {
                    s.classList.add('text-yellow-400');
                    s.classList.add('is-selected');
                } else {
                    s.classList.remove('text-yellow-400');
                    s.classList.remove('is-selected');
                }
            });
        });
    });

    document.getElementById('star-rating-input').addEventListener('mouseleave', () => {
        const currentVal = parseInt(ratingInput.value);
        stars.forEach((s, i) => {
            if (i < currentVal) s.classList.add('text-yellow-400');
            else s.classList.remove('text-yellow-400');
        });
    });

    // Review form submission
    document.getElementById('review-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const rating = parseInt(ratingInput.value);
        const comment = document.getElementById('review-comment').value?.trim() || '';

        try {
            let response;
            if (currentEditingReviewId) {
                // Update existing review
                response = await ApiService.fetchWithAuth(`/api/reviews/${currentEditingReviewId}`, {
                    method: 'PUT',
                    body: JSON.stringify({
                        bookId: bookId,
                        rating: rating,
                        comment: comment
                    })
                });
            } else {
                // Create new review
                response = await ApiService.fetchWithAuth('/api/reviews', {
                    method: 'POST',
                    body: JSON.stringify({
                        bookId: bookId,
                        rating: rating,
                        comment: comment
                    })
                });
            }

            if (response.ok) {
                alert(currentEditingReviewId ? 'Cập nhật đánh giá thành công!' : 'Cảm ơn bạn đã đánh giá sản phẩm!');
                resetReviewForm();
                document.getElementById('review-form-container').classList.add('hidden');
                // Refresh reviews
                currentReviewPage = 0;
                await fetchReviewStats();
                await fetchRatingDistribution();
                await fetchReviews(0);
            } else {
                const errorText = await response.text();
                alert(errorText || (currentEditingReviewId ? 'Cập nhật thất bại' : 'Gửi đánh giá thất bại.'));
            }
        } catch (error) {
            alert(error.message || 'Đã có lỗi xảy ra.');
        }
    });

    // Add cancel button handler if it exists
    const cancelBtn = document.querySelector('#review-form-container button[type="reset"], .cancel-review-btn');
    if (cancelBtn) {
        cancelBtn.addEventListener('click', () => {
            resetReviewForm();
            document.getElementById('review-form-container').classList.add('hidden');
        });
    }

    document.getElementById('load-more-reviews-btn').addEventListener('click', () => {
        currentReviewPage++;
        fetchReviews(currentReviewPage, true);
    });

    // Handle rating filter buttons
    const ratingFilterBtns = document.querySelectorAll('#rating-filters button');
    ratingFilterBtns.forEach(btn => {
        btn.addEventListener('click', async () => {
            const filter = btn.dataset.filter;
            const container = document.getElementById('reviews-list-container');
            
            if (filter === 'all') {
                currentReviewPage = 0;
                await fetchReviews(0);
            } else {
                const rating = parseInt(filter);
                try {
                    const response = await fetch(`/api/reviews/book/${bookId}/by-rating/${rating}?page=0&size=${reviewSize}`);
                    if (response.ok) {
                        const data = await response.json();
                        container.innerHTML = '';
                        const currentUserId = getCurrentUserId();
                        
                        if (data.content.length === 0) {
                            container.innerHTML = `<div class="py-10 text-center text-gray-400 italic">Không có đánh giá ${rating} sao cho sản phẩm này.</div>`;
                            return;
                        }
                        
                        data.content.forEach(review => {
                            // Reuse the review rendering logic from fetchReviews
                            const reviewEl = document.createElement('div');
                            reviewEl.className = 'py-6 border-b border-brand-border flex gap-4';
                            
                            const initials = review.user.username.substring(0, 1).toUpperCase();
                            const dateStr = new Date(review.createdAt).toLocaleString('vi-VN');
                            
                            reviewEl.innerHTML = `
                                <div class="w-12 h-12 rounded-full bg-brand-hero border border-brand-border flex-shrink-0 flex items-center justify-center font-bold text-brand-brown">${initials}</div>
                                <div class="flex-grow">
                                    <div class="flex items-center gap-2 mb-1">
                                        <span class="font-bold text-brand-dark text-sm">${review.user.username}</span>
                                        <span class="bg-green-100 text-green-700 text-[10px] font-bold px-2 py-0.5 rounded flex items-center gap-1">
                                            <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg> Đã mua hàng
                                        </span>
                                    </div>
                                    <div class="flex text-yellow-400 text-xs mb-2">${'★'.repeat(review.rating)}${'<span class="text-gray-300">★</span>'.repeat(5 - review.rating)}</div>
                                    <div class="text-xs text-gray-400 mb-3">${dateStr}</div>
                                    <p class="text-sm text-brand-dark leading-relaxed">${review.comment || 'Người dùng không để lại bình luận.'}</p>
                                </div>
                            `;
                            container.appendChild(reviewEl);
                        });
                    }
                } catch (error) {
                    console.error('Error filtering reviews:', error);
                }
            }
        });
    });




    // ==================== BUY NOW BUTTON ====================
const buyNowButton = document.getElementById('detail-buy-now-btn');

buyNowButton?.addEventListener('click', async () => {
    const userId = ensureBuyer('Vui lòng đăng nhập tài khoản BUYER để mua ngay.');
    if (!userId) return;

    const bookId = Number(buyNowButton.getAttribute('data-book-id'));
    const quantity = Math.max(1, Number(qtyInput.value || 1));

    try {
        // Step 1: Thêm vào giỏ hàng
        await ApiService.Cart.addItem(userId, {
            bookId,
            quantity
        });
        
        // Step 2: Chuyển đến trang checkout
        window.location.href = '/main/checkout';
    } catch (error) {
        alert(error?.message || 'Thêm vào giỏ hàng thất bại.');
    }
});

    // Initialize Review Section
    fetchReviewStats();
    fetchRatingDistribution();
    fetchReviews(0);
    checkReviewEligibility();
};

// 🛡️ ÉP CHẠY NGAY LẬP TỨC NẾU TRANG ĐÃ LOAD XONG:
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initDetailsPage);
} else {
    initDetailsPage();
}