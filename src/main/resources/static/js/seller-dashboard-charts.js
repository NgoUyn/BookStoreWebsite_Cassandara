/**
 * 📊 Seller Dashboard Charts - BOOKOM
 * File: seller-dashboard-charts.js
 * 
 * Xử lý tất cả biểu đồ cho Seller Dashboard:
 * - Stat Cards (4 cards)
 * - Revenue Bar Chart (theo ngày/tuần/tháng)
 * - Order Status Doughnut Chart
 * - Category Distribution Doughnut Chart
 * - Customer Growth Bar Chart
 * - Monthly Revenue Line Chart
 */

(function () {
    'use strict';

    // ==========================================
    // 1. CONSTANTS & CONFIG
    // ==========================================

    const API_ROOT = '/api/seller';
    const REFRESH_INTERVAL = 60000; // 60s auto-refresh

    // BOOKOM brand colors
    const COLORS = {
        brown: '#D19C74',
        dark: '#5D4037',
        accent: '#E5CBB5',
        cream: '#FAF5E8',
        orange: '#ea580c',
        peach: '#F8D9C0',
        white: '#FFFFFF'
    };

    // Status colors for doughnut chart
    const STATUS_COLORS = {
        'PROCESSING': '#F59E0B',       // amber - Đang xác nhận
        'COMFIRMED': '#6366F1',        // indigo - Đã xác nhận
        'SHIPPING': '#06B6D4',         // cyan - Đang giao
        'COMPLETED': '#10B981',        // emerald - Đã hoàn thành
        'CANCELLED': '#EF4444'         // red - Đã hủy
    };

    const STATUS_LABELS = {
        'PROCESSING': 'Đang xác nhận',
        'COMFIRMED': 'Đã xác nhận',
        'SHIPPING': 'Đang giao',
        'COMPLETED': 'Đã hoàn thành',
        'CANCELLED': 'Đã hủy'
    };

    // Category chart palette
    const CATEGORY_PALETTE = [
        '#D19C74', '#5D4037', '#F59E0B', '#6366F1',
        '#06B6D4', '#10B981', '#EF4444', '#8B5CF6',
        '#EC4899', '#14B8A6', '#F97316', '#84CC16'
    ];

    // ==========================================
    // 2. UTILITY FUNCTIONS
    // ==========================================

    function formatVND(value) {
        return new Intl.NumberFormat('vi-VN', {
            style: 'currency',
            currency: 'VND',
            maximumFractionDigits: 0
        }).format(value || 0);
    }

    function formatNumber(value) {
        return String(value || 0).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    }

    function getAuthHeaders() {
        var token = localStorage.getItem('accessToken') || localStorage.getItem('access_token') ||
            sessionStorage.getItem('accessToken') || sessionStorage.getItem('access_token');
        var headers = { 'Content-Type': 'application/json' };
        if (token) headers['Authorization'] = 'Bearer ' + token;
        var userId = localStorage.getItem('userId') || window.BookomDevUserId;
        if (!token && userId) headers['X-User-Id'] = String(userId);
        return headers;
    }

    function fetchJson(url) {
        return fetch(url, { headers: getAuthHeaders() }).then(function (res) {
            if (res.status === 401 || res.status === 403) {
                try {
                    localStorage.removeItem('accessToken');
                    localStorage.removeItem('access_token');
                    sessionStorage.removeItem('accessToken');
                    sessionStorage.removeItem('access_token');
                } catch (e) { }
                if (window.location.pathname !== '/main/auth') {
                    window.location.href = '/main/auth';
                }
                throw new Error('Unauthorized: ' + res.status);
            }
            if (!res.ok) throw new Error('HTTP ' + res.status);
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

    // ==========================================
    // 3. CHART MANAGER
    // ==========================================

    var charts = {};

    function createOrUpdateChart(id, type, data, options) {
        if (!window.Chart) {
            console.warn('Chart.js not loaded');
            return;
        }
        var canvas = document.getElementById(id);
        if (!canvas) {
            console.warn('Canvas #' + id + ' not found');
            return;
        }
        if (charts[id]) {
            charts[id].destroy();
        }
        charts[id] = new Chart(canvas, {
            type: type,
            data: data,
            options: options || {}
        });
    }

    // ==========================================
    // 4. RENDER STAT CARDS
    // ==========================================

    function renderStatCards(stats) {
        if (!stats) return;

        // Card 1: Doanh thu
        setText('stat-revenue', formatVND(stats.totalRevenue));
        animateNumber('stat-revenue');

        // Card 2: Đơn hàng
        setText('stat-orders', formatNumber(stats.totalOrders));
        animateNumber('stat-orders');

        // Card 3: Khách hàng
        setText('stat-customers', formatNumber(stats.uniqueCustomers));
        animateNumber('stat-customers');

        // Card 4: Sản phẩm
        setText('stat-products', formatNumber(stats.totalProducts));
        animateNumber('stat-products');
    }

    function animateNumber(elId) {
        var el = document.getElementById(elId);
        if (!el) return;
        el.style.transition = 'all 0.5s ease';
        el.style.opacity = '0.6';
        setTimeout(function () {
            el.style.opacity = '1';
        }, 100);
    }

    // ==========================================
    // 5. RENDER REVENUE BAR CHART
    // ==========================================

    function renderRevenueChart(revenueData, period) {
        if (!revenueData || revenueData.length === 0) return;

        var labels = revenueData.map(function (d) {
            var parts = d.date.split('-');
            if (period === 'week') {
                // Show day/month for week view
                return parts[2] + '/' + parts[1];
            }
            // Show day/month for month view
            return parts[2] + '/' + parts[1];
        });

        var values = revenueData.map(function (d) { return d.revenue; });

        // Format labels - show fewer labels if too many
        var displayLabels = labels.map(function (label, idx) {
            if (period === 'month' && labels.length > 15) {
                return idx % 3 === 0 ? label : '';
            }
            if (period === 'week') {
                return idx % 2 === 0 ? label : '';
            }
            return label;
        });

        createOrUpdateChart('revenue-bar-chart', 'bar', {
            labels: displayLabels,
            datasets: [{
                label: 'Doanh thu',
                data: values,
                backgroundColor: values.map(function (v) {
                    return v > 0 ? COLORS.brown : '#E5CBB5';
                }),
                borderColor: values.map(function (v) {
                    return v > 0 ? COLORS.brown : '#E5CBB5';
                }),
                borderWidth: 1,
                borderRadius: 6,
                borderSkipped: false,
                barPercentage: period === 'week' ? 0.7 : 0.5
            }]
        }, {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: function (context) {
                            return formatVND(context.raw);
                        }
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    grid: {
                        color: 'rgba(93, 64, 55, 0.08)',
                        borderDash: [5, 5]
                    },
                    ticks: {
                        callback: function (value) {
                            if (value >= 1000000) return (value / 1000000).toFixed(1) + 'tr';
                            if (value >= 1000) return (value / 1000).toFixed(0) + 'k';
                            return value;
                        },
                        font: { size: 11, weight: 'bold' },
                        color: '#5D4037'
                    }
                },
                x: {
                    grid: { display: false },
                    ticks: {
                        font: { size: 10, weight: 'bold' },
                        color: '#5D4037',
                        maxRotation: 45
                    }
                }
            }
        });
    }

    // ==========================================
    // 6. RENDER ORDER STATUS DOUGHNUT
    // ==========================================

    function renderOrderStatusChart(statusData) {
        if (!statusData) return;

        var labels = Object.keys(statusData).map(function (k) {
            return STATUS_LABELS[k] || k;
        });
        var values = Object.keys(statusData).map(function (k) { return statusData[k]; });
        var colors = Object.keys(statusData).map(function (k) {
            return STATUS_COLORS[k] || '#D19C74';
        });

        // Filter out zero values
        var filteredLabels = [];
        var filteredValues = [];
        var filteredColors = [];

        labels.forEach(function (label, idx) {
            if (values[idx] > 0) {
                filteredLabels.push(label);
                filteredValues.push(values[idx]);
                filteredColors.push(colors[idx]);
            }
        });

        if (filteredValues.length === 0) {
            filteredLabels = ['Chưa có dữ liệu'];
            filteredValues = [1];
            filteredColors = ['#E5CBB5'];
        }

        createOrUpdateChart('order-status-doughnut', 'doughnut', {
            labels: filteredLabels,
            datasets: [{
                data: filteredValues,
                backgroundColor: filteredColors,
                borderWidth: 2,
                borderColor: '#fff',
                hoverOffset: 12
            }]
        }, {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '65%',
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: {
                        padding: 12,
                        usePointStyle: true,
                        pointStyle: 'circle',
                        font: { size: 11, weight: 'bold' },
                        color: '#5D4037'
                    }
                },
                tooltip: {
                    callbacks: {
                        label: function (context) {
                            var total = context.dataset.data.reduce(function (a, b) { return a + b; }, 0);
                            var pct = total > 0 ? ((context.raw / total) * 100).toFixed(1) : 0;
                            return context.label + ': ' + context.raw + ' (' + pct + '%)';
                        }
                    }
                }
            }
        });
    }

    // ==========================================
    // 7. RENDER CATEGORY DISTRIBUTION DOUGHNUT
    // ==========================================

    function renderCategoryChart(categoryData) {
        if (!categoryData) return;

        var labels = Object.keys(categoryData);
        var values = Object.keys(categoryData).map(function (k) { return categoryData[k]; });

        // Filter out zero values
        var filteredLabels = [];
        var filteredValues = [];

        labels.forEach(function (label, idx) {
            if (values[idx] > 0) {
                filteredLabels.push(label);
                filteredValues.push(values[idx]);
            }
        });

        if (filteredValues.length === 0) {
            filteredLabels = ['Chưa có dữ liệu'];
            filteredValues = [1];
        }

        var colors = filteredLabels.map(function (_, idx) {
            return CATEGORY_PALETTE[idx % CATEGORY_PALETTE.length];
        });

        createOrUpdateChart('category-doughnut', 'doughnut', {
            labels: filteredLabels,
            datasets: [{
                data: filteredValues,
                backgroundColor: colors,
                borderWidth: 2,
                borderColor: '#fff',
                hoverOffset: 12
            }]
        }, {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '65%',
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: {
                        padding: 10,
                        usePointStyle: true,
                        pointStyle: 'circle',
                        font: { size: 10, weight: 'bold' },
                        color: '#5D4037',
                        boxWidth: 10
                    }
                },
                tooltip: {
                    callbacks: {
                        label: function (context) {
                            var total = context.dataset.data.reduce(function (a, b) { return a + b; }, 0);
                            var pct = total > 0 ? ((context.raw / total) * 100).toFixed(1) : 0;
                            return context.label + ': ' + context.raw + ' cuốn (' + pct + '%)';
                        }
                    }
                }
            }
        });
    }

    // ==========================================
    // 8. RENDER CUSTOMER GROWTH CHART
    // ==========================================

    function renderCustomerGrowthChart(growthData) {
        if (!growthData) return;

        var labels = Object.keys(growthData);
        var newCustomers = labels.map(function (k) { return growthData[k].new || 0; });
        var returningCustomers = labels.map(function (k) { return growthData[k].returning || 0; });

        createOrUpdateChart('customer-growth-chart', 'bar', {
            labels: labels,
            datasets: [
                {
                    label: 'Khách mới',
                    data: newCustomers,
                    backgroundColor: '#6366F1',
                    borderRadius: 4,
                    barPercentage: 0.4
                },
                {
                    label: 'Khách cũ',
                    data: returningCustomers,
                    backgroundColor: '#F59E0B',
                    borderRadius: 4,
                    barPercentage: 0.4
                }
            ]
        }, {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'top',
                    labels: {
                        usePointStyle: true,
                        padding: 16,
                        font: { size: 12, weight: 'bold' },
                        color: '#5D4037'
                    }
                },
                tooltip: {
                    callbacks: {
                        label: function (context) {
                            return context.dataset.label + ': ' + context.raw + ' khách';
                        }
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    grid: {
                        color: 'rgba(93, 64, 55, 0.08)',
                        borderDash: [5, 5]
                    },
                    ticks: {
                        stepSize: 1,
                        font: { size: 11, weight: 'bold' },
                        color: '#5D4037'
                    }
                },
                x: {
                    grid: { display: false },
                    ticks: {
                        font: { size: 10, weight: 'bold' },
                        color: '#5D4037'
                    }
                }
            }
        });
    }

    // ==========================================
    // 9. RENDER MONTHLY REVENUE CHART
    // ==========================================

    function renderMonthlyRevenueChart(monthlyData) {
        if (!monthlyData) return;

        var labels = Object.keys(monthlyData);
        var revenues = labels.map(function (k) { return monthlyData[k].revenue || 0; });
        var orders = labels.map(function (k) { return monthlyData[k].orders || 0; });

        createOrUpdateChart('monthly-revenue-chart', 'line', {
            labels: labels,
            datasets: [
                {
                    label: 'Doanh thu',
                    data: revenues,
                    borderColor: COLORS.brown,
                    backgroundColor: 'rgba(209, 156, 116, 0.1)',
                    fill: true,
                    tension: 0.4,
                    pointBackgroundColor: COLORS.brown,
                    pointBorderColor: '#fff',
                    pointBorderWidth: 2,
                    pointRadius: 5,
                    pointHoverRadius: 7,
                    yAxisID: 'y'
                },
                {
                    label: 'Đơn hàng',
                    data: orders,
                    borderColor: '#6366F1',
                    backgroundColor: 'rgba(99, 102, 241, 0.1)',
                    fill: true,
                    tension: 0.4,
                    pointBackgroundColor: '#6366F1',
                    pointBorderColor: '#fff',
                    pointBorderWidth: 2,
                    pointRadius: 5,
                    pointHoverRadius: 7,
                    yAxisID: 'y1'
                }
            ]
        }, {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false
            },
            plugins: {
                legend: {
                    position: 'top',
                    labels: {
                        usePointStyle: true,
                        padding: 16,
                        font: { size: 12, weight: 'bold' },
                        color: '#5D4037'
                    }
                },
                tooltip: {
                    callbacks: {
                        label: function (context) {
                            if (context.dataset.yAxisID === 'y') {
                                return 'Doanh thu: ' + formatVND(context.raw);
                            }
                            return 'Đơn hàng: ' + context.raw;
                        }
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    position: 'left',
                    grid: {
                        color: 'rgba(93, 64, 55, 0.08)',
                        borderDash: [5, 5]
                    },
                    ticks: {
                        callback: function (value) {
                            if (value >= 1000000) return (value / 1000000).toFixed(1) + 'tr';
                            if (value >= 1000) return (value / 1000).toFixed(0) + 'k';
                            return value;
                        },
                        font: { size: 11, weight: 'bold' },
                        color: '#5D4037'
                    }
                },
                y1: {
                    beginAtZero: true,
                    position: 'right',
                    grid: { display: false },
                    ticks: {
                        stepSize: 1,
                        font: { size: 11, weight: 'bold' },
                        color: '#6366F1'
                    }
                },
                x: {
                    grid: { display: false },
                    ticks: {
                        font: { size: 10, weight: 'bold' },
                        color: '#5D4037'
                    }
                }
            }
        });
    }

    // ==========================================
    // 10. PERIOD TOGGLE HANDLER
    // ==========================================

    var currentPeriod = 'week';

    function setupPeriodToggle() {
        var weekBtn = document.getElementById('period-week');
        var monthBtn = document.getElementById('period-month');

        if (weekBtn) {
            weekBtn.addEventListener('click', function () {
                if (currentPeriod === 'week') return;
                currentPeriod = 'week';
                weekBtn.classList.add('active-period');
                monthBtn.classList.remove('active-period');
                loadDashboardData();
            });
        }

        if (monthBtn) {
            monthBtn.addEventListener('click', function () {
                if (currentPeriod === 'month') return;
                currentPeriod = 'month';
                monthBtn.classList.add('active-period');
                weekBtn.classList.remove('active-period');
                loadDashboardData();
            });
        }
    }

    // ==========================================
    // 11. MAIN DATA LOADER
    // ==========================================

    function loadDashboardData() {
        var url = API_ROOT + '/dashboard-stats?period=' + currentPeriod;

        return fetchJson(url).then(function (data) {
            if (data.error) {
                console.error('Dashboard error:', data.error);
                return;
            }

            // Render all sections
            renderStatCards(data.summaryStats);
            renderRevenueChart(data.revenueByDay, currentPeriod);
            renderOrderStatusChart(data.orderStatusDistribution);
            renderCategoryChart(data.categoryDistribution);
            renderCustomerGrowthChart(data.customerGrowth);
            renderMonthlyRevenueChart(data.monthlyRevenue);

            // Update last refresh time
            var refreshEl = document.getElementById('last-refresh-time');
            if (refreshEl) {
                refreshEl.textContent = new Date().toLocaleTimeString('vi-VN');
            }
        }).catch(function (err) {
            console.error('Failed to load dashboard data:', err);
        });
    }

    // ==========================================
    // 12. INITIALIZATION
    // ==========================================

    function init() {
        // Check Chart.js availability
        if (!window.Chart) {
            console.error('Chart.js is not loaded. Please include Chart.js CDN.');
            return;
        }

        // Early auth check: redirect to login if no token
        var token = localStorage.getItem('accessToken') || localStorage.getItem('access_token') ||
            sessionStorage.getItem('accessToken') || sessionStorage.getItem('access_token');
        if (!token) {
            console.warn('No auth token found, redirecting to login');
            window.location.href = '/main/auth';
            return;
        }

        // Setup period toggle
        setupPeriodToggle();

        // Initial load
        loadDashboardData();

        // Auto-refresh every 60s
        setInterval(loadDashboardData, REFRESH_INTERVAL);

        console.log('📊 Seller Dashboard Charts initialized');
    }

    // Run on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
