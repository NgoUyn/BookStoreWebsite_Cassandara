# UC Feasibility Note - Multi-vendor BookStore

Cập nhật ngày: 2026-04-15

## Ghi chú quyết định
- Tài liệu này dùng để đánh giá mức khả thi UC theo trạng thái code hiện tại.
- `PROJECT_SUMMARY.md` đã được loại bỏ có chủ đích; file này là nguồn tổng hợp thay thế.

## Mức độ trưởng thành hiện tại
- Tổng quan: backend foundation tốt, buyer flow chính đã chạy end-to-end.
- Đánh giá nhanh: khoảng 86% cho phase backend foundation + buyer storefront/order flow.

Đã có:
- Multi-vendor domain model đầy đủ (role, cart, order, sub-order).
- Checkout tách đơn theo seller.
- Security nền tảng cho carts/orders (JWT filter + role guard theo route chính).
- OTP SMTP cho auth.
- Lưu avatar + favorite categories vào DB.
- Frontend bind API cho index/discovery/details/cart/checkout/order pages.

Chưa đầy đủ:
- Security coverage chưa full cho mọi endpoint admin/seller/books.
- Chưa có admin moderation đầy đủ (approve/reject flow hoàn chỉnh).
- Chưa có lock/unlock user đầy đủ.
- Chưa có E2E automation toàn luồng.

## Ma trận UC và khả năng triển khai

### BUYER
| UC | Khả năng hiện tại | Trạng thái | Kết luận |
|---|---|---|---|
| B01 Đăng ký/Đăng nhập/Hồ sơ | Cao | Có validation + OTP SMTP + profile update | Khả thi cao |
| B02 Tìm kiếm/Lọc sách | Cao | Có search/filter + approved-only | Khả thi cao |
| B03 Xem chi tiết sách | Cao | Có route chi tiết theo `/book/{id}` | Khả thi cao |
| B04 Quản lý giỏ hàng | Cao | API + UI bind đầy đủ thao tác chính | Khả thi cao |
| B05 Đặt hàng tách seller | Cao | Checkout API + checkout UI bind | Khả thi cao |
| B06 Theo dõi đơn cá nhân | Cao | `/api/orders/me` + `/api/orders/me/{orderId}` + UI bind | Khả thi cao |

### SELLER
| UC | Khả năng hiện tại | Trạng thái | Kết luận |
|---|---|---|---|
| S01 Hồ sơ shop | Trung bình-Cao | Có nền tảng qua user profile | Cần UX/API riêng |
| S02 Đăng bán sách chờ duyệt | Trung bình | Có model approvalStatus | Cần moderation flow hoàn chỉnh |
| S03 Quản lý kho | Trung bình | Có update cơ bản | Cần ownership-safe đầy đủ |
| S04 Xử lý đơn | Cao | Có update status sub-order + ownership guard | Khả thi cao |
| S05 Dashboard doanh thu | Trung bình | Có panel nền tảng | Cần KPI business chuẩn |

### ADMIN
| UC | Khả năng hiện tại | Trạng thái | Kết luận |
|---|---|---|---|
| A01 Dashboard tổng quan | Trung bình-Cao | Có panel + loader/refresh + toast | Cần hoàn thiện KPI |
| A02 Khóa/mở user | Thấp | Chưa có đầy đủ field/API | Cần ưu tiên |
| A03 Kiểm duyệt sách | Trung bình | Có status model | Cần API moderation đầy đủ |
| A04 Xem toàn bộ đơn | Trung bình | Có model/repo | Cần admin endpoint + filter |

## Đề xuất ưu tiên triển khai
1. Security hardening toàn tuyến.
2. Hoàn thiện buyer experience (lỗi/trạng thái/UX).
3. Hoàn thiện seller ownership-safe operations.
4. Hoàn thiện admin moderation + user management.
5. Thiết lập quality gate với integration + E2E tests.

## Delta mới nhất (2026-04-15)
- Auth: thêm OTP SMTP, register yêu cầu verify OTP.
- Profile: lưu avatar + favorite categories.
- Cart/Checkout: đã bind API thật và đặt hàng qua `/api/orders/me/checkout`.
- Orders: thêm API chi tiết đơn `GET /api/orders/me/{orderId}`.
- UI: Order_Success và Order_Details bind theo orderId.
- Doc: cập nhật ghi chú feasibility sau merge nhánh `origin/Scu`.
- Admin: chuẩn hóa layout/head theo `pageTitle`, thêm loader + nút refresh và toast.
- Admin: sidebar TailAdmin có thu gọn/mở rộng, dùng CSS riêng.
- Admin: fetch có auth header + xử lý chuyển về login khi 401/403.
- Kiểm chứng: compile và regression tests hiện có đều pass.

## Kết luận
- Các UC BUYER trọng tâm đã khả thi ở mức end-to-end cho demo.
- SELLER/ADMIN cần thêm một vòng hoàn thiện authorization, moderation và reporting để đạt production-ready.

acc test
admin2@gmail.com
admin123

    taskkill /F /IM java.exe

shop_nha_nam@gmail.com
seller123