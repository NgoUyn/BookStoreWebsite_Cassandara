# BOOKOM NoSQL - MongoDB Design Document

> Đồ án cuối kỳ NoSQL | Nguồn gốc: ứng dụng Spring Boot 3.2.4 + SQL Server (JPA/Hibernate/Flyway)
> Đích: Spring Boot + **MongoDB 8.x** (replica set) | Ngày cập nhật: 21/09/2026

---

## 1. Mục tiêu & phạm vi

| Mục tiêu | Cách làm |
|---|---|
| Thay SQL Server bằng MongoDB | 22 entity JPA → 6 aggregate root + 12 collection tham chiếu + hạ tầng |
| **Không phá vỡ ứng dụng** | Giữ nguyên hợp đồng REST (68 endpoint), 57 DTO, 21 file JS, Thymeleaf, JWT (userId vẫn là `Long`) |
| Dữ liệu phục vụ truy vấn cơ bản + nâng cao | `db/mongo/03_seed_reference.js`: 241 user, 400 sách, 600 đơn (12 tháng), 472 review, 5.000 activity log, 534 dòng daily_stats |
| Index chứng minh được hiệu năng | 85+ index (unique, compound, multikey, partial, TTL, collation, 2dsphere, text) + explain() so sánh COLLSCAN/IXSCAN |

---

## 2. Kiến trúc phân tầng

```
Controller (34 file - KHÔNG ĐỔI)
      |
Service (business, không biết Mongo)  →  service/mongo/* (hạ tầng: sequence, backup, query explorer)
      |
Repository / AggregationRepository    →  MongoTemplate + Aggregation pipeline
      |
Document (model/document + model/embedded)
      |
MongoDB (replica set rs0, db "bookom")
```

**Nguyên tắc bảo trì:**
1. Không Controller nào gọi `MongoTemplate` trực tiếp; chỉ `service/mongo/*` được dùng.
2. Chọn công cụ truy vấn theo bậc: derived query → `@Query` JSON → `Aggregation` → update pipeline.
3. Mọi index phải có nguồn code (`db/mongo/02_indexes.js`); `auto-index-creation=false`.
4. Mọi document có id số implements `SequencedDocument`; audit implements `AuditableDocument`.

---

## 3. SÁU AGGREGATE ROOT (thiết kế lõi)

| # | Collection | Thành phần EMBED (bounded) | Tham chiếu tới aggregate khác | Lý do |
|---|---|---|---|---|
| 1 | `users` | `profile{}`, `addresses[]` (≤5), `wishlist.bookIds[]` (≤200), `ml{}` (12 feature + RFM), `seller{}`, `stats{}` | orders, reviews | 1-1 và 1-ít, luôn đọc cùng nhau khi mở hồ sơ |
| 2 | `books` | `images{}`, `rating{avg,count,distribution}`, `stats{soldCount,viewCount,wishlistCount}`, `tags[]`, `boughtTogether[]` (top 10), `topReviews[]` (subset 3), `seller{}` snapshot | reviews, categories | Trang chi tiết/ danh mục đọc 1 document |
| 3 | `carts` | `items[]` (≤50) + `totals{}` | books, users | 1 document / buyer, ghi rất nhiều → 1 update atomic |
| 4 | `orders` | `subOrders[].items[]`, `shipping{}`, `payment{}`, `voucher{}`, `statusHistory[]`, `buyer{}` snapshot | users, books, payment_transactions | **1 insert = 1 đơn hoàn chỉnh** (multi-vendor), JSON y hệt bản SQL |
| 5 | `seller_shops` | `seller{}` snapshot, `location{geo}`, `stats{}` | books, orders | Bounded, cần geo/text index |
| 6 | `reviews` | `images[]` (≤5), `replies[]` (≤20, `$slice`), `helpfulCount`, `moderation{history[]}`, `book{}`/`user{}` snapshot | books, users | **Quan hệ 1-N KHÔNG trần + truy vấn cắt ngang** (theo bookId *và* userId) |

### 3.1 Vì sao `reviews` là aggregate RIÊNG (không embed vào `books`)

