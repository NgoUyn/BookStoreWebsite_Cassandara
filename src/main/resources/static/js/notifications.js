(function(){
    const root = document.getElementById('notificationRoot');
    if (!root) return;

    const notifBtn = document.getElementById('notifBtn');
    const notifDrop = document.getElementById('notifDrop');
    const notifList = document.getElementById('notifList');
    const notifBadge = document.getElementById('notifBadge');
    const markAllBtn = document.getElementById('markAllReadBtn');

    const API_UNREAD = '/api/notifications/me/unread-count';
    const API_LIST = '/api/notifications/me?page=0&size=20';
    const API_READ_ALL = '/api/notifications/me/read-all';
    const API_MARK = (id) => `/api/notifications/me/${id}/read`;
    const SSE_URL = '/api/notifications/me/subscribe';
    const SSE_MAX_RETRY = 5;

    let es = null;

    /**
     * Header xác thực cho các API notification (trước đây chỉ gửi Content-Type -> 401).
     * Ưu tiên ApiService.getHeaders() để dùng chung logic lấy token/X-User-Id toàn app.
     */
    const authHeaders = () => {
        if (window.ApiService && typeof window.ApiService.getHeaders === 'function') {
            try { return window.ApiService.getHeaders(); } catch (e) { /* fallback bên dưới */ }
        }
        const userId = localStorage.getItem('userId') || sessionStorage.getItem('userId') || '';
        const token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');
        const headers = { 'Content-Type': 'application/json', 'X-User-Id': userId };
        if (token) headers['Authorization'] = `Bearer ${token}`;
        return headers;
    };

    const getToken = () => localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');

    const showBadge = (n) => {
        if (!notifBadge) return;
        if (!n || Number(n) <= 0) {
            notifBadge.classList.add('hidden');
            notifBadge.textContent = '0';
        } else {
            notifBadge.classList.remove('hidden');
            notifBadge.textContent = String(n);
        }
    };

    const formatRow = (item) => {
        const div = document.createElement('div');
        div.className = 'p-3 border-b hover:bg-gray-50 flex items-start gap-3';
        const left = document.createElement('div');
        left.innerHTML = `<div class="font-bold">${escapeHtml(item.title)}</div><div class="text-sm text-gray-600">${escapeHtml(item.message)}</div>`;
        const right = document.createElement('div');
        right.className = 'ml-auto text-xs text-gray-500';
        right.innerHTML = item.isRead ? 'Đã đọc' : '<span class="text-blue-600">Mới</span>';

        div.appendChild(left);
        div.appendChild(right);
        div.addEventListener('click', async () => {
            try {
                await fetch(API_MARK(item.id), { method: 'PATCH', headers: authHeaders() });
                // mark visually
                right.innerHTML = 'Đã đọc';
                // decrement badge
                const cur = Number(notifBadge.textContent || '0');
                showBadge(Math.max(0, cur - (item.isRead ? 0 : 1)));
            } catch (e) {
                console.error('Mark read failed', e);
            }
        });
        return div;
    };

    const escapeHtml = (s) => {
        if (s == null) return '';
        return String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;');
    };

    const loadUnread = async () => {
        try {
            const res = await fetch(API_UNREAD, { headers: authHeaders() });
            if (!res.ok) return showBadge(0);
            const data = await res.json();
            showBadge(data?.unread || 0);
        } catch (e) {
            showBadge(0);
        }
    };

    const loadList = async () => {
        notifList.innerHTML = '<div class="p-4 text-sm text-gray-500">Đang tải…</div>';
        try {
            const res = await fetch(API_LIST, { headers: authHeaders() });
            if (!res.ok) { notifList.innerHTML = '<div class="p-4 text-sm text-red-600">Không tải được.</div>'; return; }
            const data = await res.json();
            const items = Array.isArray(data?.items) ? data.items : [];
            if (items.length === 0) {
                notifList.innerHTML = '<div class="p-4 text-sm text-gray-500">Không có thông báo.</div>';
                return;
            }
            notifList.innerHTML = '';
            items.forEach(it => notifList.appendChild(formatRow(it)));
        } catch (e) {
            notifList.innerHTML = '<div class="p-4 text-sm text-red-600">Lỗi khi tải.</div>';
        }
    };

    const markAllRead = async () => {
        try {
            const res = await fetch(API_READ_ALL, { method: 'PATCH', headers: authHeaders() });
            if (!res.ok) throw new Error('Failed');
            const json = await res.json();
            showBadge(0);
            loadList();
        } catch (e) {
            console.error('markAllRead', e);
        }
    };

    const toggleDrop = async () => {
        if (notifDrop.classList.contains('hidden')) {
            await loadList();
            notifDrop.classList.remove('hidden');
        } else {
            notifDrop.classList.add('hidden');
        }
    };

    let sseRetry = 0;
    let sseTimer = null;

    /** Retry có giới hạn + backoff luỹ tiến (2s, 4s, 8s, 16s, 30s) thay cho vòng lặp 5s vô hạn. */
    const scheduleReconnect = () => {
        if (sseRetry >= SSE_MAX_RETRY) {
            console.warn(`SSE: dừng kết nối lại sau ${sseRetry} lần thử.`);
            return;
        }
        const delay = Math.min(2000 * Math.pow(2, sseRetry), 30000);
        sseRetry += 1;
        clearTimeout(sseTimer);
        sseTimer = setTimeout(connectSse, delay);
    };

    const connectSse = () => {
        const token = getToken();
        if (!token) {
            // Chưa đăng nhập -> chắc chắn 401, không mở vòng reconnect vô hạn.
            console.debug('SSE: bỏ qua vì chưa đăng nhập.');
            return;
        }
        try {
            // EventSource không set được header -> server đọc token qua query param
            // (JwtAuthenticationFilter chỉ chấp nhận query param cho path streaming).
            const url = `${SSE_URL}?access_token=${encodeURIComponent(token)}`;
            if (es) { try { es.close(); } catch (e) { /* ignore */ } }
            es = new EventSource(url);

            es.addEventListener('subscribed', (e) => {
                sseRetry = 0; // handshake OK -> reset backoff
                console.log('SSE subscribed', e.data);
            });

            es.addEventListener('notification', (e) => {
                try {
                    const payload = JSON.parse(e.data);
                    // prepend to list if open
                    if (notifList) {
                        notifList.prepend(formatRow(payload));
                    }
                    // bump badge
                    const cur = Number(notifBadge.textContent || '0');
                    showBadge(cur + 1);
                } catch (err) { console.error('invalid notification payload', err); }
            });

            es.onerror = (err) => {
                console.warn('SSE error', err);
                // Vòng lặp tự kết nối lại có giới hạn (không còn setTimeout 5s vô hạn)
                try { es.close(); } catch (e) { /* ignore */ }
                es = null;
                scheduleReconnect();
            };
        } catch (e) {
            console.error('SSE not supported/failed', e);
            scheduleReconnect();
        }
    };

    notifBtn.addEventListener('click', (e) => { e.preventDefault(); toggleDrop(); });
    markAllBtn.addEventListener('click', async (e) => { e.preventDefault(); await markAllRead(); });

    // init
    loadUnread();
    connectSse();

    // Đóng SSE khi rời trang để server giải phóng emitter ngay
    window.addEventListener('beforeunload', () => {
        clearTimeout(sseTimer);
        if (es) { try { es.close(); } catch (e) { /* ignore */ } }
    });

    // close dropdown on outside click
    document.addEventListener('click', (ev) => {
        if (!root.contains(ev.target)) {
            notifDrop.classList.add('hidden');
        }
    });
})();
