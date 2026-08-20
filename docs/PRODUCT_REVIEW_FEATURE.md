# Chức Năng Đánh Giá Sản Phẩm (Product Review Feature)

## 📋 Tổng Quan

Hệ thống đánh giá sản phẩm cho phép **Buyer đánh giá các sách đã mua** với các tính năng:
- ⭐ Đánh giá từ 1-5 sao
- 💬 Viết bình luận
- ✏️ Chỉnh sửa đánh giá
- 🗑️ Xóa đánh giá
- 📊 Xem thống kê đánh giá
- 🔍 Lọc theo số sao

---

## 🏗️ Kiến Trúc Hệ Thống

### Database (SQL Server)
**Bảng: `book_reviews`**
```sql
CREATE TABLE book_reviews (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    book_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment NVARCHAR(MAX) NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),
    is_hidden BIT NOT NULL DEFAULT 0,
    
    CONSTRAINT FK_book_reviews_book FOREIGN KEY (book_id) REFERENCES books(id),
    CONSTRAINT FK_book_reviews_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Indexes
CREATE INDEX IX_book_reviews_book_visible ON book_reviews(book_id, is_hidden, created_at DESC);
CREATE INDEX IX_book_reviews_book_rating ON book_reviews(book_id, rating) WHERE is_hidden = 0;
```

### Backend Architecture

#### Entities
- **BookReview** (`src/main/java/.../model/BookReview.java`)
  - Liên kết với Book và User
  - Fields: id, book, user, rating, comment, createdAt, isHidden

#### Repositories
- **BookReviewRepository** 
  - Custom queries: findByBookAndIsHiddenFalse, countRatingDistributionByBook
  - Hỗ trợ pagination và filtering

#### Services
- **BookReviewService** 
  - `addReview()` - Thêm đánh giá mới (validate: đã mua, rating 1-5)
  - `updateReview()` - Chỉnh sửa đánh giá (chỉ owner)
  - `deleteReview()` - Xóa đánh giá (chỉ owner)
  - `getBookReviews()` - Lấy danh sách đánh giá
  - `getBookReviewsByRating()` - Lọc theo số sao
  - `getRatingDistribution()` - Thống kê (1 sao: 50, 2 sao: 30, v.v.)
  - `getUserReviews()` - Lấy tất cả đánh giá của user
  - `getUserReviewForBook()` - Lấy đánh giá cụ thể của user cho sách

#### Controllers
- **BookReviewController** (`/api/reviews`)
  - GET endpoints: Lấy reviews, stats, distribution
  - POST: Tạo review mới
  - PUT: Cập nhật review
  - DELETE: Xóa review

---

## 🔌 API Endpoints

### Public Endpoints (Không cần auth)

#### 1. **Lấy danh sách đánh giá của sách**
```http
GET /api/reviews/book/{bookId}?page=0&size=10
```
**Response:**
```json
{
  "content": [
    {
      "id": 1,
      "book": { "id": 1, "title": "..." },
      "user": { "id": 1, "username": "buyer123" },
      "rating": 5,
      "comment": "Quyển sách rất hay!",
      "createdAt": "2025-05-20T10:30:00",
      "isHidden": false
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 50,
  "last": false
}
```

#### 2. **Lấy thống kê đánh giá**
```http
GET /api/reviews/book/{bookId}/stats
```
**Response:**
```json
{
  "averageRating": 4.5,
  "totalReviews": 50
}
```

#### 3. **Lấy phân bố đánh giá (Distribution)**
```http
GET /api/reviews/book/{bookId}/distribution
```
**Response:**
```json
{
  "1": 5,    // 5 reviews với 1 sao
  "2": 8,    // 8 reviews với 2 sao
  "3": 12,   // 12 reviews với 3 sao
  "4": 15,   // 15 reviews với 4 sao
  "5": 10    // 10 reviews với 5 sao
}
```

#### 4. **Lọc đánh giá theo số sao**
```http
GET /api/reviews/book/{bookId}/by-rating/{rating}?page=0&size=10
```
Ví dụ: `GET /api/reviews/book/1/by-rating/5` (lấy tất cả 5-sao reviews)

---

### Protected Endpoints (Yêu cầu đăng nhập BUYER)

#### 5. **Kiểm tra xem user đã đánh giá sách chưa**
```http
GET /api/reviews/book/{bookId}/user-review
Authorization: Bearer <token>
```
**Response:**
```json
{
  "hasReviewed": true,
  "review": {
    "id": 5,
    "rating": 4,
    "comment": "Tốt",
    "createdAt": "2025-05-20T10:30:00"
  }
}
```

#### 6. **Lấy tất cả đánh giá của user hiện tại**
```http
GET /api/reviews/my-reviews?page=0&size=10
Authorization: Bearer <token>
```

#### 7. **Tạo đánh giá mới**
```http
POST /api/reviews
Authorization: Bearer <token>
Content-Type: application/json

{
  "bookId": 1,
  "rating": 5,
  "comment": "Rất hay!"
}
```
**Validation Rules:**
- ✅ Rating: 1-5
- ✅ Comment: tối đa 2000 ký tự
- ✅ User phải đã mua sách (COMPLETED order)
- ✅ User chưa đánh giá sách này trước đó