| Tiêu chí | Embed vào `books` | **Aggregate riêng (đã chọn)** |
|---|---|---|
| Giới hạn 16MB/document | Sách bestseller 50k review ⇒ phình, dễ chạm trần | Document `books` luôn nhỏ (chỉ summary + 3 review) |
| Ràng buộc "1 user 1 review/sách" | ❌ Không thể ràng buộc ở DB (cặp nằm trong cùng 1 document) | ✅ Unique index `{bookId:1,userId:1}` |
| Truy vấn theo user ("review của tôi") | ❌ Phải `$unwind` mảng trong `books` | ✅ Query trực tiếp `{userId}` với index |
| Write hotspot | ❌ Mọi review ghi vào cùng 1 document sách | ✅ Ghi vào document review độc lập |
| Mở rộng (reply, vote, ảnh, kiểm duyệt) | ❌ Sửa mảng lồng trong sách | ✅ Chỉ update document review |

→ `books` chỉ giữ **read-model tổng hợp** (`rating`) và **subset** (`topReviews`), cập nhật bằng `$merge`.

### 3.2 Đồng bộ giữa 2 aggregate (eventual consistency có kiểm soát)

| Chế độ | Cách làm | Dùng khi |
|---|---|---|
| Realtime | Session transaction (`MongoTransactionManager`): insert `reviews` + update `books.rating` | Người dùng vừa gửi review |
| Batch reconcile | Aggregation `$group` + `$merge` vào `books` | Định kỳ/bấm nút "Refresh views" |

---

## 4. Quy tắc quyết định EMBED hay REFERENCE

Một quan hệ chỉ nên **embed** khi trả lời CÓ cho **cả 5 câu**:
1. Luôn đọc cùng nhau? 2. Bounded (có trần thực tế)? 3. Cùng vòng đời?
4. Không gây write hotspot? 5. Không cần TTL/cleanup riêng?

| Ứng viên | Kết quả | Ghi chú |
|---|---|---|
| CartItem → carts | ✅ EMBED | ≤50 item |
| SubOrder/OrderItem/shipping/payment → orders | ✅ EMBED | Bounded, cùng vòng đời |
| Anh/rating/tags/stats → books | ✅ EMBED | Counter cập nhật bằng `$inc` |
| profile/address/ml/wishlist → users | ✅ EMBED | 1-1 / 1-ít |
| reviews → books | ❌ REFERENCE | Trượt (1), (2), (3), (4) |
| notification_deliveries → notifications | ❌ REFERENCE | Lịch sử retry không trần, queue worker poll theo `{status,nextRetryAt}` |
| payment_transactions → orders | ❌ REFERENCE | **TTL chỉ đặt trên collection gốc** - nếu embed, TTL xoá sẽ xoá luôn đơn hàng |
| activity_log, user_security_events → users | ❌ REFERENCE (time-series) | Không trần + cần TTL riêng |
| association_rules → books | ⚠️ Materialized view + subset | Recompute bằng `$merge`, subset top-10 vào `books.boughtTogether` |

---

## 5. Danh sách 20 collection

| Nhóm | Collection | Vai trò |
|---|---|---|
| Aggregate | `users`, `books`, `carts`, `orders`, `seller_shops`, `reviews` | 6 aggregate root |
| Reference | `categories`, `coupons`, `notifications`, `notification_deliveries`, `payment_transactions`, `order_returns`, `support_tickets`, `association_rules`, `refresh_tokens`, `otp_codes`, `distributed_locks` | 11 collection tham chiếu |
| Đặc biệt | `activity_log` (**time-series**, TTL 180 ngày) | Log hành vi/audit |
| Hạ tầng | `counters` (cấp Long id), `daily_stats` (materialized view) | Hỗ trợ migration |
| File | `fs.files` / `fs.chunks` (GridFS) | Ảnh bìa, avatar, attachment |

Tất cả có **validator `$jsonSchema`** (`validationLevel: moderate`, `validationAction: error`) — xem `01_create_collections_validators.js`.

### 5.1 Danh mục index quan trọng

