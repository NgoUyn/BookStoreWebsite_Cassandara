# BOOKOM - Tài liệu dự án

> Ứng dụng bán sách **đa người bán** — Spring Boot 3.2.4 + **MongoDB 8.x** (replica set `rs0`).
> Phiên bản cũ (SQL Server + JPA/Hibernate/Flyway) đã được migrate sang NoSQL; lịch sử vẫn còn trong git.

## Chạy nhanh (5 bước)

```bat
tools\mongo-dev-start.bat                 REM 1) Bat MongoDB 27018 (replica set rs0)
tools\mongo-run-scripts.bat 00 01 02 03   REM 2) Collection + validator + 100 index + 20 danh muc
tools\mongo-import-sellers.bat            REM 3) 100 nha ban THAT (user + shop APPROVED)
tools\mongo-import-books.bat              REM 4) Toan bo sach tu db\seed\Books.csv (~248k quyen)
tools\run-app.bat                         REM 5) http://localhost:8080
```

Kiểm tra nhanh sau khi chạy: `powershell -NoProfile -ExecutionPolicy Bypass -File tools\smoke-test-api.ps1`

## Tài liệu chính

| Tài liệu | Nội dung |
|---|---|
| **[mongodb/DESIGN.md](./mongodb/DESIGN.md)** | **Tài liệu chính**: 6 aggregate root, embed vs tham chiếu, 100 index, so sánh SQL↔Mongo, 19 lỗi gặp phải & cách sửa, checklist tiêu chí chấm điểm, cách chạy, tài khoản demo |
| [mongodb/GUI_TOOL_GUIDE.md](./mongodb/GUI_TOOL_GUIDE.md) | Hướng dẫn MongoDB Compass (tiêu chí 3) + checklist ảnh chụp |
| `db/mongo/*.js` | Script DB: replica set, collection + validator, index, danh mục, 15 truy vấn cơ bản, 22 pipeline nâng cao, verify, purge dữ liệu bịa, import seller/books |
| `tools/*.bat`, `tools/*.ps1` | Script vận hành: start/stop MongoDB, shell, Compass, backup/restore/verify, chạy app, chạy test, smoke-test API, tạo tài khoản demo, import dữ liệu |

## Tài khoản mẫu (đăng nhập bằng email)

| Vai trò | Email | Mật khẩu |
|---|---|---|
| **ADMIN** | `admin@gmail.com` | `Admin123@` |
| **SELLER** | `nhaxuatbantre@bookom.vn` … (100 nhà bán) | `Nhaxuatbantre123@` … |
| **BUYER** | tự đăng ký qua `/main/auth` (OTP hiện ở popup DEV MODE khi chưa cấu hình SMTP) | — |

> Quy ước mật khẩu nhà bán: `<Tênbỏdấu>123@` — chữ đầu HOA, còn lại viết thường (VD `Nhanam123@`, `Nhasachdainam123@`).
> Danh sách 100 tên nhà bán nằm ở `db/seed/sellers_real.txt` (sửa rồi chạy lại `tools\build-sellers-seed.bat` + `tools\mongo-import-sellers.bat`).

## Ghi chú kỹ thuật

- **Không dùng SQL Server nữa**: `pom.xml` chỉ còn `spring-boot-starter-data-mongodb`; 35 file `db/migration/*.sql` đã gỡ.
- **`Books.csv` (73 MB)** đặt ở `db/seed/Books.csv` (không commit) để JAR nhẹ; `DatabaseSeederService` đọc theo `app.seeder.books-csv` và fallback `classpath:/Books.csv`.
- **Dữ liệu giao dịch (đơn hàng, review, giỏ, thông báo, log) để trống có chủ đích** — toàn bộ dữ liệu bịa đã bị xoá; hệ thống sinh lại dần khi dùng thật (đặt hàng, review qua UI) và qua các job/refresh (`$merge`, RecommendationJob, CustomerAnalysis).
- Ảnh bìa lấy từ cột `Image-URL-*` của dataset Book-Crossing; nếu ảnh Amazon không còn truy cập được, thay bằng ảnh nội bộ trong `src/main/resources/static/uploads/covers/`.
