# Hướng dẫn truy vấn trên GUI TOOL (MongoDB Compass) — Tiêu chí 3

> **QUAN TRỌNG – đọc trước:** đồ án dùng MongoDB ở **cổng 27018** (replica set `rs0`).
> Máy bạn có thêm MongoDB service khác ở **cổng 27017** (standalone, dữ liệu **không liên quan**).
> Nếu Compass mở ở 27017, bạn sẽ **không thấy database `bookom`** → đó không phải lỗi.

---

## 1. Kết nối Compass vào database của đồ án

**Cách 1 (nhanh):** chạy `tools\mongo-open-compass.bat` → tự mở Compass với đúng URI.

**Cách 2 (thủ công):** mở Compass → **New connection**

- Dán URI:
  ```
  mongodb://127.0.0.1:27018/bookom?replicaSet=rs0
  ```
- Hoặc "Fill in connection fields individually": Hostname `127.0.0.1`, Port `27018`,
  Authentication `None`, vào **Advanced Connection Options → Replica Set Name = rs0**.
- Bấm **Connect** → chọn database **bookom** (20 collection).

| Ảnh cần chụp | Nội dung |
|---|---|
| `GUI-01_connect.png` | Hộp thoại New connection với URI `...:27018/bookom?replicaSet=rs0` |
| `GUI-02_databases.png` | Danh sách database, thấy `bookom` (và `bookom_restoretest` sau khi test restore) |
| `GUI-03_collections.png` | Danh sách 20 collection của `bookom` (kèm số document/size) |

---

## 2. Bản đồ tính năng Compass ↔ tiêu chí chấm điểm

| Compass | Dùng cho | Việc cần làm để chụp minh chứng |
|---|---|---|
| Tab **Documents** | Truy vấn cơ bản (tiêu chí 3) | Nhập Filter + Projection + Sort + Skip/Limit đúng như `04_queries_basic.js` (Q1…Q15) → chụp kết quả |
| Tab **Aggregation** | Truy vấn nâng cao | Dán pipeline JSON ở mục 4 → chạy từng stage → chụp (mở rộng vài stage để thấy dữ liệu) |
| Tab **Explain Plan** | Chứng minh index | Bấm Explain cho query có index (IXSCAN) và query không index (COLLSCAN) → chụp 2 ảnh so sánh |
| Tab **Indexes** | Thiết kế index | Mở `orders` (10 index), `books` (10 index) → chụp danh sách + 1 index có `partialFilterExpression`/TTL |
| Tab **Schema** | Phân tích cấu trúc | Bấm **Analyze** cho `books`, `orders` → thấy tỉ lệ field, nested array |
| Tab **Validation** | Ràng buộc dữ liệu | Mở `books` → thấy `$jsonSchema` (validator) đã tạo |
| Nút **Import / Export** | Tiêu chí 2 | Export collection `books` ra JSON bằng GUI; Import lại vào collection test |
| Tab **Performance** (chỉ Atlas) | — | Không cần (dùng Query Profiler qua `05:A20` hoặc mongosh) |

> Compass **không** chạy được multi-document transaction (đó là việc của ứng dụng/driver) —
> phần transaction đã minh chứng bằng `05_queries_advanced.js` (A15) và sẽ minh chứng thêm ở tầng service Spring Boot.

---

## 3. Truy vấn CƠ BẢN trên Compass (tương ứng `04_queries_basic.js`)

Ví dụ Q1 — sách đã duyệt, còn bán, sắp theo giá:

- **Filter**: `{"approvalStatus": "APPROVED", "isActive": true}`
- **Projection**: `{"title": 1, "author": 1, "finalPrice": 1, "rating.avg": 1}`
- **Sort**: `{"finalPrice": 1}` — **Limit**: `5`

Các câu cần chụp (mỗi câu 1 ảnh, đặt tên `GUI-04_Qxx_*.png`):

| Câu | Filter | Điểm cần thấy trên ảnh |
|---|---|---|
| Q2 | `{"finalPrice": {"$gte": 100000, "$lte": 200000}, "publishYear": {"$gte": 2020}}` | range query |
| Q3 | `{"title": {"$regex": "^Nhung nguoi", "$options": "i"}}` | regex **có ^** (dùng được index) |
| Q4 | `{"tags": "ban chay"}` | **multikey index** |
| Q6 | `{"userId": 289, "isRead": false}` | compound index `{userId,isRead,createdAt}` |
| Q7 | `{"items": {"$elemMatch": {"bookId": 10021}}}` | `$elemMatch` trên mảng nhúng |
| Q9 | `{"subOrders": {"$elemMatch": {"sellerId": 37, "status": "PROCESSING"}}}` | mảng lồng 2 cấp |
| Q11 | `{"code": "bookom1k"}` | cần đổi collation → thấy Compass có ô **Collation** (locale `en`, strength `2`) |