| Collection | Index | Loại | Phục vụ |
|---|---|---|---|
| users | `uq_users_username`, `uq_users_email`, `uq_users_phone` | unique (+**partial**) | đăng nhập, chống trùng |
| users | `idx_users_role_active`, `idx_users_last_order`, `idx_users_ml_risk`, `idx_users_wishlist_books` | compound / multikey | admin lọc user, churn, "ai thả tim sách" |
| books | `idx_books_catalog{approvalStatus,isActive,categoryId}` | compound | trang chủ/danh mục |
| books | `idx_books_price_rating{finalPrice,"rating.avg"}` | compound (range+sort) | lọc giá & sắp theo rating |
| books | `txt_books` (title 10, author 5, tags 3, publisher 1) | **text có trọng số** | tìm kiếm |
| books | `idx_books_pinned` | **partial** (`isActive,isPinned`) | sách ghim |
| books | `idx_books_tags` | **multikey** | lọc theo tag |
| carts | `uq_carts_buyer`, `idx_carts_items_book` | unique / multikey | giỏ hàng |
| orders | `uq_orders_code`, `idx_orders_buyer_created` | unique / compound | tra cứu đơn |
| orders | `idx_orders_seller_status{"subOrders.sellerId","subOrders.status",createdAt}` | multikey | dashboard seller |
| orders | `idx_orders_payment_pending` | **partial** (`payment.status:PENDING`) | đơn chờ thanh toán |
| reviews | `uq_reviews_book_user` | **unique** (1 user/1 review/sách) | chống spam |
| reviews | `idx_reviews_book_visible`, `idx_reviews_user`, `idx_reviews_helpful` | compound | hiển thị/kiểm duyệt |
| coupons | `uq_coupons_code_ci` | unique + **collation en/2** | tìm mã không phân biệt hoa thường |
| coupons | `idx_coupons_expires_active` | **partial** | job hết hạn |
| seller_shops | `geo_shops_location` | **2dsphere** | tìm shop gần |
| notifications | `ttl_notifications_expires` | **TTL 0s** (`expiresAt`) | tự dọn thông báo cũ |
| refresh_tokens / otp_codes | `ttl_refresh_expiry`, `ttl_otp_expiry` | **TTL 0s** | hết hạn tự động |
| payment_transactions | `uq_pt_txn_code` | unique + **partial** (`$type:"string"`) | chống trùng giao dịch |
| association_rules | `uq_rules_pair`, `ttl_rules_stale` | unique / TTL 30 ngày | gợi ý mua kèm |

**Tổng: 100 index** (bao gồm `_id_`).

---

## 6. Ánh xạ SQL Server → MongoDB

| Bảng / đối tượng SQL | MongoDB | Ghi chú |
|---|---|---|
| `users`, `user_addresses`, `customer_ml`, `user_favorite_categories`, `user_wishlist_books` | `users` (embed) | 5 bảng → 1 document |
| `books` (+ ảnh, `average_rating`) | `books` (embed) + GridFS | thêm `finalPrice`, `categoryName`, `seller` snapshot |
| `category` | `categories` + denorm `categoryName` | `$graphLookup` cho cây danh mục |
| `carts`, `cart_items` | `carts.items[]` | bỏ 2 bảng & id item trung gian |
| `orders_master`, `sub_orders`, `order_items` | `orders.subOrders[].items[]` | 3 bảng → 1 document, JSON tương đương |
| `book_reviews` | `reviews` (aggregate riêng) | thêm `images[]`, `replies[]`, `moderation` |
| `coupons` | `coupons` | bỏ `LOWER(code)` nhờ collation |
| `notifications`, `notification_delivery` | `notifications` + `notification_deliveries` | `payload_json` string → `payload` BSON doc |
| `payment_transactions` | `payment_transactions` | thêm `ipnPayload` doc |
| `seller_shops` | `seller_shops` | thêm `location` 2dsphere |
| `association_rules` | `association_rules` + `books.boughtTogether` | materialized view |
| `refresh_tokens`, OTP (trong RAM) | `refresh_tokens`, `otp_codes` | TTL thay job dọn |
| `distributed_lock` + 3 stored procedure | `distributed_locks` | `findAndModify` (atomic lease) thay SP |
| `user_activity_log`, `user_security_events` | `activity_log` (time-series) | trước đây bảng không có repository nào dùng |
| IDENTITY | `counters` + `BeforeConvertCallback` | giữ `Long id`, không phá API |
| Flyway (35 migration) | `db/mongo/*.js` (idempotent) + `_migrations` (kế hoạch) | chạy bằng mongosh |
| H2 (test) | flapdoodle embedded mongo | `@DataMongoTest` |

---

## 7. Kỹ thuật NoSQL đã dùng (đối chiếu bài giảng)

