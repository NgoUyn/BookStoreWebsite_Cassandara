/**
 * Trang Admin > Sao lưu hệ thống (Admin_Backup.html).
 *
 * Gọi API trong AdminBackupController:
 *   GET    /api/admin/backup                  -> tình trạng + danh sách bản sao lưu
 *   POST   /api/admin/backup                  -> tạo bản sao lưu mới
 *   POST   /api/admin/backup/{file}/restore   -> phục hồi (GHI ĐÈ dữ liệu hiện tại)
 *   GET    /api/admin/backup/{file}/download  -> tải file .archive (fetch + blob để gửi JWT)
 *   DELETE /api/admin/backup/{file}           -> xoá file sao lưu
 */
(() => {
    const API = '/api/admin/backup';
    const els = {};

    const authHeaders = () => (window.ApiService ? ApiService.getHeaders() : { 'Content-Type': 'application/json' });

    const escapeHtml = (value) => String(value == null ? '' : value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');

    const humanSize = (bytes) => {
        if (!bytes) return '0 B';
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
        return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
    };

    const formatDate = (value) => {
        if (!value) return '—';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return escapeHtml(value);
        return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'medium' }).format(date);
    };

    /** fetch + tự bóc message lỗi từ JSON/text giống api-service.js. */
    async function request(url, options) {
        const opts = options || {};
        const response = await fetch(url, {
            method: opts.method || 'GET',
            headers: Object.assign({}, authHeaders(), opts.headers || {})
        });
        const text = await response.text();
        let data = null;
        try {
            data = text ? JSON.parse(text) : null;
        } catch (e) {
            data = { message: text };
        }
        if (!response.ok) {
            throw new Error((data && (data.message || data.error)) || ('HTTP ' + response.status));
        }
        return data;
    }

    const setBusy = (busy) => {
        const createBtn = document.getElementById('btn-create-backup');
        const reloadBtn = document.getElementById('btn-reload-backup');
        if (createBtn) {
            createBtn.disabled = busy;
            createBtn.textContent = busy ? 'Đang xử lý…' : 'Tạo bản sao lưu ngay';
        }
        if (reloadBtn) reloadBtn.disabled = busy;
    };

    function showLog(log) {
        const section = document.getElementById('backup-log-section');
        const pre = document.getElementById('backup-log');
        if (!log) {
            section.classList.add('hidden');
            pre.textContent = '';
            return;
        }
        pre.textContent = log;
        section.classList.remove('hidden');
    }

    function renderOverview(data) {
        els.storage.textContent = data.storageType === 'gridfs'
            ? 'GridFS (trong MongoDB)'
            : (data.storageType ? data.storageType + ' (ngoài DB)' : '—');

        const dump = data.mongodumpAvailable;
        const restore = data.mongorestoreAvailable;
        if (dump && restore) {
            els.tools.innerHTML = '<span class="text-green-700">Đã cài đặt</span>';
        } else {
            const missing = [];
            if (!dump) missing.push('mongodump');
            if (!restore) missing.push('mongorestore');
            els.tools.innerHTML = '<span class="text-red-600">Thiếu ' + missing.join(' &amp; ') + '</span>';
        }

        const count = data.backups ? data.backups.length : 0;
        els.dir.textContent = data.backupDir || '—';
        els.total.textContent = count + ' bản sao lưu · tổng ' + humanSize(data.totalSizeBytes)
            + (data.busy ? ' · ĐANG CHẠY tiến trình' : '');
        els.count.textContent = count + ' file';

        renderRows(data.backups);
    }

    function renderRows(backups) {
        if (!backups || !backups.length) {
            els.tbody.innerHTML = '<tr><td colspan="5" class="px-6 py-10 text-center text-gray-400 font-bold">'
                + 'Chưa có bản sao lưu nào. Bấm “Tạo bản sao lưu ngay”.</td></tr>';
            return;
        }
        els.tbody.innerHTML = backups.map((item) => {
            const file = escapeHtml(item.fileName);
            const badge = item.latest
                ? '<span class="inline-block px-3 py-1 rounded-full text-xs font-bold bg-green-100 text-green-700">Mới nhất</span>'
                : '<span class="inline-block px-3 py-1 rounded-full text-xs font-bold bg-gray-100 text-gray-600">Bản cũ</span>';
            return '<tr class="hover:bg-[#FAF5E8] transition-all">'
                + '<td class="px-6 py-4 font-bold text-[#5D4037] break-all">' + file + '</td>'
                + '<td class="px-6 py-4 font-bold">' + humanSize(item.sizeBytes) + '</td>'
                + '<td class="px-6 py-4 text-xs font-bold">' + formatDate(item.createdAt) + '</td>'
                + '<td class="px-6 py-4">' + badge + '</td>'
                + '<td class="px-6 py-4 text-right space-x-3 whitespace-nowrap">'
                + '<button data-action="download" data-file="' + file + '" class="text-[#5D4037] hover:text-[#ea580c] font-bold text-sm">Tải về</button>'
                + '<button data-action="restore" data-file="' + file + '" class="text-[#ea580c] hover:text-[#c2410c] font-bold text-sm">Phục hồi</button>'
                + '<button data-action="delete" data-file="' + file + '" class="text-red-600 hover:text-red-800 font-bold text-sm">Xoá</button>'
                + '</td></tr>';
        }).join('');
    }

    async function load() {
        try {
            renderOverview(await request(API));
        } catch (err) {
            els.tbody.innerHTML = '<tr><td colspan="5" class="px-6 py-10 text-center text-red-500 font-bold">'
                + escapeHtml(err.message) + '</td></tr>';
        }
    }

    /** POST /api/admin/backup: mongodump --gzip --archive (DB + ảnh GridFS). */
    async function createBackup() {
        const confirm = await Swal.fire({
            title: 'Tạo bản sao lưu?',
            text: 'Toàn bộ database bookom và ảnh (GridFS) sẽ được nén vào 1 file .archive.',
            icon: 'question',
            showCancelButton: true,
            confirmButtonText: 'Sao lưu',
            cancelButtonText: 'Huỷ',
            confirmButtonColor: '#ea580c',
            cancelButtonColor: '#9ca3af'
        });
        if (!confirm.isConfirmed) return;

        setBusy(true);
        Swal.fire({
            title: 'Đang sao lưu…',
            text: 'Vui lòng không đóng trang (cơ sở dữ liệu lớn có thể mất vài phút).',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        try {
            const result = await request(API, { method: 'POST' });
            Swal.close();
            showLog(result.log);
            await Swal.fire({
                icon: result.success ? 'success' : 'error',
                title: result.success ? 'Sao lưu thành công' : 'Sao lưu thất bại',
                text: result.message,
                confirmButtonColor: '#ea580c'
            });
            await load();
        } catch (err) {
            Swal.close();
            await Swal.fire({ icon: 'error', title: 'Lỗi', text: err.message, confirmButtonColor: '#ea580c' });
        } finally {
            setBusy(false);
        }
    }

    /** POST /api/admin/backup/{file}/restore: mongorestore --drop (GHI ĐÈ dữ liệu). */
    async function restoreBackup(fileName) {
        const confirm = await Swal.fire({
            title: 'Phục hồi từ bản sao lưu?',
            html: 'Dữ liệu hiện tại (đơn hàng, người dùng, sách, ảnh…) sẽ bị <b>GHI ĐÈ</b> bằng nội dung của<br><b>' + escapeHtml(fileName) + '</b>.<br>Toàn bộ thay đổi sau thời điểm sao lưu sẽ MẤT.',
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Phục hồi & ghi đè',
            cancelButtonText: 'Huỷ',
            confirmButtonColor: '#dc2626',
            cancelButtonColor: '#9ca3af'
        });
        if (!confirm.isConfirmed) return;

        setBusy(true);
        Swal.fire({
            title: 'Đang phục hồi…',
            text: 'Vui lòng không đóng trang.',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        try {
            const result = await request(API + '/' + encodeURIComponent(fileName) + '/restore', { method: 'POST' });
            Swal.close();
            showLog(result.log);
            await Swal.fire({
                icon: result.success ? 'success' : 'error',
                title: result.success ? 'Phục hồi thành công' : 'Phục hồi thất bại',
                text: result.message,
                confirmButtonColor: '#ea580c'
            });
            await load();
        } catch (err) {
            Swal.close();
            await Swal.fire({ icon: 'error', title: 'Lỗi', text: err.message, confirmButtonColor: '#ea580c' });
        } finally {
            setBusy(false);
        }
    }

    /** DELETE /api/admin/backup/{file}. */
    async function deleteBackup(fileName) {
        const confirm = await Swal.fire({
            title: 'Xoá bản sao lưu?',
            text: fileName + ' sẽ bị xoá khỏi thư mục backups (không ảnh hưởng dữ liệu đang chạy).',
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Xoá',
            cancelButtonText: 'Huỷ',
            confirmButtonColor: '#dc2626',
            cancelButtonColor: '#9ca3af'
        });
        if (!confirm.isConfirmed) return;

        try {
            const result = await request(API + '/' + encodeURIComponent(fileName), { method: 'DELETE' });
            await Swal.fire({
                icon: result.success ? 'success' : 'warning',
                title: result.success ? 'Đã xoá' : 'Thông báo',
                text: result.message,
                confirmButtonColor: '#ea580c'
            });
            await load();
        } catch (err) {
            await Swal.fire({ icon: 'error', title: 'Lỗi', text: err.message, confirmButtonColor: '#ea580c' });
        }
    }

    /**
     * Tải file .archive về máy.
     * Dùng fetch + blob (KHÔNG dùng thẻ <a href>) vì endpoint yêu cầu JWT trong
     * header Authorization — điều hướng trực tiếp của browser sẽ không gửi header.
     */
    async function downloadBackup(fileName) {
        try {
            const response = await fetch(API + '/' + encodeURIComponent(fileName) + '/download', {
                headers: authHeaders()
            });
            if (!response.ok) {
                throw new Error('Không tải được file (HTTP ' + response.status + ')');
            }
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = fileName;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(url);
        } catch (err) {
            await Swal.fire({ icon: 'error', title: 'Lỗi tải file', text: err.message, confirmButtonColor: '#ea580c' });
        }
    }

    window.initAdminBackupPage = function () {
        els.tbody = document.getElementById('backup-table-body');
        els.storage = document.getElementById('status-storage');
        els.tools = document.getElementById('status-tools');
        els.dir = document.getElementById('status-dir');
        els.total = document.getElementById('status-total');
        els.count = document.getElementById('backup-count');

        document.getElementById('btn-create-backup').addEventListener('click', createBackup);
        document.getElementById('btn-reload-backup').addEventListener('click', load);

        // Ủy quyền sự kiện: bảng được render lại sau mỗi lần tải danh sách
        els.tbody.addEventListener('click', (event) => {
            const button = event.target.closest('button[data-action]');
            if (!button) return;
            const fileName = button.getAttribute('data-file');
            const action = button.getAttribute('data-action');
            if (action === 'download') downloadBackup(fileName);
            else if (action === 'restore') restoreBackup(fileName);
            else if (action === 'delete') deleteBackup(fileName);
        });

        load();
    };
})();
