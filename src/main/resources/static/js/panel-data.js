(function () {
  var API_ROOT = "/api/panel";
  var charts = {};

  function esc(v) {
    return String(v || "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\"/g, "&quot;")
        .replace(/'/g, "&#39;");
  }

  function vnd(v) {
    return new Intl.NumberFormat("vi-VN", {
      style: "currency",
      currency: "VND",
      maximumFractionDigits: 0
    }).format(v || 0);
  }

  function authHeaders() {
    var token = localStorage.getItem('accessToken') || localStorage.getItem('access_token') ||
        sessionStorage.getItem('accessToken') || sessionStorage.getItem('access_token');
    var headers = {
      'Content-Type': 'application/json'
    };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    
    // Fallback for dev mode
    var userId = localStorage.getItem('userId') || window.BookomDevUserId;
    if (!token && userId) headers['X-User-Id'] = String(userId);
    
    return headers;
  }

  function getJson(url) {
    return fetch(url, { headers: authHeaders() }).then(function (res) {
      if (res.status === 401 || res.status === 403) {
        // Redirect to a login page or show auth modal for admin
        try {
          localStorage.removeItem('accessToken');
          localStorage.removeItem('access_token');
          sessionStorage.removeItem('accessToken');
          sessionStorage.removeItem('access_token');
        } catch(e){}
        if (window.location.pathname !== '/main/auth') {
          window.location.href = '/main/auth';
        }
        throw new Error("Unauthorized: " + res.status);
      }
      if (!res.ok) throw new Error("HTTP " + res.status);
      return res.json();
    });
  }

  function setText(id, val) {
    var el = document.getElementById(id);
    if (el) el.textContent = val;
  }

  function setHtml(id, html) {
    var el = document.getElementById(id);
    if (el) el.innerHTML = html;
  }

  function chart(id, type, data, options) {
    if (!window.Chart) return;
    var canvas = document.getElementById(id);
    if (!canvas) return;
    if (charts[id]) charts[id].destroy();
    charts[id] = new Chart(canvas, { type: type, data: data, options: options || {} });
  }

  function qs(params) {
    var usp = new URLSearchParams();
    Object.keys(params).forEach(function (k) {
      if (params[k] !== undefined && params[k] !== null) usp.set(k, params[k]);
    });
    return usp.toString();
  }

  function initAdminDashboard() {
    return getJson(API_ROOT + "/summary").then(function (data) {
      setText("metric-gmv", vnd(data.gmv));
      setText("metric-books", String(data.books || 0).replace(/\B(?=(\d{3})+(?!\d))/g, "."));
      setText("metric-categories", String(data.categories || 0));
      setText("metric-shops", String(data.shops || 0));

      var latestShops = data.latestShops || [];
      var rows = latestShops.map(function (s, idx) {
        return (
            "<tr>" +
            '<td class="px-6 py-4 font-bold text-[#5D4037]">' + esc(s.shopName) + "</td>" +
            '<td class="px-6 py-4">' + esc(s.owner) + "</td>" +
            '<td class="px-6 py-4 font-mono text-gray-500">MST0' + (1000 + idx) + '</td>' +
            '<td class="px-6 py-4 text-gray-500">' + esc(s.joined) + '</td>' +
            '<td class="px-6 py-4 text-right"><span class="rounded-full bg-emerald-50 px-3 py-1 text-[10px] font-black uppercase text-emerald-600">' + esc(s.status) + '</span></td>' +
            "</tr>"
        );
      })
          .join("");
      setHtml("admin-dashboard-shops", rows || '<tr><td class="px-6 py-10 text-center" colspan="5">Không có dữ liệu gian hàng</td></tr>');

      var catStats = data.categoryStats || {};
      chart(
          "admin-category-chart",
          "bar",
          {
            labels: Object.keys(catStats),
            datasets: [{
              label: "Số lượng sách",
              data: Object.keys(catStats).map(function (k) { return catStats[k]; }),
              backgroundColor: "#D19C74",
              borderRadius: 6
            }]
          },
          {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
              y: { beginAtZero: true, grid: { borderDash: [5, 5] } },
              x: { grid: { display: false } }
            }
          }
      );

      var stockBuckets = data.stockBuckets || {};
      chart(
          "admin-stock-chart",
          "doughnut",
          {
            labels: ["Thấp (Low)", "Bình thường", "Cao (High)"],
            datasets: [{
              data: [stockBuckets.low || 0, stockBuckets.normal || 0, stockBuckets.high || 0],
              backgroundColor: ["#ea580c", "#D19C74", "#5D4037"],
              borderWidth: 0,
              hoverOffset: 10
            }]
          },
          {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
              legend: {
                position: 'bottom',
                labels: { padding: 20, usePointStyle: true, font: { weight: 'bold' } }
              }
            },
            cutout: '70%'
          }
      );
      // populate small quick metrics from /dashboard if available
      getJson(API_ROOT + "/dashboard").then(function(dash) {
        try { setText('metric-orders', String(dash.ordersCount || 0)); } catch(e) {}
        try { setText('metric-newusers', String(dash.newUsers || 0)); } catch(e) {}
        try { setText('metric-revenue', vnd(dash.revenue || 0)); } catch(e) {}
      }).catch(function(){/* non-critical */});
    });
  }

  function initAdminBooks() {
    var qEl = document.getElementById("books-q");
    var cEl = document.getElementById("books-category");
    var sEl = document.getElementById("books-stock");
    var aEl = document.getElementById("books-approval");
    var acEl = document.getElementById("books-active");

    function loadCategories() {
      return getJson("/api/categories").then(function (cats) {
        if (!cEl) return;
        var opts = ['<option value="all">Tat ca danh muc</option>']
            .concat((cats || []).map(function (c) {
              return '<option value="' + esc(c.name) + '">' + esc(c.name) + '</option>';
            }))
            .join("");
        cEl.innerHTML = opts;
      }).catch(function () {
        if (cEl) cEl.innerHTML = '<option value="all">Tat ca danh muc</option>';
      });
    }

    function load() {
      var url = API_ROOT + "/books-all?" + qs({
        q: qEl ? qEl.value : "",
        category: cEl ? cEl.value : "all",
        stock: sEl ? sEl.value : "all",
        approvalStatus: aEl ? aEl.value : "all",
        active: acEl ? acEl.value : "all"
      });

      return getJson(url).then(function (page) {
        var rows = (page.content || []).map(function (r) {
          var stockBadge = r.stockBucket === "low"
              ? '<span class="rounded bg-rose-100 px-2 py-1 text-xs font-black text-rose-700">Low</span>'
              : r.stockBucket === "normal"
                  ? '<span class="rounded bg-amber-100 px-2 py-1 text-xs font-black text-amber-700">Normal</span>'
                  : '<span class="rounded bg-emerald-100 px-2 py-1 text-xs font-black text-emerald-700">High</span>';

          var appStatusClass = r.approvalStatus === "APPROVED" ? "text-emerald-600"
              : r.approvalStatus === "PENDING" ? "text-amber-600"
                  : "text-rose-600";

          var activeClass = r.active === "Active" ? "text-emerald-600" : "text-rose-600";

          var actionBtns = '<div class="flex gap-2 justify-end">';
          if (r.approvalStatus === "PENDING") {
            actionBtns += '<button onclick="adminApproveBook(' + r.id + ')" class="rounded border border-emerald-200 bg-white px-3 py-1.5 text-xs font-black text-emerald-600 hover:bg-emerald-50 transition-all shadow-sm" title="Duyệt sách">✓</button>';
            actionBtns += '<button onclick="adminRejectBook(' + r.id + ')" class="rounded border border-rose-200 bg-white px-3 py-1.5 text-xs font-black text-rose-600 hover:bg-rose-50 transition-all shadow-sm" title="Từ chối sách">✗</button>';
          }
          if (r.active === "Active") {
            actionBtns += '<button onclick="adminLockBook(' + r.id + ')" class="text-rose-600 hover:text-rose-800" title="Khóa">🔒</button>';
          } else {
            actionBtns += '<button onclick="adminUnlockBook(' + r.id + ')" class="text-emerald-600 hover:text-emerald-800" title="Mở khóa">🔓</button>';
          }
          actionBtns += '<button onclick="adminDeleteBook(' + r.id + ')" class="text-gray-400 hover:text-rose-600" title="Xóa">🗑️</button>';
          actionBtns += '</div>';

          return (
              "<tr>" +
              '<td class="px-4 py-3 font-bold">' + esc(r.title) + "</td>" +
              '<td class="px-4 py-3">' + esc(r.author) + "</td>" +
              '<td class="px-4 py-3 font-black text-brand-orange">' + vnd(r.price) + "</td>" +
              '<td class="px-4 py-3">' + esc(r.category) + "</td>" +
              '<td class="px-4 py-3 font-black ' + appStatusClass + '">' + esc(r.approvalStatus) + "</td>" +
              '<td class="px-4 py-3 font-black ' + activeClass + '">' + esc(r.active) + "</td>" +
              '<td class="px-4 py-3 text-right">' + actionBtns + "</td>" +
              "</tr>"
          );
        }).join("");

        setHtml("admin-books-body", rows || '<tr><td class="px-4 py-3" colspan="7">Khong co du lieu</td></tr>');
      });
    }

    [qEl, cEl, sEl, aEl, acEl].forEach(function (el) {
      if (el) el.addEventListener("input", load);
      if (el) el.addEventListener("change", load);
    });

    return loadCategories().then(load);
  }

  // Admin Book Actions
  window.adminLockBook = function(id) {
    if (!confirm("Bạn có chắc chắn muốn khóa sách này? Sách sẽ không hiển thị cho người mua.")) return;
    fetch("/api/admin/books/" + id + "/lock", { method: "PUT", headers: authHeaders() })
        .then(function(res) {
          if(res.ok) {
            BookomToast.success("Đã khóa sách thành công");
            document.getElementById("books-q").dispatchEvent(new Event("input"));
          } else {
            return res.text().then(function(text) {
              BookomToast.error("Lỗi khi khóa sách: " + (text || res.status));
            });
          }
        });
  };
  window.adminUnlockBook = function(id) {
    fetch("/api/admin/books/" + id + "/unlock", { method: "PUT", headers: authHeaders() })
        .then(function(res) {
          if(res.ok) {
            BookomToast.success("Đã mở khóa sách thành công");
            document.getElementById("books-q").dispatchEvent(new Event("input"));
          } else {
            return res.text().then(function(text) {
              BookomToast.error("Lỗi khi mở khóa sách: " + (text || res.status));
            });
          }
        });
  };
  window.adminDeleteBook = function(id) {
    if (!confirm("Xóa vĩnh viễn sách này? Thao tác này không thể hoàn tác.")) return;
    fetch("/api/admin/books/" + id, { method: "DELETE", headers: authHeaders() })
        .then(function(res) {
          if(res.ok) {
            BookomToast.success("Đã xóa sách khỏi hệ thống");
            document.getElementById("books-q").dispatchEvent(new Event("input"));
          } else {
            return res.text().then(function(text) {
              BookomToast.error("Lỗi khi xóa sách: " + (text || res.status));
            });
          }
        });
  };

  window.adminApproveBook = function(id) {
    if (!confirm("Duyệt sách này để cho phép bán trên hệ thống?")) return;
    fetch("/api/admin/books/" + id + "/status?status=APPROVED", { method: "PUT", headers: authHeaders() })
        .then(function(res) {
          if (res.ok) {
            BookomToast.success("Đã duyệt sách thành công");
            document.getElementById("books-q").dispatchEvent(new Event("input"));
          } else {
            return res.text().then(function(text) {
              BookomToast.error("Lỗi khi duyệt sách: " + (text || res.status));
            });
          }
        });
  };

  window.adminRejectBook = function(id) {
    if (!confirm("Từ chối sách này?")) return;
    fetch("/api/admin/books/" + id + "/status?status=REJECTED", { method: "PUT", headers: authHeaders() })
        .then(function(res) {
          if (res.ok) {
            BookomToast.success("Đã từ chối sách thành công");
            document.getElementById("books-q").dispatchEvent(new Event("input"));
          } else {
            return res.text().then(function(text) {
              BookomToast.error("Lỗi khi từ chối sách: " + (text || res.status));
            });
          }
        });
  };

  function initAdminOrders() {
    var qEl = document.getElementById("orders-q");
    var sEl = document.getElementById("orders-status");
    var dfEl = document.getElementById("orders-date-from");
    var dtEl = document.getElementById("orders-date-to");

    function load() {
      var url = API_ROOT + "/orders?" + qs({
        q: qEl ? qEl.value : "",
        status: sEl ? sEl.value : "all",
        dateFrom: dfEl ? dfEl.value : "",
        dateTo: dtEl ? dtEl.value : ""
      });

      return getJson(url).then(function (page) {
        var rows = (page.content || []).map(function (o) {
          var statusClass = o.status === "PROCESSING" ? "text-indigo-600"
              : o.status === "COMFIRMED" ? "text-amber-600"
                  : o.status === "SHIPPING" ? "text-blue-600"
                      : o.status === "COMPLETED" ? "text-emerald-600"
                          : "text-rose-600";

          return (
              "<tr>" +
              '<td class="px-4 py-3 font-bold">#' + esc(o.id) + "</td>" +
              '<td class="px-4 py-3">' + esc(o.buyer) + "</td>" +
              '<td class="px-4 py-3 font-black text-brand-orange">' + vnd(o.total) + "</td>" +
              '<td class="px-4 py-3 text-xs">' + esc(o.address) + "</td>" +
              '<td class="px-4 py-3 font-black ' + statusClass + '">' + esc(o.status) + "</td>" +
              '<td class="px-4 py-3">' + esc(new Date(o.date).toLocaleDateString("vi-VN")) + "</td>" +
              '<td class="px-4 py-3 text-right"><button onclick="viewOrderDetail(' + o.id + ')" class="rounded border border-brand-accent px-3 py-1 text-xs font-black">Chi tiết</button></td>' +
              "</tr>"
          );
        }).join("");

        setHtml("admin-orders-body", rows || '<tr><td class="px-4 py-3" colspan="7">Không có dữ liệu</td></tr>');
      });
    }

    [qEl, sEl, dfEl, dtEl].forEach(function (el) {
      if (el) el.addEventListener("input", load);
      if (el) el.addEventListener("change", load);
    });

    return load();
  }

  window.viewOrderDetail = function(orderId) {
    // Navigate to detail page or open modal
    // window.location.href = "/admin/orders/" + orderId;
    BookomToast.info("Chi tiết đơn hàng #" + orderId + " (Sẽ sớm cập nhật)");
  };

  function initAdminUsers() {
    var qEl = document.getElementById("users-q");
    var rEl = document.getElementById("users-role");
    var sEl = document.getElementById("users-status");

    function load() {
      var url = API_ROOT + "/users-detailed?" + qs({
        q: qEl ? qEl.value : "",
        role: rEl ? rEl.value : "all",
        status: sEl ? sEl.value : "all"
      });

      return getJson(url).then(function (rows) {
        var html = (rows || []).map(function (u) {
          var roleClass = u.role === "ADMIN" ? "bg-rose-50 text-rose-600"
              : u.role === "SELLER" ? "bg-violet-50 text-violet-600"
                  : "bg-blue-50 text-blue-600";

          var statusClass = u.status === "Active" ? "text-emerald-600" : "text-rose-600";

          var actionBtn = u.status === "Active"
              ? '<button class="inline-flex items-center gap-1.5 rounded-lg border border-rose-200 bg-white px-3 py-1.5 text-xs font-black text-rose-600 hover:bg-rose-50 transition-all shadow-sm" onclick="adminLockUser(' + u.id + ')"><span>🔒</span> Khóa</button>'
              : '<button class="inline-flex items-center gap-1.5 rounded-lg border border-emerald-200 bg-white px-3 py-1.5 text-xs font-black text-emerald-600 hover:bg-emerald-50 transition-all shadow-sm" onclick="adminUnlockUser(' + u.id + ')"><span>🔓</span> Mở khóa</button>';

          return (
              "<tr class='hover:bg-gray-50/50 transition-colors'>" +
              '<td class="px-6 py-4 font-bold text-[#5D4037]">' + esc(u.name) + "</td>" +
              '<td class="px-6 py-4 text-gray-500">' + esc(u.email) + "</td>" +
              '<td class="px-6 py-4"><span class="rounded-full px-3 py-1 text-[10px] font-black uppercase ' + roleClass + '">' + esc(u.role) + "</span></td>" +
              '<td class="px-6 py-4 text-gray-500">' + esc(u.joined) + "</td>" +
              '<td class="px-6 py-4 font-black ' + statusClass + '">' + (u.status === 'Active' ? 'Hoạt động' : 'Đang khóa') + "</td>" +
              '<td class="px-6 py-4 text-right">' + actionBtn + '</td>' +
              "</tr>"
          );
        }).join("");

        setHtml("admin-users-body", html || '<tr><td class="px-6 py-10 text-center text-gray-400 font-bold" colspan="6">Không tìm thấy người dùng phù hợp</td></tr>');
      });
    }

    [qEl, rEl, sEl].forEach(function (el) {
      if (el) el.addEventListener("input", load);
      if (el) el.addEventListener("change", load);
    });

    return load();
  }

  // Khóa tài khoản người dùng
  window.adminLockUser = function(userId) {
    if (!confirm("Bạn có chắc chắn muốn KHÓA tài khoản này? Người dùng sẽ không thể đăng nhập.")) return;

    fetch("/api/admin/users/" + userId + "/lock", {
      method: "PUT",
      headers: authHeaders()
    })
        .then(function(res) {
          if (!res.ok) {
            return res.text().then(function(text) {
              throw new Error(text || ("Lỗi HTTP " + res.status));
            });
          }
          return res.json();
        })
        .then(function() {
          BookomToast.success("Đã khóa tài khoản thành công");
          document.getElementById("users-q").dispatchEvent(new Event("input"));
        })
        .catch(function(err) {
          BookomToast.error("Lỗi khi khóa tài khoản: " + err.message);
        });
  };

  // Mở khóa tài khoản người dùng
  window.adminUnlockUser = function(userId) {
    fetch("/api/admin/users/" + userId + "/unlock", {
      method: "PUT",
      headers: authHeaders()
    })
        .then(function(res) {
          if (!res.ok) {
            return res.text().then(function(text) {
              throw new Error(text || ("Lỗi HTTP " + res.status));
            });
          }
          return res.json();
        })
        .then(function() {
          BookomToast.success("Đã mở khóa tài khoản thành công");
          document.getElementById("users-q").dispatchEvent(new Event("input"));
        })
        .catch(function(err) {
          BookomToast.error("Lỗi khi mở khóa: " + err.message);
        });
  };

  function initAdminShops() {
    var qEl = document.getElementById("shops-q");

    function load() {
      var url = API_ROOT + "/shops?" + qs({ q: qEl ? qEl.value : "" });
      return getJson(url).then(function (rows) {
        var html = (rows || []).map(function (s) {
          return (
              "<tr>" +
              '<td class="px-4 py-3 font-bold">' + esc(s.shopName) + "</td>" +
              '<td class="px-4 py-3">' + esc(s.owner) + "</td>" +
              '<td class="px-4 py-3">' + esc(s.legal) + "</td>" +
              '<td class="px-4 py-3">' + esc(s.products) + "</td>" +
              '<td class="px-4 py-3">' + esc(s.joined) + "</td>" +
              '<td class="px-4 py-3 text-right"><button class="rounded-lg bg-emerald-500 px-3 py-1.5 text-white text-xs font-black">Phe duyet</button></td>' +
              "</tr>"
          );
        }).join("");

        setHtml("admin-shops-body", html || '<tr><td class="px-4 py-3" colspan="6">Khong co du lieu</td></tr>');
      });
    }

    if (qEl) qEl.addEventListener("input", load);
    return load();
  }

  function initAdminCategories() {
    var listEl = document.getElementById('admin-categories-body');
    var createBtn = document.getElementById('create-category-btn');
    var nameInput = document.getElementById('create-category-name');
    var descInput = document.getElementById('create-category-desc');

    function load() {
      return getJson('/api/admin/categories').then(function(rows) {
        if (!listEl) return;
        var html = (rows || []).map(function(c) {
          return '<tr>' +
              '<td class="px-4 py-3 font-bold">' + esc(c.name) + '</td>' +
              '<td class="px-4 py-3">' + esc(c.description || '') + '</td>' +
              '<td class="px-4 py-3 text-right"><button class="text-rose-600" data-id="' + c.id + '">Xóa</button></td>' +
              '</tr>';
        }).join('');
        listEl.innerHTML = html || '<tr><td class="px-4 py-3" colspan="3">Không có danh mục</td></tr>';
        // wire delete buttons
        Array.from(listEl.querySelectorAll('button[data-id]')).forEach(function(btn) {
          btn.addEventListener('click', function() {
            var id = btn.getAttribute('data-id');
            if (!confirm('Xóa danh mục?')) return;
            fetch('/api/admin/categories/' + id, { method: 'DELETE', headers: authHeaders() })
                .then(function(res) { if(res.ok) { BookomToast.success('Đã xóa'); load(); } else { return res.text().then(function(t){ BookomToast.error(t || 'Lỗi'); }); } });
          });
        });
      });
    }

    if (createBtn && nameInput) {
      createBtn.addEventListener('click', function() {
        var name = (nameInput.value || '').trim();
        var desc = (descInput && descInput.value) || '';
        if (!name) return BookomToast.error('Tên danh mục bắt buộc');
        fetch('/api/admin/categories', { method: 'POST', headers: authHeaders(), body: JSON.stringify({ name: name, description: desc }) })
            .then(function(res) { if (res.status === 201 || res.ok) { BookomToast.success('Đã tạo'); nameInput.value=''; if(descInput) descInput.value=''; load(); } else { return res.text().then(function(t){ BookomToast.error(t || 'Lỗi'); }); } });
      });
    }

    return load();
  }

  function initSellerDashboard() {
    return getJson(API_ROOT + "/seller/analytics").then(function (ana) {
      setText("seller-metric-revenue", vnd(ana.estimatedRevenue || 0));
      setText("seller-metric-pending", String((ana.orderStatusCounts && (ana.orderStatusCounts["PROCESSING"] || ana.orderStatusCounts["Đang xác nhận"])) || 0));
      setText("seller-metric-products", String(ana.bookCount || 0));
      setText("seller-metric-low", String(ana.lowStock || 0));

      var orders = ana.recentOrders || [];
      var html = orders.map(function (o) {
        return (
            "<tr>" +
            '<td class="px-4 py-3 font-bold">#' + esc(o.id) + "</td>" +
            '<td class="px-4 py-3">' + esc(o.customer) + "</td>" +
            '<td class="px-4 py-3 text-xs">' + esc(o.item) + "</td>" +
            '<td class="px-4 py-3 font-black text-brand-orange">' + vnd(o.value) + "</td>" +
            '<td class="px-4 py-3 text-right text-[10px] font-black uppercase">' + esc(o.status) + "</td>" +
            "</tr>"
        );
      }).join("");

      setHtml("seller-dashboard-orders", html || '<tr><td class="px-4 py-3" colspan="5">Không có dữ liệu đơn hàng</td></tr>');
    }).catch(function(err) {
      console.error('Dashboard error (non-critical):', err);
    });
  }

  function initSellerOrders() {
    var qEl = document.getElementById("orders-q");
    var sEl = document.getElementById("orders-status");
    function getStatusColor(status) {
      switch(status) {
        case 'PROCESSING': return 'text-indigo-600';
        case 'COMFIRMED': return 'text-amber-600';
        case 'SHIPPING': return 'text-blue-600';
        case 'COMPLETED': return 'text-emerald-600';
        case 'CANCELLED': return 'text-slate-500';
        default: return 'text-gray-600';
      }
    }

    function getStatusLabel(status) {
      var labels = {
        'PROCESSING': 'Đang xác nhận',
        'COMFIRMED': 'Đã xác nhận',
        'SHIPPING': 'Đang giao',
        'COMPLETED': 'Đã hoàn thành',
        'CANCELLED': 'Đã hủy'
      };
      return labels[status] || status;
    }

    function load() {
      if (!window.ApiService || !ApiService.Order || !ApiService.Order.getSellerOrders) {
        setHtml("seller-orders-body", '<tr><td class="px-4 py-3" colspan="6">Thiếu ApiService</td></tr>');
        return Promise.resolve();
      }

      var query = (qEl ? qEl.value : "").toLowerCase();
      var statusFilter = sEl ? sEl.value : "all";

      return ApiService.Order.getSellerOrders().then(function (rows) {
        var orders = Array.isArray(rows) ? rows : [];

        var filtered = orders.filter(function (order) {
          var orderId = String(order.orderId || "");
          var buyerName = (order.buyerUsername || "").toLowerCase();
          var itemSummary = (order.itemSummary || "").toLowerCase();

          var matchQuery = !query ||
              orderId.includes(query) ||
              buyerName.includes(query) ||
              itemSummary.includes(query);

          var matchStatus = statusFilter === "all" || String(order.status) === statusFilter;

          return matchQuery && matchStatus;
        });

        var html = filtered.map(function (order) {
          var statusColor = getStatusColor(order.status);
          var statusLabel = getStatusLabel(order.status);
          var buyerName = order.buyerUsername || "--";
          var itemSummary = order.itemSummary || "--";

          var actionButtons = '';
          if (order.status === "COMPLETED" || order.status === "CANCELLED") {
            actionButtons = '<button class="rounded border border-brand-accent px-3 py-1 text-xs font-bold hover:bg-brand-accent hover:text-white transition" onclick="viewOrderDetail(' + (order.orderId || 0) + ')">Xem chi tiết</button>';
          } else {
            actionButtons = '<div class="flex gap-2 justify-end">' +
                '<button class="rounded border border-emerald-500 px-3 py-1 text-xs font-bold text-emerald-600 hover:bg-emerald-50 transition" onclick="confirmSellerOrder(' + (order.subOrderId || 0) + ',\'' + (order.status || "PROCESSING") + '\')">' +
                'Xác nhận</button>' +
                '<button class="rounded border border-rose-500 px-3 py-1 text-xs font-bold text-rose-600 hover:bg-rose-50 transition" onclick="cancelSellerOrder(' + (order.subOrderId || 0) + ')">Hủy</button>' +
                '<button class="rounded border border-brand-accent px-3 py-1 text-xs font-bold hover:bg-brand-accent hover:text-white transition" onclick="viewOrderDetail(' + (order.orderId || 0) + ')">Xem chi tiết</button>' +
                '</div>';
          }

          return (
              '<tr>' +
              '<td class="px-4 py-3 font-bold">#' + esc(order.orderId || order.subOrderId || "?") + '</td>' +
              '<td class="px-4 py-3 text-sm">' + esc(buyerName) + '</td>' +
              '<td class="px-4 py-3 text-sm">' + esc(itemSummary) + '</td>' +
              '<td class="px-4 py-3 font-black text-brand-orange">' + vnd(order.totalAmount) + '</td>' +
              '<td class="px-4 py-3 font-black ' + statusColor + '">' + esc(statusLabel) + '</td>' +
              '<td class="px-4 py-3 text-right">' + actionButtons + '</td>' +
              '</tr>'
          );
        }).join("");

        setHtml("seller-orders-body", html || '<tr><td class="px-4 py-3" colspan="6">Không có dữ liệu</td></tr>');
      }).catch(function (err) {
        console.error('Seller orders load failed:', err);
        setHtml("seller-orders-body", '<tr><td class="px-4 py-3" colspan="6">Lỗi tải dữ liệu</td></tr>');
      });
    }

    [qEl, sEl].forEach(function(el) {
      if (el) el.addEventListener("input", load);
      if (el) el.addEventListener("change", load);
    });

    window.viewOrderDetail = function(orderId) {
      window.location.href = '/main/order-details?orderId=' + orderId;
    };

    window.confirmSellerOrder = function(subOrderId, currentStatus) {
      var statusLabel = {
        'PROCESSING': 'Đã xác nhận',
        'COMFIRMED': 'Đang giao',
        'SHIPPING': 'Đã hoàn thành'
      }[currentStatus] || 'xác nhận';

      if (!confirm("Bạn có chắc muốn cập nhật trạng thái thành '" + statusLabel + "'?")) return;

      BookomToast.info('Đang cập nhật...');

      if (!window.ApiService || !ApiService.Order || !ApiService.Order.confirmSubOrder) {
        BookomToast.error('ApiService không khả dụng');
        return;
      }

      ApiService.Order.confirmSubOrder(subOrderId)
          .then(function(response) {
            BookomToast.success('✓ Cập nhật trạng thái thành công');
            setTimeout(function() { location.reload(); }, 800);
          })
          .catch(function(error) {
            BookomToast.error('❌ Lỗi: ' + (error.message || 'Cập nhật thất bại'));
            console.error(error);
          });
    };

    window.cancelSellerOrder = function(subOrderId) {
      if (!confirm("Bạn có chắc muốn hủy đơn hàng này? Hành động này không thể hoàn tác.")) return;

      BookomToast.info('Đang hủy đơn hàng...');

      if (!window.ApiService || !ApiService.Order || !ApiService.Order.updateSubOrderStatus) {
        BookomToast.error('ApiService không khả dụng');
        return;
      }

      ApiService.Order.updateSubOrderStatus(subOrderId, 'CANCELLED')
          .then(function(response) {
            BookomToast.success('✓ Đơn hàng đã được hủy');
            setTimeout(function() { location.reload(); }, 800);
          })
          .catch(function(error) {
            BookomToast.error('❌ Lỗi: ' + (error.message || 'Hủy thất bại'));
            console.error(error);
          });
    };

    return load();
  }

  function initSellerInventory() {
    var qEl = document.getElementById("inv-q");
    var cEl = document.getElementById("inv-category");
    var sEl = document.getElementById("inv-stock");

    function loadCategories() {
      return getJson("/api/categories").then(function (cats) {
        if (!cEl) return;
        var opts = ['<option value="all">Tat ca danh muc</option>']
            .concat((cats || []).map(function (c) { return '<option value="' + esc(c.name) + '">' + esc(c.name) + '</option>'; }))
            .join("");
        cEl.innerHTML = opts;
      });
    }

    function load() {
      // Use ApiService to load seller books from API
      if (typeof ApiService === 'undefined' || !ApiService.Book || !ApiService.Book.getSellerBooks) {
        console.error('ApiService.Book.getSellerBooks not available');
        setHtml("seller-inventory-body", '<tr><td colspan="6" class="px-4 py-3">Lỗi tải dữ liệu</td></tr>');
        return Promise.resolve();
      }

      var categoryId = (cEl && cEl.value !== "all") ? cEl.value : null;
      var query = qEl ? qEl.value : "";
      
      return ApiService.Book.getSellerBooks(query, categoryId, 0, 500).then(function(result) {
        // Handle both Page<Book> format and array format
        var books = result.content || result || [];
        
        if (!Array.isArray(books)) {
          books = [];
        }

        // Filter by stock if needed
        if (sEl && sEl.value !== "all") {
          var stockFilter = sEl.value;
          books = books.filter(function(b) {
            var stock = b.stockQuantity || 0;
            if (stockFilter === "low") return stock < 10;
            if (stockFilter === "normal") return stock >= 10 && stock < 50;
            if (stockFilter === "high") return stock >= 50;
            return true;
          });
        }

        var html = books.map(function(book) {
          var statusColor = book.approvalStatus === 'APPROVED' ? 'text-emerald-600' :
              book.approvalStatus === 'PENDING' ? 'text-amber-600' : 'text-red-600';
          var badge = book.stockQuantity < 10
              ? '<span class="rounded bg-rose-100 px-2 py-1 text-xs font-black text-rose-700">Low</span>'
              : book.stockQuantity < 50
                  ? '<span class="rounded bg-amber-100 px-2 py-1 text-xs font-black text-amber-700">Normal</span>'
                  : '<span class="rounded bg-emerald-100 px-2 py-1 text-xs font-black text-emerald-700">High</span>';

          return (
              "<tr>" +
              '<td class="px-4 py-3 font-bold">' + esc(book.title || '?') + "</td>" +
              '<td class="px-4 py-3">' + esc(book.author || '?') + "</td>" +
              '<td class="px-4 py-3 font-black text-brand-orange">' + vnd(book.price || 0) + "</td>" +
              '<td class="px-4 py-3">' + (book.stockQuantity || 0) + "</td>" +
              '<td class="px-4 py-3">' + badge + "</td>" +
              '<td class="px-4 py-3 text-right"><button class="rounded border border-brand-accent px-3 py-1 text-xs font-black" onclick="editBook(' + book.id + ')">Cập nhật</button></td>' +
              "</tr>"
          );
        }).join("");

        setHtml("seller-inventory-body", html || '<tr><td colspan="6" class="px-4 py-3">Không có sách</td></tr>');
      }).catch(function(err) {
        console.error('Failed to load books:', err);
        setHtml("seller-inventory-body", '<tr><td colspan="6" class="px-4 py-3">Lỗi tải dữ liệu từ server</td></tr>');
      });
    }

    [qEl, cEl, sEl].forEach(function (el) {
      if (el) el.addEventListener("input", load);
      if (el) el.addEventListener("change", load);
    });

    return loadCategories().then(load);
  }

  function initSellerAnalytics() {
    return getJson(API_ROOT + "/seller/analytics").then(function (ana) {
      setText("seller-ana-revenue", vnd(ana.estimatedRevenue || 0));
      setText("seller-ana-avg", vnd(ana.averagePrice || 0));
      setText("seller-ana-count", String(ana.bookCount || 0));
      setText("seller-ana-low", String(ana.lowStock || 0));

      var categoryCounts = ana.categoryCounts || {};
      var categoryRevenue = ana.categoryRevenue || {};
      var rows = Object.keys(categoryCounts).map(function (k) {
        return (
            "<tr>" +
            '<td class="px-4 py-3 font-bold">' + esc(k) + "</td>" +
            '<td class="px-4 py-3">' + esc(categoryCounts[k]) + "</td>" +
            '<td class="px-4 py-3 font-black text-brand-orange">' + vnd(categoryRevenue[k]) + "</td>" +
            "</tr>"
        );
      }).join("");
      setHtml("seller-analytics-body", rows || '<tr><td class="px-4 py-3" colspan="3">Khong co du lieu</td></tr>');

      chart(
          "seller-category-chart",
          "bar",
          {
            labels: Object.keys(categoryRevenue),
            datasets: [{ label: "Doanh thu", data: Object.keys(categoryRevenue).map(function (k) { return categoryRevenue[k]; }), backgroundColor: "#D19C74" }]
          },
          { responsive: true, plugins: { legend: { display: false } } }
      );

      var orderStatus = ana.orderStatusCounts || {};
      chart(
          "seller-order-status-chart",
          "doughnut",
          {
            labels: Object.keys(orderStatus),
            datasets: [{ data: Object.keys(orderStatus).map(function (k) { return orderStatus[k]; }), backgroundColor: ["#ea580c", "#D19C74", "#5D4037"] }]
          },
          { responsive: true }
      );
    });
  }

  // Simple toast utility using Tailwind classes
  function ensureToastContainer(){
    var container = document.getElementById('bookom-toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'bookom-toast-container';
      container.className = 'fixed top-4 right-4 flex flex-col gap-2 z-50';
      document.body.appendChild(container);
    }
    return container;
  }

  function createToast(type, message, ttl){
    ttl = ttl || 4000;
    var container = ensureToastContainer();
    var bg = type === 'success' ? 'bg-emerald-500' : type === 'error' ? 'bg-rose-500' : 'bg-slate-700';
    var ico = type === 'success' ? '✓' : type === 'error' ? '!' : 'i';
    var el = document.createElement('div');
    el.className = 'max-w-sm w-auto text-white px-4 py-2 rounded shadow-lg flex items-center gap-3 ' + bg + ' opacity-0 translate-y-2 transition-all';
    el.innerHTML = '<span class="font-bold">' + ico + '</span><div class="flex-1 text-sm">' + (message || '') + '</div>';
    container.appendChild(el);
    // enter
    requestAnimationFrame(function(){ el.classList.remove('opacity-0'); el.classList.add('opacity-100'); el.style.transform = 'translateY(0)'; });
    setTimeout(function(){
      // leave
      el.classList.add('opacity-0');
      el.style.transform = 'translateY(-8px)';
      setTimeout(function(){ container.removeChild(el); if(container.children.length===0) container.remove(); }, 300);
    }, ttl);
  }

  window.BookomToast = {
    success: function(msg, ttl){ createToast('success', msg, ttl); },
    error: function(msg, ttl){ createToast('error', msg, ttl); },
    info: function(msg, ttl){ createToast('info', msg, ttl); }
  };

  window.BookomPanelData = {
    initAdminDashboard: initAdminDashboard,
    initAdminBooks: initAdminBooks,
    initAdminUsers: initAdminUsers,
    initAdminShops: initAdminShops,
    initSellerDashboard: initSellerDashboard,
    initSellerOrders: initSellerOrders,
    initSellerInventory: initSellerInventory,
    initSellerAnalytics: initSellerAnalytics
    ,initAdminCategories: initAdminCategories
  };
})();