| Kỹ thuật | Nơi dùng | Minh chứng |
|---|---|---|
| `$facet` | Dashboard 1 request + khai thác luật kết hợp | `05:A1`, `03:[$facet]` |
| `$lookup` (+ `let/pipeline`) | Doanh thu theo danh mục; 3 review mới nhất/sách | `05:A2`, `05:A3` |
| `$graphLookup` | Cây danh mục | `05:A4` |
| `$bucket`, `$bucketAuto` | Phân khúc giá trị đơn; chia nhóm giá sách | `05:A5`, `05:A6` |
| `$setWindowFields` | TB động 7 ngày + xếp hạng | `05:A7` |
| `$switch` + RFM | Phân khúc VIP/trung thành/nguy cơ rời | `05:A8` |
| `$merge` (materialized view) | `books.rating/topReviews/boughtTogether`, `users.stats`, `users.ml`, `seller_shops.stats`, `daily_stats` | `03:*`, `05:A9` |
| `$unionWith` | Doanh thu thuần = bán ∪ trả hàng | `05:A10` |
| `$densify` + `$fill` | Điền ngày thiếu doanh thu | `05:A11` |
| Text index có trọng số + `$meta textScore` | Tìm kiếm sách | `05:A12` |
| `$geoNear` (2dsphere) | Tìm shop gần người dùng | `05:A13` |
| TTL index | notification/refresh token/OTP/payment/rules | `02`, `05:A14` |
| Multi-document transaction | Trừ kho + ghi đơn, demo rollback | `05:A15` |
| Update bằng aggregation pipeline | Tính lại `rating` 1 thao tác | `05:A16` |
| Toán tử mảng | `$filter`, `$slice`, `$push+$slice(-20)` | `05:A17` |
| `explain("executionStats")` | IXSCAN 21 doc vs COLLSCAN toàn bộ | `05:A18` |
| `$jsonSchema` validator | Chặn dữ liệu sai tại DB | `01`, `05:A19` |
| Query profiler | `system.profile`, `planSummary` | `05:A20` |
| Change Streams | Bắt insert/update realtime | `05` (cuối file) |
| Time-series collection | `activity_log` + TTL 180 ngày | `01`, `03` |
| `counters` + `findAndModify $inc` | Cấp Long id | `MongoSequenceService.java` |
| Partial / collation / multikey index | Ràng buộc & tối ưu | `02_indexes.js` |
| GridFS | Lưu ảnh (Phase 4) | `MongoConfig.gridFsTemplate` |

---

## 8. Quy ước bảo trì & mở rộng

### 8.1 Cấu trúc package (đã tạo)

```
com.example.bookstore
├── config/mongo/     MongoConfig · MongoIdAssignmentCallback · MongoIndexVerifier
├── model/
│   ├── document/     SequencedDocument · AuditableDocument · Counter (… Book, User, Cart, Order, Review)
│   └── embedded/     CartItemEmbedded · SubOrderEmbedded · OrderItemEmbedded · RatingSummary · …
├── repository/       (derived + @Query JSON)
│   └── aggregation/  (MongoTemplate + Aggregation cho truy vấn nâng cao)
├── service/          (business, không biết Mongo)
│   └── mongo/        MongoSequenceService · (Phase 4: BackupService, QueryExplorerService, …)
└── mapper/           Document ↔ DTO (giữ nguyên shape JSON)
```

### 8.2 Thêm một chức năng mới (quy trình 5 bước)

1. Xác định **aggregate** chứa dữ liệu đó (bảng ở mục 4) — nếu chưa có, tạo collection + validator + index trong `01/02`.
2. Tạo document/embedded class implements `SequencedDocument` (+ `AuditableDocument`).
3. Tạo repository (derived/`@Query`) hoặc aggregation repository nếu cần `$group/$lookup/$facet`.
4. Service gọi repository; **không** đụng `MongoTemplate` trực tiếp.
5. Thêm index vào `02_indexes.js` + cập nhật `MongoIndexVerifier.requiredIndexes()`.

### 8.3 Quy tắc chọn công cụ truy vấn

| Tình huống | Dùng |
|---|---|
| Lọc/sắp/xoá đơn giản trên 1 collection | Derived query |
| Truy vấn JSON ngắn (regex, `$in`, range, `$expr`) | `@Query("{...}")` |
| Cần `$unwind/$group/$lookup/$facet/$merge` | `Aggregation` + `MongoTemplate` |
| Cần tính lại summary trong cùng document | Update bằng aggregation pipeline |
| Cần phản hồi < 100ms cho dữ liệu tổng hợp | Materialized view bằng `$merge` (+ Redis nếu cần) |

### 8.4 Quy tắc bắt buộc