**Response:** BookReview object

#### 8. **Chỉnh sửa đánh giá**
```http
PUT /api/reviews/{reviewId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "rating": 4,
  "comment": "Sửa lại bình luận"
}
```
**Rules:**
- Chỉ owner có thể sửa
- bookId không bắt buộc (vì đã biết từ URL)

#### 9. **Xóa đánh giá**
```http
DELETE /api/reviews/{reviewId}
Authorization: Bearer <token>
```
**Rules:**
- Chỉ owner có thể xóa

---

## 💻 Frontend Implementation

### HTML Form (Details_Produce.html)
```html
<!-- Review Stats Section -->
<div class="bg-white rounded-xl p-8 mb-10 shadow-sm">
  <div class="flex items-start gap-8">
    <div class="text-right">
      <div class="text-5xl font-black text-brand-orange mb-2">
        <span id="avg-rating-text">0.0</span> <span class="text-xl text-brand-dark">/ 5</span>
      </div>
      <div id="avg-stars-container" class="flex text-yellow-400 text-xl mb-1">
        <!-- Injected by JS -->
      </div>
      <div class="text-sm text-gray-500 font-medium">
        <span id="total-reviews-text">0</span> đánh giá
      </div>
    </div>

    <!-- Rating Filters -->
    <div class="flex-grow flex flex-wrap gap-3" id="rating-filters">
      <button class="bg-brand-orange text-white font-bold px-6 py-2 rounded-lg" data-filter="all">
        Tất Cả
      </button>
      <button class="bg-white border border-brand-border text-brand-dark font-medium px-6 py-2 rounded-lg" data-filter="5">
        5 Sao
      </button>
      <!-- More star filters -->
    </div>
  </div>
</div>

<!-- Review Form -->
<div id="review-form-container" class="hidden mb-10 bg-white border border-brand-orange/20 rounded-xl p-6">
  <h3 class="font-bold text-brand-dark mb-4">Viết đánh giá của bạn</h3>
  <form id="review-form" class="flex flex-col gap-4">
    <!-- Star rating input -->
    <div class="flex items-center gap-4">
      <span class="text-sm font-medium">Đánh giá của bạn:</span>
      <div id="star-rating-input" class="flex text-gray-300 text-2xl cursor-pointer">
        <span class="star hover:text-yellow-400 transition" data-value="1">★</span>
        <!-- More stars -->
      </div>
      <input type="hidden" id="selected-rating" value="5">
    </div>

    <!-- Comment textarea -->
    <textarea 
      name="comment" 
      id="review-comment" 
      rows="4"
      placeholder="Chia sẻ trải nghiệm của bạn..."
      maxlength="2000"
      class="border border-brand-border rounded-lg p-4 focus:outline-none focus:ring-2 focus:ring-brand-orange">
    </textarea>

    <!-- Buttons -->
    <div class="flex gap-2">
      <button type="submit" class="flex-1 bg-brand-orange text-white font-bold py-2 rounded-lg hover:bg-brand-brown transition">
        Gửi đánh giá
      </button>
      <button type="button" class="cancel-review-btn flex-1 bg-gray-200 text-gray-700 font-bold py-2 rounded-lg hover:bg-gray-300">
        Hủy
      </button>
    </div>
  </form>
</div>

<!-- Reviews List -->
<div id="reviews-list-container" class="bg-white rounded-xl p-8 shadow-sm">
  <!-- Reviews injected by JS -->
</div>
```

### JavaScript Features (details-page.js)

#### 1. **Tải thống kê**
```javascript
fetchReviewStats()    // Cập nhật average rating & total count
fetchRatingDistribution()  // Lấy phân bố (1-5 sao)
```

#### 2. **Hiển thị đánh giá**
```javascript
fetchReviews(page, append)  // Load reviews with edit/delete buttons
```

#### 3. **Chỉnh sửa đánh giá**
```javascript
loadReviewForEditing(reviewId)  // Pre-fill form với dữ liệu review
// Form submit sẽ PUT thay vì POST
```

#### 4. **Xóa đánh giá**
```javascript
deleteReview(reviewId)  // DELETE request + remove from DOM
```

#### 5. **Lọc theo sao**
```javascript
// Rating filter buttons automatically handle filtering
// GET /api/reviews/book/{bookId}/by-rating/{rating}
```

---

## 🔐 Security & Validation

### Backend Validation
```java
// Rating validation
if (rating < 1 || rating > 5) 
    throw new IllegalArgumentException("Đánh giá phải từ 1-5 sao");

// Comment length
if (comment.length() > 2000)
    throw new IllegalArgumentException("Bình luận tối đa 2000 ký tự");

// Purchase validation
boolean hasPurchased = orderItemRepository.hasUserPurchasedBook(user, bookId);
if (!hasPurchased) 
    throw new IllegalStateException("Phải mua sách trước khi đánh giá");

// Duplicate review check
if (reviewRepository.existsByBookAndUser(book, user))
    throw new IllegalStateException("Đã đánh giá sách này rồi");

// Owner check for edit/delete
if (!review.getUser().getId().equals(userId))
    throw new IllegalStateException("Không có quyền chỉnh sửa");
```