---

## 4. Truy vấn NÂNG CAO — dán pipeline JSON vào tab Aggregation

Trong Compass: chọn collection → tab **Aggregation** → bấm **{}** (mở editor text) → dán mảng JSON
dưới đây → **Run**. Mỗi pipeline tương ứng 1 mục trong `05_queries_advanced.js`.

### P1. Dashboard 1 request bằng `$facet` (collection `orders`) — ảnh `GUI-05_P1_facet.png`
```json
[
  { "$match": { "isDeleted": { "$ne": true } } },
  { "$unwind": "$subOrders" },
  { "$match": { "subOrders.status": { "$ne": "CANCELLED" } } },
  { "$facet": {
      "overview": [ { "$group": { "_id": null, "revenue": { "$sum": "$subOrders.subTotal" },
                                  "orderCount": { "$sum": 1 }, "avgOrder": { "$avg": "$subOrders.subTotal" } } } ],
      "topSellers": [ { "$group": { "_id": "$subOrders.sellerId", "revenue": { "$sum": "$subOrders.subTotal" },
                                    "orders": { "$sum": 1 } } },
                      { "$sort": { "revenue": -1 } }, { "$limit": 3 } ],
      "statusBreakdown": [ { "$group": { "_id": "$subOrders.status", "count": { "$sum": 1 } } },
                           { "$sort": { "count": -1 } } ],
      "topBooks": [ { "$unwind": "$subOrders.items" },
                    { "$group": { "_id": "$subOrders.items.bookId", "title": { "$first": "$subOrders.items.title" },
                                  "sold": { "$sum": "$subOrders.items.quantity" } } },
                    { "$sort": { "sold": -1 } }, { "$limit": 3 } ]
  } }
]
```

### P2. `$lookup` có `let`/`pipeline` — 3 review mới nhất cho top sách (collection `books`) — `GUI-05_P2_lookup.png`
```json
[
  { "$match": { "rating.count": { "$gt": 0 } } },
  { "$sort": { "rating.avg": -1 } },
  { "$limit": 2 },
  { "$lookup": {
      "from": "reviews",
      "let": { "bookId": "$_id" },
      "pipeline": [
        { "$match": { "$expr": { "$eq": ["$bookId", "$$bookId"] }, "moderation.status": "VISIBLE" } },
        { "$sort": { "createdAt": -1 } },
        { "$limit": 3 },
        { "$project": { "_id": 0, "rating": 1, "user.username": 1, "comment": 1 } }
      ],
      "as": "latestReviews" } },
  { "$project": { "title": 1, "rating.avg": 1, "latestReviews": 1 } }
]
```

### P3. `$graphLookup` — cây danh mục (collection `categories`) — `GUI-06_P3_graph.png`
```json
[
  { "$match": { "parentId": null } },
  { "$limit": 3 },
  { "$graphLookup": { "from": "categories", "startWith": "$_id", "connectFromField": "_id",
                      "connectToField": "parentId", "as": "descendants", "maxDepth": 3, "depthField": "level" } },
  { "$project": { "name": 1, "descendants.name": 1, "descendants.level": 1 } }
]
```

### P4. `$setWindowFields` — trung bình động 7 ngày + xếp hạng (collection `daily_stats`) — `GUI-07_P4_window.png`
```json
[
  { "$group": { "_id": "$dateKey", "revenue": { "$sum": "$revenue" } } },
  { "$sort": { "_id": 1 } },
  { "$setWindowFields": {
      "sortBy": { "_id": 1 },
      "output": {
        "movingAvg7d": { "$avg": "$revenue", "window": { "documents": [-6, 0] } },
        "rankRevenue": { "$rank": {} } } } },
  { "$sort": { "_id": -1 } },
  { "$limit": 5 }
]
```

### P5. RFM segmentation (collection `orders`) — `GUI-08_P5_rfm.png`
```json
[
  { "$match": { "isDeleted": { "$ne": true }, "status": { "$ne": "CANCELLED" } } },
  { "$group": { "_id": "$buyerId", "lastOrder": { "$max": "$createdAt" },
                "frequency": { "$sum": 1 }, "monetary": { "$sum": "$totalAmount" } } },
  { "$set": { "recencyDays": { "$divide": [ { "$subtract": ["$$NOW", "$lastOrder"] }, 86400000 ] } } },
  { "$set": { "segment": { "$switch": {
      "branches": [
        { "case": { "$and": [ { "$lte": ["$recencyDays", 30] }, { "$gte": ["$monetary", 1000000] } ] }, "then": "VIP" },
        { "case": { "$and": [ { "$lte": ["$recencyDays", 90] }, { "$gte": ["$frequency", 2] } ] }, "then": "TRUNG_THANH" },
        { "case": { "$gte": ["$recencyDays", 180] }, "then": "NGUY_CO_ROI" } ],
      "default": "CAN_CHAM_SOC" } } } },
  { "$group": { "_id": "$segment", "soKhach": { "$sum": 1 }, "doanhThu": { "$sum": "$monetary" } } },
  { "$sort": { "soKhach": -1 } }
]
```

