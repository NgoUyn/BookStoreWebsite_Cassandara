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

    let es = null;

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
                await fetch(API_MARK(item.id), { method: 'PATCH', headers: { 'Content-Type':'application/json' } });
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
            const res = await fetch(API_UNREAD, { headers: { 'Content-Type':'application/json' } });
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
            const res = await fetch(API_LIST, { headers: { 'Content-Type':'application/json' } });
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
            const res = await fetch(API_READ_ALL, { method: 'PATCH', headers: { 'Content-Type':'application/json' } });
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

    const connectSse = () => {
        try {
            // Try to attach token if present (EventSource can't set headers)
            const token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');
            const url = token ? `${SSE_URL}?access_token=${encodeURIComponent(token)}` : SSE_URL;
            es = new EventSource(url);

            es.addEventListener('subscribed', (e) => {
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
                // Attempt reconnect with backoff
                es.close();
                setTimeout(connectSse, 5000);
            };
        } catch (e) {
            console.error('SSE not supported/failed', e);
        }
    };

    notifBtn.addEventListener('click', (e) => { e.preventDefault(); toggleDrop(); });
    markAllBtn.addEventListener('click', async (e) => { e.preventDefault(); await markAllRead(); });

    // init
    loadUnread();
    connectSse();

    // close dropdown on outside click
    document.addEventListener('click', (ev) => {
        if (!root.contains(ev.target)) {
            notifDrop.classList.add('hidden');
        }
    });
})();