- **Index-as-code**: mọi index nằm trong `02_indexes.js`; `auto-index-creation=false`.
- **Snapshot consistency**: đổi tên shop/user/category phải cập nhật snapshot (`books.seller.shopName`, `orders.subOrders[].seller.shopName`, `reviews.book.title`) bằng `updateMany` (Phase 4: `SnapshotUpdater`).
- **Projection**: API danh sách sách không trả `topReviews`/`boughtTogether` khi không cần.
- **Không đặt TTL cho field con của document khác** (xem 9.2).
- **Migration**: mỗi thay đổi cấu trúc = 1 script idempotent + tăng `schemaVersion`.

---

## 9. "Bẫy" đã gặp thật khi làm (phần phân tích cho báo cáo)

| # | Vấn đề | Nguyên nhân | Cách xử lý |
|---|---|---|---|
| 9.1 | Index `unique + sparse` lỗi `E11000 dup key {transactionCode: null}` | `sparse` chỉ bỏ qua field **THIẾU**; giá trị `null` vẫn được index | Đổi sang **partial index** `partialFilterExpression: {$type:"string"}` |
| 9.2 | `$merge` báo chạy OK nhưng dữ liệu **không đổi** | Trong `whenMatched` pipeline, `$field` trỏ vào document **đích**; phải dùng `$$new.field` mới là document đến | Sửa tất cả `whenMatched` sang `$$new.*` (5 chỗ) |
| 9.3 | Khai thác luật kết hợp ra **0 cặp** | `$unwind` trên scalar không khôi phục mảng ⇒ tích Descartes rỗng | `$project` **nhân bản mảng** (`bookA`, `bookB`) rồi mới unwind 2 lần |
| 9.4 | Ra cặp nhưng **0 luật** đạt ngưỡng | Dữ liệu random thuần ⇒ lift ≈ 1.0 (không có tương quan) | Thêm "sách hot" (70% basket lấy từ 25 sách) ⇒ 16 luật thật |
| 9.5 | `$densify` lỗi `field type must be numeric or date` | `dateKey` là string | `$dateFromString` → `$densify` → `$fill` |
| 9.6 | Lỗi `await is only allowed within async functions` | `mongosh --file` chạy như CommonJS | Đưa change stream vào async IIFE ở **cuối** file |
| 9.7 | `countDocuments is not a function` | Gọi trên cursor thay vì collection | `db.orders.countDocuments({...})` |
| 9.8 | Validator chặn dữ liệu hợp lệ khi app ghi | `strict` + `required` quá chặt | Dùng `moderate`, chỉ `required` field lõi |
| 9.9 | Không ràng buộc được "1 user 1 review/sách" nếu embed review vào `books` | Cặp `(bookId,userId)` nằm trong **cùng** document ⇒ DB không kiểm soát chéo document | `reviews` là aggregate riêng + **unique index** `{bookId,userId}` |

---

## 10. Cách chạy (Quick start)

```bat
REM 1) Khoi dong MongoDB (replica set single-node, cong 27018)
tools\mongo-dev-start.bat

REM 2) Tao DB + validator + index + du lieu + chay truy van
tools\mongo-run-scripts.bat            REM chay 00..05
tools\mongo-run-scripts.bat 02 03      REM hoac chay chon loc

REM 3) Mo shell / GUI
tools\mongo-shell.bat                  REM mongosh
REM GUI: MongoDB Compass -> mongodb://127.0.0.1:27018/?replicaSet=rs0

REM 4) Backup / Restore (Database Tools da cai: MongoDB Tools 100.18.0)
tools\mongo-backup.bat                    REM -> backups\bookom_<ngay>_<gio>.archive
tools\mongo-verify-backup.bat             REM dump + restore vao DB test + DOI CHIEU so document
tools\mongo-restore.bat backups\bookom_....archive
tools\mongo-import.bat  <collection> <file> [json|jsonArray|csv] [--drop]
tools\mongo-export.bat  <collection> <file> [json|csv]

REM 5) Mo GUI tool (MongoDB Compass 1.50)
tools\mongo-open-compass.bat              REM mo Compass voi dung URI (cong 27018!)

REM 6) Chay ung dung Spring Boot
.\mvnw.cmd spring-boot:run
```

