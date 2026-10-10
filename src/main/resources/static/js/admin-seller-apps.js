// (() => {
//     const state = {
//         q: '',
//         page: 0,
//         size: 10,
//         totalPages: 0,
//         totalItems: 0,
//         loading: false
//     };
//
//     const els = {};
//
//     const escapeText = (value) => value == null ? '' : String(value);
//
//     const formatDate = (value) => {
//         if (!value) return '—';
//         const date = new Date(value);
//         if (Number.isNaN(date.getTime())) return escapeText(value);
//         return new Intl.DateTimeFormat('vi-VN', {
//             dateStyle: 'medium',
//             timeStyle: 'short'
//         }).format(date);
//     };
//
//     const statusLabel = (status) => {
//         if (status === 'PENDING') return 'Chờ duyệt';
//         if (status === 'APPROVED') return 'Đã duyệt';
//         if (status === 'REJECTED') return 'Đã từ chối';
//         return escapeText(status);
//     };
//
//     const statusClass = (status) => {
//         if (status === 'PENDING') return 'badge badge-warn';
//         if (status === 'APPROVED') return 'badge badge-success';
//         if (status === 'REJECTED') return 'badge badge-danger';
//         return 'badge';
//     };
//
//     const setLoading = (loading) => {
//         state.loading = loading;
//         els.tableContainer?.classList.toggle('is-loading', loading);
//         els.loadingState.style.display = loading ? 'flex' : 'none';
//     };
//
//     const renderSummary = () => {
//         const start = state.totalItems === 0 ? 0 : state.page * state.size + 1;
//         const end = Math.min((state.page + 1) * state.size, state.totalItems);
//         els.summary.textContent = state.totalItems === 0
//             ? 'Không có yêu cầu nào phù hợp.'
//             : `Đang hiển thị ${start}-${end} / ${state.totalItems} yêu cầu`;
//     };
//
//     const renderPagination = () => {
//         els.pageInfo.textContent = state.totalPages === 0
//             ? 'Trang 0 / 0'
//             : `Trang ${state.page + 1} / ${state.totalPages}`;
//         els.prevBtn.disabled = state.loading || state.page <= 0;
//         els.nextBtn.disabled = state.loading || state.page + 1 >= state.totalPages;
//     };
//
//     const clearTable = () => {
//         els.tbody.innerHTML = '';
//     };
//
//     const createCell = (text, className) => {
//         const td = document.createElement('td');
//         if (className) td.className = className;
//         td.textContent = text;
//         return td;
//     };
//
//     const renderRows = (items) => {
//         clearTable();
//         if (!items || items.length === 0) {
//             els.emptyState.style.display = 'block';
//             els.tableWrap.style.display = 'none';
//             return;
//         }
//
//         els.emptyState.style.display = 'none';
//         els.tableWrap.style.display = 'block';
//
//         items.forEach((item, index) => {
//             const tr = document.createElement('tr');
//             tr.appendChild(createCell(String(state.page * state.size + index + 1), 'muted-col'));
//             tr.appendChild(createCell(item.shopName || '—'));
//             tr.appendChild(createCell(item.sellerUsername || '—'));
//             tr.appendChild(createCell(item.sellerEmail || '—'));
//             tr.appendChild(createCell(item.slug || '—', 'mono-col'));
//             tr.appendChild(createCell(item.address || '—'));
//
//             const statusTd = document.createElement('td');
//             const badge = document.createElement('span');
//             badge.className = statusClass(item.approvalStatus);
//             badge.textContent = statusLabel(item.approvalStatus);
//             statusTd.appendChild(badge);
//             tr.appendChild(statusTd);
//
//             // rejection reason column (if present)
//             const reasonTd = document.createElement('td');
//             reasonTd.className = 'px-4 py-4 whitespace-nowrap text-sm text-gray-500';
//             reasonTd.textContent = item.rejectionReason || '';
//             tr.appendChild(reasonTd);
//
//             tr.appendChild(createCell(formatDate(item.createdAt), 'time-col'));
//
//             const actionTd = document.createElement('td');
//             actionTd.className = 'action-col';
//
//             const approveBtn = document.createElement('button');
//             approveBtn.type = 'button';
//             approveBtn.className = 'btn btn-primary';
//             approveBtn.textContent = 'Duyệt';
//             approveBtn.addEventListener('click', async () => {
//                 const confirm = await Swal.fire({
//                     title: 'Duyệt ứng dụng này?',
//                     text: `${item.shopName || ''} sẽ được chuyển sang trạng thái SELLER.`,
//                     icon: 'question',
//                     showCancelButton: true,
//                     confirmButtonText: 'Duyệt',
//                     cancelButtonText: 'Hủy'
//                 });
//                 if (!confirm.isConfirmed) return;
//                 try {
//                     setLoading(true);
//                     const resp = await ApiService.Admin.approveSellerApplication(item.id);
//                     // resp may include mailSent/mailConfigured
//                     if (resp && resp.mailSent === false) {
//                         const choice = await Swal.fire({
//                             title: 'Đã duyệt nhưng email chưa gửi',
//                             text: 'Email thông báo không thể gửi. Bạn muốn gửi lại email hoặc chỉ tạo lại thông báo trong ứng dụng? ',
//                             icon: 'warning',
//                             showCancelButton: true,
//                             showDenyButton: true,
//                             confirmButtonText: 'Gửi lại Email',
//                             denyButtonText: 'Chỉ gửi thông báo',
//                             cancelButtonText: 'Đóng'
//                         });
//                         if (choice.isConfirmed) {
//                             await ApiService.Admin.resendEmail(item.id, 'approved');
//                             await Swal.fire({ icon: 'success', title: 'Email đã gửi lại' });
//                         } else if (choice.isDenied) {
//                             await ApiService.Admin.resendNotification(item.id, 'approved');
//                             await Swal.fire({ icon: 'success', title: 'Thông báo đã được tạo lại' });
//                         }
//                     } else {
//                         await Swal.fire({ icon: 'success', title: 'Đã duyệt', text: 'Đã gửi email và thông báo cho người bán.' });
//                     }
//                     await loadApplications();
//                 } catch (error) {
//                     Swal.fire({ icon: 'error', title: 'Lỗi', text: error?.message || 'Không thể duyệt yêu cầu' });
//                 } finally {
//                     setLoading(false);
//                 }
//             });
//
//             const rejectBtn = document.createElement('button');
//             rejectBtn.type = 'button';
//             rejectBtn.className = 'btn btn-ghost';
//             rejectBtn.textContent = 'Từ chối';
//             rejectBtn.addEventListener('click', async () => {
//                 // Open in-page modal and populate shop id on dataset
//                 openRejectModalFor(item);
//             });
//
//             actionTd.appendChild(approveBtn);
//             actionTd.appendChild(rejectBtn);
//             tr.appendChild(actionTd);
//             els.tbody.appendChild(tr);
//         });
//     };
//
//     const loadApplications = async () => {
//         console.log("=== HÀM loadApplications() ĐÃ ĐƯỢC KÍCH HOẠT ===");
//         setLoading(true);
//         try {
//             const payload = await ApiService.Admin.listSellerApplications({
//                 q: state.q,
//                 page: state.page,
//                 size: state.size
//             });
//
//             const items = Array.isArray(payload?.items) ? payload.items : [];
//             state.page = Number.isFinite(payload?.page) ? payload.page : state.page;
//             state.size = Number.isFinite(payload?.size) ? payload.size : state.size;
//             state.totalPages = Number.isFinite(payload?.totalPages) ? payload.totalPages : 0;
//             state.totalItems = Number.isFinite(payload?.totalItems) ? payload.totalItems : items.length;
//
//             renderRows(items);
//             renderSummary();
//             renderPagination();
//         } catch (error) {
//             els.emptyState.style.display = 'block';
//             els.emptyState.textContent = error?.message || 'Không tải được danh sách ứng dụng';
//             els.tableWrap.style.display = 'none';
//             state.totalPages = 0;
//             state.totalItems = 0;
//             renderSummary();
//             renderPagination();
//         } finally {
//             setLoading(false);
//         }
//     };
//
//     const bindEvents = () => {
//         els.searchForm.addEventListener('submit', async (event) => {
//             event.preventDefault();
//             state.q = els.searchInput.value.trim();
//             state.page = 0;
//             await loadApplications();
//         });
//
//         els.clearBtn.addEventListener('click', async () => {
//             els.searchInput.value = '';
//             state.q = '';
//             state.page = 0;
//             await loadApplications();
//         });
//
//         els.sizeSelect.addEventListener('change', async () => {
//             state.size = Number(els.sizeSelect.value) || 10;
//             state.page = 0;
//             await loadApplications();
//         });
//
//         els.prevBtn.addEventListener('click', async () => {
//             if (state.page <= 0) return;
//             state.page -= 1;
//             await loadApplications();
//         });
//
//         els.nextBtn.addEventListener('click', async () => {
//             if (state.page + 1 >= state.totalPages) return;
//             state.page += 1;
//             await loadApplications();
//         });
//     };
//
//     window.addEventListener('DOMContentLoaded', () => {
//         els.searchForm = document.getElementById('searchForm');
//         els.searchInput = document.getElementById('searchInput');
//         els.clearBtn = document.getElementById('clearBtn');
//         els.sizeSelect = document.getElementById('sizeSelect');
//         els.prevBtn = document.getElementById('prevBtn');
//         els.nextBtn = document.getElementById('nextBtn');
//         els.pageInfo = document.getElementById('pageInfo');
//         els.summary = document.getElementById('summary');
//         els.tableContainer = document.getElementById('tableContainer');
//         els.tableWrap = document.getElementById('tableWrap');
//         els.tbody = document.getElementById('applicationsTbody');
//         els.emptyState = document.getElementById('emptyState');
//         els.loadingState = document.getElementById('loadingState');
//
//         bindEvents();
//         // Modal elements
//         window._adminRejectModal = {
//             modal: document.getElementById('rejectModal'),
//             reasonInput: document.getElementById('rejectReasonInput'),
//             submitBtn: document.getElementById('rejectModalSubmit'),
//             cancelBtn: document.getElementById('rejectModalCancel'),
//             closeBtn: document.getElementById('rejectModalClose'),
//             feedback: document.getElementById('rejectFeedback'),
//             resendOptions: document.getElementById('resendOptions'),
//             resendEmailBtn: document.getElementById('resendEmailBtn'),
//             resendNotificationBtn: document.getElementById('resendNotificationBtn'),
//             resendStatus: document.getElementById('resendStatus'),
//             currentShopId: null,
//             currentType: 'rejected'
//         };
//
//         const m = window._adminRejectModal;
//         const closeModal = () => {
//             m.modal.classList.add('hidden');
//             m.reasonInput.value = '';
//             m.feedback.textContent = '';
//             m.resendOptions.classList.add('hidden');
//             m.resendStatus.textContent = '';
//             m.currentShopId = null;
//         };
//
//         m.cancelBtn.addEventListener('click', closeModal);
//         m.closeBtn.addEventListener('click', closeModal);
//
//         m.submitBtn.addEventListener('click', async () => {
//             const reason = m.reasonInput.value.trim();
//             if (!m.currentShopId) return;
//             try {
//                 setLoading(true);
//                 const resp = await ApiService.Admin.rejectSellerApplication(m.currentShopId, reason || null);
//                 // resp may include mailSent and mailConfigured
//                 if (resp && resp.mailSent === false) {
//                     m.resendOptions.classList.remove('hidden');
//                     m.feedback.textContent = 'Email chưa được gửi. Bạn có thể gửi lại email hoặc thông báo.';
//                     // store notification id for possible resend
//                     m.resendStatus.textContent = '';
//                     // keep modal open to allow resend
//                 } else {
//                     await Swal.fire({ icon: 'success', title: 'Đã từ chối', text: 'Người bán đã nhận thông báo.' });
//                     closeModal();
//                     await loadApplications();
//                 }
//             } catch (error) {
//                 m.feedback.textContent = error?.message || 'Không thể từ chối yêu cầu';
//             } finally {
//                 setLoading(false);
//             }
//         });
//
//         m.resendEmailBtn.addEventListener('click', async () => {
//             if (!m.currentShopId) return;
//             try {
//                 m.resendStatus.textContent = 'Đang gửi lại email…';
//                 const r = await ApiService.Admin.resendEmail(m.currentShopId, m.currentType);
//                 m.resendStatus.textContent = r?.mailSent ? 'Gửi lại email thành công' : 'Gửi lại email thất bại';
//                 if (r?.mailSent) {
//                     await Swal.fire({ icon: 'success', title: 'Email đã gửi lại' });
//                     closeModal();
//                     await loadApplications();
//                 }
//             } catch (e) {
//                 m.resendStatus.textContent = e?.message || 'Lỗi khi gửi lại email';
//             }
//         });
//
//         m.resendNotificationBtn.addEventListener('click', async () => {
//             if (!m.currentShopId) return;
//             try {
//                 m.resendStatus.textContent = 'Đang gửi lại thông báo…';
//                 const r = await ApiService.Admin.resendNotification(m.currentShopId, m.currentType);
//                 m.resendStatus.textContent = r?.notificationId ? 'Thông báo đã được tạo lại' : 'Không thể gửi lại thông báo';
//                 if (r?.notificationId) {
//                     await Swal.fire({ icon: 'success', title: 'Thông báo đã gửi' });
//                     closeModal();
//                     await loadApplications();
//                 }
//             } catch (e) {
//                 m.resendStatus.textContent = e?.message || 'Lỗi khi gửi lại thông báo';
//             }
//         });
//         loadApplications();
//
//     });
//
//     function openRejectModalFor(item) {
//         const m = window._adminRejectModal;
//         m.currentShopId = item.id;
//         m.currentType = 'rejected';
//         m.reasonInput.value = item.rejectionReason || '';
//         m.modal.classList.remove('hidden');
//         m.resendOptions.classList.add('hidden');
//         m.resendStatus.textContent = '';
//     }
// })();