### P6. `$geoNear` — tìm shop gần Đà Nẵng (collection `seller_shops`) — `GUI-09_P6_geo.png`
```json
[
  { "$geoNear": { "near": { "type": "Point", "coordinates": [108.2022, 16.0544] },
                  "distanceField": "distanceMeters", "maxDistance": 500000, "spherical": true } },
  { "$project": { "shopName": 1, "city": 1,
                  "distanceKm": { "$round": [ { "$divide": ["$distanceMeters", 1000] }, 1 ] } } },
  { "$limit": 5 }
]
```

### P7. Full-text search có trọng số (tab **Documents** của `books`, không phải pipeline) — `GUI-10_text.png`
- Filter: `{"$text": {"$search": "\"khoi nghiep\" dao tao"}}`
- Projection: `{"score": {"$meta": "textScore"}, "title": 1, "author": 1}`
- Sort: `{"score": {"$meta": "textScore"}}`

---

## 5. Chứng minh INDEX bằng tab Explain Plan (rất quan trọng khi chấm)

| Bước | Filter trong tab Documents | Ảnh | Điểm giảng viên cần thấy |
|---|---|---|---|
| 1 | `{"approvalStatus": "APPROVED", "finalPrice": {"$gte": 100000, "$lte": 120000}}` → bấm **Explain** | `GUI-11_explain_IXSCAN.png` | Stage **IXSCAN**, `docsExamined` ≈ `nReturned` (ví dụ 21 vs 17), `executionTimeMillis` ≈ 1ms |
| 2 | `{"description": {"$regex": "chuyen sau"}}` → **Explain** | `GUI-12_explain_COLLSCAN.png` | Stage **COLLSCAN**, `docsExamined` = toàn bộ 400 sách |
| 3 | Lặp lại (2) nhưng thêm Projection `{"title": 1, "_id": 0}` | — | So sánh chi phí: quét toàn bộ vẫn phải đọc mọi document |

**Kết luận để ghi vào báo cáo:** index `idx_books_price_rating` giúp MongoDB đọc **21 document** thay vì **400**, giảm ~19 lần chi phí đọc.

---

## 6. Index / Schema / Validation / Import-Export trong Compass

| Việc | Thao tác | Ảnh |
|---|---|---|
| Xem index | collection `orders` → tab **Indexes** (10 index: unique, compound, **partial**, multikey `subOrders.sellerId`) | `GUI-13_indexes_orders.png` |
| Xem TTL index | collection `notifications` → Indexes → `ttl_notifications_expires` (`expireAfterSeconds: 0`) | `GUI-14_indexes_ttl.png` |
| Phân tích schema | collection `books` → tab **Schema** → **Analyze** (thấy `rating` nested, `tags` array) | `GUI-15_schema.png` |
| Xem validator | collection `books` → tab **Validation** (`$jsonSchema`, `validationLevel: moderate`, `action: error`) | `GUI-16_validator.png` |
| Export bằng GUI | `books` → nút **Export Data** → JSON → lưu `exports\books_compass.json` | `GUI-17_export.png` |
| Import bằng GUI | collection test → **Add Data → Import File** → chọn file vừa export | `GUI-18_import.png` |

---

## 7. Phương án dự phòng: `mongosh` (nếu Compass lỗi khi demo)

```bat
tools\mongo-shell.bat                     :: mo shell
tools\mongo-run-scripts.bat 04 05 06      :: chay toan bo truy van + in ket qua
```

Log đã sinh sẵn để đối chiếu:
- `logs\mongo-04-run.log` — kết quả 15 truy vấn cơ bản
- `logs\mongo-05-run.log` — kết quả 22 pipeline (kể cả `explain`, profiler, **change stream**)
- `logs\verify-restore.log` — biên bản backup/restore

---

## 8. Checklist nghiệm thu tiêu chí 3

- [ ] Ít nhất **8 truy vấn cơ bản** trên tab Documents (Q1–Q11) có ảnh
- [ ] Ít nhất **6 pipeline nâng cao** trên tab Aggregation (P1–P6) có ảnh
- [ ] 2 ảnh **Explain Plan** đối chiếu IXSCAN ↔ COLLSCAN + câu kết luận số document đọc được
- [ ] 1 ảnh tab **Indexes** (thấy index partial/multikey/TTL) và 1 ảnh **Validation**
- [ ] 1 ảnh **Export/Import** bằng GUI
- [ ] Lưu ảnh vào `docs\mongodb\screenshots\` theo đúng tên gợi ý ở trên, chèn vào báo cáo theo thứ tự


