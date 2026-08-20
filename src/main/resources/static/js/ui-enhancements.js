/**
 * UI Enhancement Utilities for BOOKOM
 * Provides modern UX patterns: toasts, skeletons, animations, etc.
 */

// ============================================================
// 1. TOAST NOTIFICATIONS
// ============================================================

const ToastService = (() => {
  const container = (() => {
    let el = document.getElementById('toast-container');
    if (!el) {
      el = document.createElement('div');
      el.id = 'toast-container';
      el.className = 'fixed bottom-6 right-6 z-[9999] space-y-3 pointer-events-none';
      document.body.appendChild(el);
    }
    return el;
  })();

  const show = (message, type = 'info', duration = 3000, action = null) => {
    const toast = document.createElement('div');
    const icons = {
      success: `<svg class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"></path></svg>`,
      error: `<svg class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"></path></svg>`,
      info: `<svg class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd"></path></svg>`,
      warning: `<svg class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd"></path></svg>`
    };

    const bgColor = {
      success: 'bg-green-500',
      error: 'bg-red-500',
      info: 'bg-blue-500',
      warning: 'bg-yellow-500'
    }[type] || 'bg-blue-500';

    toast.className = `
      ${bgColor} text-white px-6 py-4 rounded-lg shadow-lg flex items-center gap-3
      animate-slide-in-up pointer-events-auto transition-all duration-300
      max-w-sm
    `;

    toast.innerHTML = `
      <div class="flex-shrink-0">${icons[type]}</div>
      <div class="flex-1">
        <p class="font-semibold text-sm">${message}</p>
      </div>
      ${action ? `<button class="ml-4 hover:opacity-75 transition">${action.label}</button>` : ''}
      <button class="close-toast ml-4 hover:opacity-75 transition">
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
        </svg>
      </button>
    `;

    container.appendChild(toast);

    if (action) {
      toast.querySelector('button:not(.close-toast)').addEventListener('click', action.callback);
    }

    toast.querySelector('.close-toast').addEventListener('click', () => removeToast(toast));

    const timeout = setTimeout(() => removeToast(toast), duration);
    toast.addEventListener('mouseenter', () => clearTimeout(timeout));
  };

  const removeToast = (toast) => {
    toast.classList.add('opacity-0', '-translate-y-2');
    setTimeout(() => toast.remove(), 300);
  };

  return { show, success: (msg, dur) => show(msg, 'success', dur), error: (msg, dur) => show(msg, 'error', dur), info: (msg, dur) => show(msg, 'info', dur), warning: (msg, dur) => show(msg, 'warning', dur) };
})();

// ============================================================
// 2. SKELETON LOADER
// ============================================================

const SkeletonLoader = {
  createCartItemSkeleton: () => `
    <div class="bg-white border border-brand-accent rounded-xl shadow-sm p-5 animate-pulse">
      <div class="flex gap-4">
        <div class="w-20 aspect-[3/4] bg-gray-300 rounded-lg flex-shrink-0"></div>
        <div class="flex-1 space-y-3">
          <div class="h-4 bg-gray-300 rounded w-3/4"></div>
          <div class="h-3 bg-gray-300 rounded w-1/2"></div>
          <div class="h-3 bg-gray-300 rounded w-2/3"></div>
        </div>
      </div>
    </div>
  `,

  showFor: (element, count = 3) => {
    element.innerHTML = Array(count).fill(SkeletonLoader.createCartItemSkeleton()).join('');
  }
};

// ============================================================
// 3. BUTTON LOADING STATE
// ============================================================

const ButtonLoading = {
  start: (btn) => {
    btn.dataset.originalText = btn.innerHTML;
    btn.disabled = true;
    btn.classList.add('opacity-75', 'cursor-not-allowed');
    btn.innerHTML = `
      <div class="flex items-center justify-center gap-2">
        <svg class="animate-spin w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
        </svg>
        <span>Đang xử lý...</span>
      </div>
    `;
  },

  end: (btn) => {
    btn.disabled = false;
    btn.classList.remove('opacity-75', 'cursor-not-allowed');
    btn.innerHTML = btn.dataset.originalText;
  }
};

// ============================================================
// 4. ANIMATED NUMBER UPDATES
// ============================================================

const AnimateNumber = (element, targetValue, duration = 500, formatter = null) => {
  const startValue = parseFloat(element.textContent.replace(/[^0-9.-]+/g, '')) || 0;
  const startTime = Date.now();
  const easeOutQuad = (t) => t * (2 - t);

  const animate = () => {
    const elapsed = Date.now() - startTime;
    const progress = Math.min(elapsed / duration, 1);
    const easeProgress = easeOutQuad(progress);
    const currentValue = startValue + (targetValue - startValue) * easeProgress;

    element.textContent = formatter ? formatter(currentValue) : currentValue.toFixed(0);

    if (progress < 1) {
      requestAnimationFrame(animate);
    }
  };

  animate();
};

// ============================================================
// 5. FORM VALIDATION
// ============================================================

