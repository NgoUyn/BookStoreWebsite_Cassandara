document.addEventListener('DOMContentLoaded', () => {
    if (!window.ApiService) {
        console.error('ApiService not available');
        return;
    }

    const formatVnd = (value) => new Intl.NumberFormat('vi-VN').format(Math.max(0, Number(value) || 0)) + 'đ';

    const getOrderIdFromPage = async () => {
        const params = new URLSearchParams(window.location.search);
        const fromQuery = params.get('orderId');
        if (fromQuery) {
            return Number(fromQuery);
        }

        const fromStorage = localStorage.getItem('lastOrderId');
        if (fromStorage) {
            return Number(fromStorage);
        }

        const orders = await ApiService.Order.getBuyerOrders();
        const list = Array.isArray(orders) ? orders : [];
        return list.length > 0 ? Number(list[0].orderId || list[0].id) : null;
    };

    const bindOrderSuccess = async () => {
        const orderId = await getOrderIdFromPage();
        if (!orderId) {
            return;
        }

        const detailsLink = document.getElementById('success-order-details-link');
        if (detailsLink) {
            detailsLink.href = `/main/order-details?orderId=${orderId}`;
        }

        const data = await ApiService.Order.getDetail(orderId);
        const createdAt = data?.createdAt ? new Date(data.createdAt) : null;

        const orderIdText = `#BKO-${String(orderId).padStart(6, '0')}`;
        const orderIdMain = document.getElementById('order-id');
        const orderIdMini = document.getElementById('success-order-id-mini');
        const createdAtEl = document.getElementById('success-created-at');
        const buyerLine = document.getElementById('success-buyer-line');
        const shippingAddress = document.getElementById('success-shipping-address');
        const itemsTitle = document.getElementById('success-items-title');
        const totalAmount = document.getElementById('success-total-amount');
        const itemsList = document.getElementById('success-items-list');

        if (orderIdMain) orderIdMain.innerText = orderIdText;
        if (orderIdMini) orderIdMini.innerText = `BKO-${String(orderId).padStart(6, '0')}`;
        if (createdAtEl && createdAt) {
            createdAtEl.innerText = `Ngay dat: ${createdAt.toLocaleString('vi-VN')}`;
        }
        if (buyerLine) {
            buyerLine.innerText = `Nguoi mua: ${data?.buyerUsername || '--'}`;
        }
        if (shippingAddress) {
            shippingAddress.innerText = data?.shippingAddress || 'Chua co dia chi giao hang';
        }

        const items = Array.isArray(data?.items) ? data.items : [];
        if (itemsTitle) {
            itemsTitle.innerText = `Chi Tiet San Pham (${data?.totalItems || 0})`;
        }
        if (totalAmount) {
            totalAmount.innerText = formatVnd(data?.totalAmount || 0);
        }

        if (itemsList) {
            if (items.length === 0) {
                itemsList.innerHTML = '<div class="text-sm text-gray-500 font-semibold">Don hang chua co chi tiet san pham.</div>';
            } else {
                itemsList.innerHTML = items.map((item) => `
                    <div class="flex items-start gap-4">
                        <div class="w-12 aspect-[3/4] bg-[#2c3e50] border border-gray-200 rounded shadow-sm flex-shrink-0 flex items-center justify-center text-white text-center font-bold text-[5px] uppercase p-1">BOOK</div>
                        <div class="flex flex-col flex-grow justify-center">
                            <h4 class="font-bold text-brand-dark text-sm leading-snug line-clamp-1">${item.title || 'Khong co ten sach'}</h4>
                            <p class="text-xs text-gray-500 font-medium">Cung cap boi: ${item.sellerName || 'Shop'}</p>
                            <p class="text-xs text-gray-500 font-medium">Tac gia: ${item.author || 'Dang cap nhat'}</p>
                        </div>
                        <div class="flex flex-col items-end flex-shrink-0">
                            <div class="font-bold text-brand-dark text-sm">${formatVnd(item.lineTotal || 0)}</div>
                            <div class="text-xs text-gray-400 font-bold">SL: x${item.quantity || 0}</div>
                        </div>
                    </div>
                `).join('');
            }
        }
    };

    bindOrderSuccess().catch((error) => {
        console.error('Bind order success failed:', error);
    });

    window.copyOrderId = () => {
        const orderIdText = document.getElementById('order-id')?.innerText;
        if (!orderIdText) {
            return;
        }
        navigator.clipboard.writeText(orderIdText).then(() => {
            const tooltip = document.getElementById('copy-tooltip');
            tooltip?.classList.add('show');
            setTimeout(() => {
                tooltip?.classList.remove('show');
            }, 2000);
        });
    };

    const canvas = document.getElementById('confetti-canvas');
    if (!canvas) {
        return;
    }
    const ctx = canvas.getContext('2d');
    if (!ctx) {
        return;
    }

    let particles = [];
    const colors = ['#ea580c', '#D19C74', '#F8D9C0', '#16a34a', '#fcd34d', '#ffffff'];
    const maxParticles = 150;
    let animationId;

    const resizeCanvas = () => {
        canvas.width = window.innerWidth;
        canvas.height = window.innerHeight;
    };
    window.addEventListener('resize', resizeCanvas);
    resizeCanvas();

    class ConfettiParticle {
        constructor() {
            this.x = Math.random() * canvas.width;
            this.y = Math.random() * canvas.height - canvas.height;
            this.r = Math.random() * 6 + 4;
            this.d = Math.random() * maxParticles;
            this.color = colors[Math.floor(Math.random() * colors.length)];
            this.tilt = Math.floor(Math.random() * 10) - 10;
            this.tiltAngleInc = (Math.random() * 0.07) + 0.05;
            this.tiltAngle = 0;
            this.vy = (Math.random() * 2) + 2;
        }

        draw() {
            ctx.beginPath();
            ctx.lineWidth = this.r;
            ctx.strokeStyle = this.color;
            ctx.moveTo(this.x + this.tilt + this.r, this.y);
            ctx.lineTo(this.x + this.tilt, this.y + this.tilt + this.r);
            ctx.stroke();
        }

        update() {
            this.tiltAngle += this.tiltAngleInc;
            this.y += (Math.cos(this.d) + 1 + this.r / 2) / 2 + this.vy;
            this.x += Math.sin(this.d);
            this.tilt = Math.sin(this.tiltAngle) * 15;

            if (this.y > canvas.height) {
                this.y = -10;
                this.x = Math.random() * canvas.width;
            }
        }
    }

    for (let i = 0; i < maxParticles; i++) {
        particles.push(new ConfettiParticle());
    }

    let frameCount = 0;
    const renderConfetti = () => {
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        particles.forEach((p) => {
            p.update();
            p.draw();
        });

        frameCount++;
        if (frameCount < 400) {
            animationId = requestAnimationFrame(renderConfetti);
        } else {
            canvas.style.transition = 'opacity 2s ease';
            canvas.style.opacity = '0';
            setTimeout(() => {
                cancelAnimationFrame(animationId);
                ctx.clearRect(0, 0, canvas.width, canvas.height);
            }, 2000);
        }
    };

    renderConfetti();
});