// Đưa els ra ngoài để hàm nào cũng nhìn thấy
const els = {};

const state = {
    q: '',
    page: 0,
    size: 10,
    totalPages: 0,
    totalItems: 0,
    loading: false
};

const escapeText = (value) => value == null ? '' : String(value);

const formatDate = (value) => {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return escapeText(value);
    return new Intl.DateTimeFormat('vi-VN', {
        dateStyle: 'medium',
        timeStyle: 'short'
    }).format(date);
};

const statusLabel = (status) => {
    if (status === 'PENDING') return 'Chờ duyệt';
    if (status === 'APPROVED') return 'Đã duyệt';
    if (status === 'REJECTED') return 'Đã từ chối';
    return escapeText(status);
};

const statusClass = (status) => {
    if (status === 'PENDING') return 'inline-flex items-center rounded-full bg-amber-100 px-2.5 py-1 text-xs font-black text-amber-700';
    if (status === 'APPROVED') return 'inline-flex items-center rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-black text-emerald-700';
    if (status === 'REJECTED') return 'inline-flex items-center rounded-full bg-rose-100 px-2.5 py-1 text-xs font-black text-rose-700';
    return 'inline-flex items-center rounded-full bg-gray-100 px-2.5 py-1 text-xs font-black text-gray-600';
};