**Môi trường hiện tại:** MongoDB Server 8.3 (service `MongoDB`, port 27017 – standalone, **không đụng tới**) và instance riêng của đồ án ở **port 27018** dạng replica set `rs0` (data `D:\mongo-data\rs0`). Đã có: `mongosh 2.12.0`, MongoDB Drivers. **Còn thiếu:** MongoDB Database Tools (`mongodump/mongoimport`) và MongoDB Compass → cài bằng `winget install --id MongoDB.DatabaseTools -e` và `winget install --id MongoDB.Compass.Full -e`.

### 10.1 Checklist theo tiêu chí chấm điểm

| Tiêu chí | Trạng thái | Minh chứng |
|---|---|---|
| **1 (2đ)** DB + dữ liệu + truy vấn | ✅ đã chạy | 20 collection + 19 validator + 100 index; 15 truy vấn cơ bản (`04`), 22 pipeline nâng cao (`05`), `06_verify_db.js` |
| **2 (0.5đ)** Import/Export + Backup/Restore | ✅ **đã kiểm chứng** | `tools\mongo-verify-backup.bat`: dump 20 collection → restore sang `bookom_restoretest` → **20/20 collection khớp 100%**; `mongo-export.bat` xuất 400 sách (446 KB) + `mongo-import.bat` nhập lại thành công 20 document |
| **3 (1đ)** Truy vấn trên GUI tool | 🚧 hướng dẫn xong, chờ chụp ảnh | `docs/mongodb/GUI_TOOL_GUIDE.md` (Compass 1.50 ở cổng **27018**): 7 pipeline JSON dán trực tiếp, Explain Plan, Indexes, Schema, Validation, Import/Export + checklist ảnh |
| **4 (0.5đ)** Kết nối CSDL với ứng dụng | ✅ build OK | `MongoConfig`, `application.properties`, `MongoSequenceService`, `MongoIdAssignmentCallback`, `MongoIndexVerifier`, actuator health |
| **5 (2đ)** Chức năng ứng dụng | 🚧 Phase 3 (đang làm) | Đã xong: 25 model `@Document`/embedded, 19 repository + 2 aggregation repository, CartService, WishlistService. Còn: OrderService, các Panel/Seller controller, 10 service khác, test |

### 10.1b Ghi chú triển khai (quyết định khi code — khác nhỏ so với thiết kế ban đầu)

| Hạng mục | Thiết kế ban đầu | Thực tế triển khai | Lý do |
|---|---|---|---|
| `customer_ml` | Embed hoàn toàn vào `users.ml` | **Giữ collection riêng** `customer_ml` (unique `userId`) **+ vẫn có** `users.ml` (tóm tắt + RFM) | Dữ liệu ML do pipeline riêng ghi theo lô; tách ra tránh ghi đè document người dùng đang hoạt động. `users.ml` phục vụ truy vấn nhanh (RFM, churn) |
| `images` của sách | Object `images{thumbnail,medium,large}` | Giữ object trong DB **+ getter tương thích** `getImageUrl()/getMediumImageUrl()/getLargeimageUrl()` | Frontend/JS đang đọc `book.imageUrl` → không phải sửa 21 file JS/template |
| `averageRating` | Chỉ có `rating.avg` | DB lưu `rating{}`, model expose `getAverageRating()` | JS/template dùng `book.averageRating` |
| `reviews` | Aggregate riêng | ✅ đúng thiết kế | unique `(bookId,userId)`, truy vấn cắt ngang |
| Id của con nhúng | `id` | `@Field("itemId")`, `@Field("subOrderId")`, `@Field("addressId")` | Khớp tên field đã seed/JS (tránh Spring Data map thành `_id` trong subdocument) |

### 10.2 Việc còn lại (theo thứ tự)

1. **Chụp ảnh Compass** theo `GUI_TOOL_GUIDE.md` (khoảng 15–18 ảnh) → lưu vào `docs/mongodb/screenshots/`.
2. **Phase 3**: chuyển 22 entity → document/embedded; 21 repository → MongoRepository/Aggregation; ~10 service (OrderService, CartService, BookService, BookReviewService, RecommendationJob, DistributedLockService, DatabaseSeederService…).
3. **Phase 4**: Backup Manager / Query Explorer / Explain Viewer / Index Manager trong panel admin; GridFS cho ảnh; Change Stream push SSE.
4. **Phase 5–6**: gỡ JPA + Flyway + mssql-jdbc khỏi `pom.xml`, xoá `db/migration/*.sql`, viết báo cáo + slide + kịch bản demo.