const FormValidator = {
  validators: {
    email: (val) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val),
    phone: (val) => /^(\+84|0)[0-9]{9,10}$/.test(val),
    required: (val) => val.trim().length > 0,
    minLength: (min) => (val) => val.length >= min,
    maxLength: (max) => (val) => val.length <= max
  },

  validate: (input, rules) => {
    const errors = [];
    for (const [rule, value] of Object.entries(rules)) {
      const validator = FormValidator.validators[rule];
      if (validator && typeof validator === 'function') {
        if (!validator(input.value)) {
          errors.push(rule);
        }
      }
    }
    return errors;
  },

  showError: (input, message) => {
    input.classList.add('border-red-500', 'bg-red-50');
    let error = input.parentElement.querySelector('.error-message');
    if (!error) {
      error = document.createElement('span');
      error.className = 'error-message text-xs text-red-600 mt-1 block';
      input.parentElement.appendChild(error);
    }
    error.textContent = message;
  },

  clearError: (input) => {
    input.classList.remove('border-red-500', 'bg-red-50');
    const error = input.parentElement.querySelector('.error-message');
    if (error) error.remove();
  }
};

// ============================================================
// 6. DEBOUNCE & THROTTLE
// ============================================================

const Debounce = (func, delay) => {
  let timeout;
  return (...args) => {
    clearTimeout(timeout);
    timeout = setTimeout(() => func(...args), delay);
  };
};

const Throttle = (func, delay) => {
  let lastCall = 0;
  return (...args) => {
    const now = Date.now();
    if (now - lastCall >= delay) {
      lastCall = now;
      func(...args);
    }
  };
};

// ============================================================
// 7. MODAL MANAGER
// ============================================================

const ModalManager = {
  open: (modalEl) => {
    modalEl.classList.remove('hidden');
    setTimeout(() => modalEl.classList.add('opacity-100'), 0);
    document.body.style.overflow = 'hidden';
  },

  close: (modalEl) => {
    modalEl.classList.remove('opacity-100');
    setTimeout(() => modalEl.classList.add('hidden'), 300);
    document.body.style.overflow = '';
  },

  setupBackdropClose: (modalEl, backdropSelector) => {
    const backdrop = modalEl.querySelector(backdropSelector);
    if (backdrop) {
      backdrop.addEventListener('click', () => ModalManager.close(modalEl));
    }
  }
};

// ============================================================
// 8. API ERROR HANDLER
// ============================================================

const ApiErrorHandler = {
  handle: (error, defaultMessage = 'Đã có lỗi xảy ra.') => {
    let message = defaultMessage;
    
    if (error && typeof error === 'object') {
      if (error.response && error.response.data && error.response.data.message) {
        message = error.response.data.message;
      } else if (error.message) {
        message = error.message;
      }
    } else if (typeof error === 'string') {
      message = error;
    }
    
    // Attempt to translate common errors for better UX
    const lowerMsg = message.toLowerCase();
    if (lowerMsg.includes('stock') || lowerMsg.includes('tồn kho') || lowerMsg.includes('quantity exceeds') || lowerMsg.includes('vượt')) {
      message = 'Sản phẩm vượt quá số lượng tồn kho!';
    }
    
    ToastService.error(message, 5000);
    return message;
  }
};

// Update cart badge in header based on current buyer cart
document.addEventListener('DOMContentLoaded', async () => {
  const updateCartBadge = async () => {
    try {
      if (!window.ApiService) return;
      const auth = ApiService.getAuth();
      const badgeEl = document.querySelector('header a[href="/main/cart"] .relative > span') || document.querySelector('header a[href="/main/cart"] .absolute');
      if (!auth.userId || auth.role !== 'BUYER') {
        if (badgeEl) badgeEl.textContent = '';
        return;
      }
      const cart = await ApiService.Cart.get(auth.userId);
      const count = cart?.totalItems || 0;
      // Try to find the badge element in header and set text
      const headerBadge = document.querySelector('header a[href="/main/cart"] .absolute, header a[href="/main/cart"] .-top-2');
      if (headerBadge) {
        headerBadge.textContent = String(count);
      } else {
        // Create a small badge if not present
        const cartLink = document.querySelector('header a[href="/main/cart"] .relative');
        if (cartLink) {
          let span = cartLink.querySelector('.cart-badge-count');
          if (!span) {
            span = document.createElement('span');
            span.className = 'cart-badge-count absolute -top-2 -right-2 bg-brand-dark text-white text-[10px] rounded-full h-4 w-4 flex items-center justify-center font-bold shadow-sm';
            cartLink.appendChild(span);
          }
          span.textContent = String(count);
        }
      }
    } catch (e) {
      // ignore
    }
  };

  updateCartBadge();
  // update periodically and on storage events
  window.addEventListener('storage', (e) => { if (e.key === 'userId' || e.key === 'accessToken' || e.key === 'userRole') updateCartBadge(); });
  setInterval(updateCartBadge, 30 * 1000);
});

// ============================================================
// 9. EXPORT
// ============================================================

window.UIEnhancements = {
  ToastService,
  SkeletonLoader,
  ButtonLoading,
  AnimateNumber,
  FormValidator,
  Debounce,
  Throttle,
  ModalManager,
  ApiErrorHandler
};

// ============================================================
// 10. GLOBAL STYLES (Add to head or main CSS)
// ============================================================

// CSS to include:
/*
@keyframes slide-in-up {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes fade-out {
  from { opacity: 1; }
  to { opacity: 0; }
}

.animate-slide-in-up {
  animation: slide-in-up 0.3s ease-out;
}

.animate-fade-out {
  animation: fade-out 0.3s ease-out;
}
*/