const setLoading = (loading) => {
    state.loading = loading;
    if (els.tableContainer) {
        els.tableContainer.classList.toggle('opacity-60', loading);
        els.tableContainer.classList.toggle('pointer-events-none', loading);
    }
    if (els.loadingState) els.loadingState.style.display = loading ? 'flex' : 'none';
};

const renderSummary = () => {
    const start = state.totalItems === 0 ? 0 : state.page * state.size + 1;
    const end = Math.min((state.page + 1) * state.size, state.totalItems);
    if (els.summary) {
        els.summary.textContent = state.totalItems === 0
            ? 'Không có yêu cầu nào phù hợp.'
            : `Đang hiển thị ${start}-${end} / ${state.totalItems} yêu cầu`;
    }
};

const renderPagination = () => {
    if (els.pageInfo) {
        els.pageInfo.textContent = state.totalPages === 0
            ? 'Trang 0 / 0'
            : `Trang ${state.page + 1} / ${state.totalPages}`;
    }
    if (els.prevBtn) els.prevBtn.disabled = state.loading || state.page <= 0;
    if (els.nextBtn) els.nextBtn.disabled = state.loading || state.page + 1 >= state.totalPages;
};

const renderRows = (items) => {
    if (!els.tbody) return;
    els.tbody.innerHTML = '';

    if (!items || items.length === 0) {
        if (els.emptyState) els.emptyState.style.display = 'block';
        if (els.tableWrap) els.tableWrap.style.display = 'none';
        return;
    }

    if (els.emptyState) els.emptyState.style.display = 'none';
    if (els.tableWrap) els.tableWrap.style.display = 'block';

    items.forEach((item, index) => {
        const tr = document.createElement('tr');
        tr.className = 'hover:bg-[#FAF5E8]/50 transition-colors cursor-pointer';

        const createCell = (text, className) => {
            const td = document.createElement('td');
            td.className = className ? 'px-6 py-4 ' + className : 'px-6 py-4';
            td.textContent = text;
            return td;
        };

        tr.appendChild(createCell(String(state.page * state.size + index + 1), 'w-14 text-center font-black text-gray-400'));
        tr.appendChild(createCell(item.shopName || '—', 'w-[20%] font-black text-[#5D4037]'));
        tr.appendChild(createCell(item.sellerUsername || '—', 'w-[12%] font-bold text-[#5D4037]/80'));

        // Email: 1 dòng, cắt bớt bằng truncate + tooltip
        const emailTd = document.createElement('td');
        emailTd.className = 'px-6 py-4 w-[18%] font-bold text-[#5D4037]/80 truncate';
        emailTd.textContent = item.sellerEmail || '—';
        emailTd.title = item.sellerEmail || '';
        tr.appendChild(emailTd);

        // Địa chỉ: tối đa 2 dòng, giữ tooltip đầy đủ
        const addressTd = document.createElement('td');
        addressTd.className = 'px-6 py-4 w-[26%] font-bold text-[#5D4037]/70';
        addressTd.title = item.address || '';
        const addressSpan = document.createElement('span');
        addressSpan.className = 'block line-clamp-2 whitespace-normal';
        addressSpan.textContent = item.address || '—';
        addressTd.appendChild(addressSpan);
        tr.appendChild(addressTd);

        const statusTd = document.createElement('td');
        statusTd.className = 'px-6 py-4 w-[10%] text-center';
        const badge = document.createElement('span');
        badge.className = statusClass(item.approvalStatus);
        badge.textContent = statusLabel(item.approvalStatus);
        statusTd.appendChild(badge);
        tr.appendChild(statusTd);

        tr.appendChild(createCell(formatDate(item.createdAt), 'w-[12%] text-gray-500 whitespace-nowrap'));

        const actionTd = document.createElement('td');
        actionTd.className = 'px-6 py-4 w-[12%] text-right whitespace-nowrap';
        const actionWrap = document.createElement('div');
        actionWrap.className = 'inline-flex items-center gap-2';
        actionTd.appendChild(actionWrap);

        const approveBtn = document.createElement('button');
        approveBtn.type = 'button';
        approveBtn.className = 'inline-flex items-center justify-center rounded-xl bg-[#ea580c] px-3.5 py-2 text-xs font-black text-white shadow-sm transition-all hover:bg-[#c2410c] disabled:opacity-50';
        approveBtn.textContent = 'Duyệt';
        approveBtn.addEventListener('click', async (event) => {
            event.stopPropagation();
            const confirm = await Swal.fire({
                title: 'Duyệt ứng dụng này?',
                text: `${item.shopName || ''} sẽ được chuyển sang trạng thái SELLER.`,
                icon: 'question',
                showCancelButton: true,
                confirmButtonText: 'Duyệt',
                cancelButtonText: 'Hủy'
            });
            if (!confirm.isConfirmed) return;
            try {
                setLoading(true);
                const resp = await ApiService.Admin.approveSellerApplication(item.id);
                if (resp && resp.mailSent === false) {
                    const choice = await Swal.fire({
                        title: 'Đã duyệt nhưng email chưa gửi',
                        text: 'Email thông báo không thể gửi. Bạn muốn gửi lại email hoặc chỉ tạo lại thông báo?',
                        icon: 'warning',
                        showCancelButton: true,
                        showDenyButton: true,
                        confirmButtonText: 'Gửi lại Email',
                        denyButtonText: 'Chỉ gửi thông báo',
                        cancelButtonText: 'Đóng'
                    });
                    if (choice.isConfirmed) {
                        await ApiService.Admin.resendEmail(item.id, 'approved');
                        await Swal.fire({ icon: 'success', title: 'Email đã gửi lại' });
                    } else if (choice.isDenied) {
                        await ApiService.Admin.resendNotification(item.id, 'approved');
                        await Swal.fire({ icon: 'success', title: 'Thông báo đã được tạo lại' });
                    }
                } else {
                    await Swal.fire({ icon: 'success', title: 'Đã duyệt', text: 'Đã gửi email và thông báo cho người bán.' });
                }
                await loadApplications();
            } catch (error) {
                Swal.fire({ icon: 'error', title: 'Lỗi', text: error?.message || 'Không thể duyệt yêu cầu' });
            } finally {
                setLoading(false);
            }
        });

        const rejectBtn = document.createElement('button');
        rejectBtn.type = 'button';
        rejectBtn.className = 'inline-flex items-center justify-center rounded-xl border border-[#E5CBB5] bg-white px-3.5 py-2 text-xs font-black text-[#5D4037] transition-all hover:bg-[#FAF5E8] disabled:opacity-50';
        rejectBtn.textContent = 'Từ chối';
        rejectBtn.addEventListener('click', (event) => {
            event.stopPropagation();
            const m = window._adminRejectModal;
            if (m) {
                m.currentShopId = item.id;
                m.currentType = 'rejected';
                m.reasonInput.value = item.rejectionReason || '';
                m.modal.classList.remove('hidden');
                m.resendOptions.classList.add('hidden');
                m.resendStatus.textContent = '';
            }
        });

        actionWrap.appendChild(approveBtn);
        actionWrap.appendChild(rejectBtn);
        tr.appendChild(actionTd);

        // Click vào dòng → mở trang chi tiết gian hàng (read-only)
        tr.addEventListener('click', () => {
            if (!item.id) return;
            window.location.href = `/admin/shops/${encodeURIComponent(item.id)}?from=seller-applications`;
        });

        els.tbody.appendChild(tr);
    });
};