### Frontend Validation
```javascript
// Rating required (1-5)
// Comment max 2000 chars (HTML maxlength)
// Form submission checks rating value
// Only owner can see edit/delete buttons
```

### Authentication
- ✅ @PreAuthorize("hasRole('BUYER')")
- ✅ Chỉ BUYER role mới được phép
- ✅ JwtAuthenticatedPrincipal để lấy userId

---

## 📈 Business Logic

### One Review Per User Per Book
```java
// Prevent duplicates
if (reviewRepository.existsByBookAndUser(book, user))
    throw new IllegalStateException("Bạn đã đánh giá cuốn sách này rồi");
```

### Purchase Requirement
```java
// Chỉ cho phép review nếu đã mua sách
boolean hasPurchased = orderItemRepository.hasUserPurchasedBook(user, bookId);
```

### Admin Review Moderation
```java
// Admin có thể ẩn/hiện review (toggleReviewVisibility)
// Review ẩn không hiển thị cho public
// WHERE is_hidden = false (public queries)
```

---

## 🔄 Usage Flow

### Buyer Review Journey
1. **Mua sách** → Tạo Order với Status COMPLETED
2. **Xem trang chi tiết sách**
   - Thấy review stats (avg rating, count)
   - Thấy danh sách reviews từ buyers khác
   - Form để viết review (nếu chưa reviewed)
3. **Viết review**
   - Chọn số sao (1-5)
   - Viết bình luận (tuỳ chọn)
   - Gửi → Backend validate → Lưu vào DB
4. **Chỉnh sửa/Xóa**
   - Nếu review là của mình, có nút Edit/Delete
   - Edit → Pre-fill form → Submit PUT request
   - Delete → Xác nhận → DELETE request → Remove from list
5. **Thấy review của mình**
   - Hiển thị badge "Đánh giá của bạn"
   - Edit/Delete buttons visible

---

## 📊 Example Data Flow

```
Buyer Action: Post review with 5 stars
  ↓
POST /api/reviews
  {
    "bookId": 1,
    "rating": 5,
    "comment": "Rất hay!"
  }
  ↓
Controller validates & calls Service.addReview()
  ↓
Service validates:
  - Rating 1-5? ✓
  - User purchased book? ✓
  - User already reviewed? ✗
  ✓ All pass → Save to DB
  ↓
DB: INSERT into book_reviews (book_id, user_id, rating, ...)
  ↓
Response: BookReview {id: 10, rating: 5, comment: "Rất hay!", ...}
  ↓
Frontend: Show toast "Cảm ơn bạn đã đánh giá"
  ↓
Refresh: fetchReviewStats() + fetchReviews()
  ↓
Display updated: Avg rating, total reviews, new review in list
```

---

## 🚀 Performance Considerations

### Database Indexes
```sql
-- Fast retrieval of public reviews for a book
CREATE INDEX IX_book_reviews_book_visible 
  ON book_reviews(book_id, is_hidden, created_at DESC);

-- Fast rating distribution queries
CREATE INDEX IX_book_reviews_book_rating 
  ON book_reviews(book_id, rating) WHERE is_hidden = 0;
```

### Pagination
```java
Page<BookReview> reviews = repository.findByBookAndIsHiddenFalse(book, pageable);
// Default: 10 reviews per page
```

### Caching (Future Enhancement)
```java
// Could cache average rating & distribution
// Invalidate on new review
@Cacheable("bookAverageRating")
public Double getAverageRating(Long bookId) { ... }
```

---

## 🧪 Testing

### API Tests
```bash
# Create review
curl -X POST http://localhost:8080/api/reviews \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"bookId": 1, "rating": 5, "comment": "Great!"}'

# Get reviews
curl http://localhost:8080/api/reviews/book/1?page=0&size=10

# Update review
curl -X PUT http://localhost:8080/api/reviews/1 \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"rating": 4, "comment": "Updated comment"}'

# Delete review
curl -X DELETE http://localhost:8080/api/reviews/1 \
  -H "Authorization: Bearer <token>"
```

---

## 📝 Notes

- ✅ Hỗ trợ Tiếng Việt
- ✅ Responsive UI (Tailwind CSS)
- ✅ Pagination for performance
- ✅ Real-time UI updates
- ✅ Error handling & validation
- ✅ Admin moderation capability

---

## 🔮 Future Enhancements

1. **Helpful Votes** - "Bình luận này có hữu ích?" system
2. **Review Images** - Upload ảnh với review
3. **Seller Response** - Seller có thể reply to reviews
4. **Review Analytics** - Dashboard cho sellers
5. **Review Moderation Queue** - Admin dashboard
6. **Verified Purchase Badge** - Indicator for verified purchases