const loadApplications = async () => {
    console.log("=== LỆNH FETCH API CHUẨN BỊ ĐƯỢC GỬI ĐI ===");
    setLoading(true);
    try {
        // Gọi thẳng sang ApiService công khai
        const payload = await window.ApiService.Admin.listSellerApplications({
            q: state.q,
            page: state.page,
            size: state.size
        });

        console.log("=== DỮ LIỆU API TRẢ VỀ THÀNH CÔNG ===", payload);

        const items = Array.isArray(payload?.items) ? payload.items : [];
        state.page = Number.isFinite(payload?.page) ? payload.page : state.page;
        state.size = Number.isFinite(payload?.size) ? payload.size : state.size;
        state.totalPages = Number.isFinite(payload?.totalPages) ? payload.totalPages : 0;
        state.totalItems = Number.isFinite(payload?.totalItems) ? payload.totalItems : items.length;

        renderRows(items);
        renderSummary();
        renderPagination();
    } catch (error) {
        console.error("=== API BỊ LỖI HOẶC CHẶN BỞI SPRING SECURITY ===", error);
        if (els.emptyState) {
            els.emptyState.style.display = 'block';
            els.emptyState.textContent = error?.message || 'Không tải được danh sách ứng dụng';
        }
        if (els.tableWrap) els.tableWrap.style.display = 'none';
    } finally {
        setLoading(false);
    }
};

const bindEvents = () => {
    els.searchForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        state.q = els.searchInput.value.trim();
        state.page = 0;
        await loadApplications();
    });

    els.clearBtn?.addEventListener('click', async () => {
        if (els.searchInput) els.searchInput.value = '';
        state.q = '';
        state.page = 0;
        await loadApplications();
    });

    els.sizeSelect?.addEventListener('change', async () => {
        state.size = Number(els.sizeSelect.value) || 10;
        state.page = 0;
        await loadApplications();
    });

    els.prevBtn?.addEventListener('click', async () => {
        if (state.page <= 0) return;
        state.page -= 1;
        await loadApplications();
    });

    els.nextBtn?.addEventListener('click', async () => {
        if (state.page + 1 >= state.totalPages) return;
        state.page += 1;
        await loadApplications();
    });
};

// Hàm khởi tạo chạy trực tiếp khi file được load bổ sung xuống cuối HTML
function initSellerApplicationsPage() {
    console.log("=== ĐANG KHỞI TẠO ĐỒNG BỘ CÁC PHẦN TỬ GIAO DIỆN ===");

    els.searchForm = document.getElementById('searchForm');
    els.searchInput = document.getElementById('searchInput');
    els.clearBtn = document.getElementById('clearBtn');
    els.sizeSelect = document.getElementById('sizeSelect');
    els.prevBtn = document.getElementById('prevBtn');
    els.nextBtn = document.getElementById('nextBtn');
    els.pageInfo = document.getElementById('pageInfo');
    els.summary = document.getElementById('summary');
    els.tableContainer = document.getElementById('tableContainer');
    els.tableWrap = document.getElementById('tableWrap');
    els.tbody = document.getElementById('applicationsTbody');
    els.emptyState = document.getElementById('emptyState');
    els.loadingState = document.getElementById('loadingState');

    bindEvents();

    // Modal elements
    window._adminRejectModal = {
        modal: document.getElementById('rejectModal'),
        reasonInput: document.getElementById('rejectReasonInput'),
        submitBtn: document.getElementById('rejectModalSubmit'),
        cancelBtn: document.getElementById('rejectModalCancel'),
        closeBtn: document.getElementById('rejectModalClose'),
        feedback: document.getElementById('rejectFeedback'),
        resendOptions: document.getElementById('resendOptions'),
        resendEmailBtn: document.getElementById('resendEmailBtn'),
        resendNotificationBtn: document.getElementById('resendNotificationBtn'),
        resendStatus: document.getElementById('resendStatus'),
        currentShopId: null,
        currentType: 'rejected'
    };

    const m = window._adminRejectModal;
    const closeModal = () => {
        m.modal.classList.add('hidden');
        m.reasonInput.value = '';
        m.feedback.textContent = '';
        m.resendOptions.classList.add('hidden');
        m.resendStatus.textContent = '';
        m.currentShopId = null;
    };

    m.cancelBtn?.addEventListener('click', closeModal);
    m.closeBtn?.addEventListener('click', closeModal);

    m.submitBtn?.addEventListener('click', async () => {
        const reason = m.reasonInput.value.trim();
        if (!m.currentShopId) return;
        try {
            setLoading(true);
            const resp = await window.ApiService.Admin.rejectSellerApplication(m.currentShopId, reason || null);
            if (resp && resp.mailSent === false) {
                m.resendOptions.classList.remove('hidden');
                m.feedback.textContent = 'Email chưa được gửi. Bạn có thể gửi lại email hoặc thông báo.';
                m.resendStatus.textContent = '';
            } else {
                await Swal.fire({ icon: 'success', title: 'Đã từ chối', text: 'Người bán đã nhận thông báo.' });
                closeModal();
                await loadApplications();
            }
        } catch (error) {
            m.feedback.textContent = error?.message || 'Không thể từ chối yêu cầu';
        } finally {
            setLoading(false);
        }
    });

    m.resendEmailBtn?.addEventListener('click', async () => {
        if (!m.currentShopId) return;
        try {
            m.resendStatus.textContent = 'Đang gửi lại email…';
            const r = await window.ApiService.Admin.resendEmail(m.currentShopId, m.currentType);
            if (r?.mailSent) {
                await Swal.fire({ icon: 'success', title: 'Email đã gửi lại' });
                closeModal();
                await loadApplications();
            } else {
                m.resendStatus.textContent = 'Gửi lại email thất bại';
            }
        } catch (e) {
            m.resendStatus.textContent = e?.message || 'Lỗi khi gửi lại email';
        }
    });

    m.resendNotificationBtn?.addEventListener('click', async () => {
        if (!m.currentShopId) return;
        try {
            m.resendStatus.textContent = 'Đang gửi lại thông báo…';
            const r = await window.ApiService.Admin.resendNotification(m.currentShopId, m.currentType);
            if (r?.notificationId) {
                await Swal.fire({ icon: 'success', title: 'Thông báo đã gửi' });
                closeModal();
                await loadApplications();
            } else {
                m.resendStatus.textContent = 'Không thể gửi lại thông báo';
            }
        } catch (e) {
            m.resendStatus.textContent = e?.message || 'Lỗi khi gửi lại thông báo';
        }
    });

    // Kích hoạt gọi API ngay sau khi map xong phần tử
    loadApplications();
}

// Cho phép gọi trực tiếp
window.initSellerApplicationsPage = initSellerApplicationsPage;