# Technical Architecture - BOOKOM Multi-Vendor E-Commerce Platform

**Build**: Spring Boot 3.2.4 | **Java**: 17 | **Database**: SQL Server | **ORM**: JPA/Hibernate | **Build Tool**: Maven

---

## 1. System Architecture Overview

### 5-Layer Stack

```
┌──────────────────────────────────────────────────────┐
│ 1. Presentation Layer                                 │
│ - Thymeleaf Server-Side Templates (UI)               │
│ - REST JSON APIs (80+ endpoints)                      │
│ - Static Assets (CSS, JS, images)                     │
└──────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────┐
│ 2. Controller Layer                                   │
│ - Spring MVC Controllers (13 main)                    │
│ - Request validation & routing                        │
│ - JWT/OTP authentication filter                       │
│ - SSE subscription endpoint                           │
└──────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────┐
│ 3. Service Layer                                      │
│ - Business logic (Auth, Cart, Order, Recommendation) │
│ - Transaction management                              │
│ - Scheduling (@Scheduled jobs)                        │
│ - Distributed coordination                            │
└──────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────┐
│ 4. Repository Layer                                   │
│ - Spring Data JPA (15+ repositories)                  │
│ - Custom @Query methods                               │
│ - Entity mapping & lazy loading                       │
└──────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────┐
│ 5. Data Layer                                         │
│ - SQL Server database                                 │
│ - 15 core entities                                    │
│ - Flyway migrations (V1-V15)                          │
│ - Stored procedures (lock management)                 │
└──────────────────────────────────────────────────────┘
```

### Core Design Patterns

| Pattern | Usage |
|---------|-------|
| **MVC** | Controllers handle requests, dispatch to services |
| **Repository** | All data access through JPA repositories |
| **DTO** | Request/response objects to prevent entity exposure |
| **Service** | Business logic isolated from controllers |
| **Scheduled Jobs** | Background tasks (recommendations, heartbeat, queue worker) |
| **Observer** | SSE for real-time notification delivery |
| **Strategy** | Fallback engine for recommendations |
| **Filter Chain** | JWT authentication in Spring Security chain |

---

## 2. Module Breakdown (12 Core Features)

### 2.1 Authentication & User Management
**Controllers**: `AuthController`  
**Services**: `AuthService`, `AuthOtpService`  
**Entities**: `User`, `UserRole` (enum)

**Features**:
- Multi-role registration (BUYER, SELLER, ADMIN)
- OTP email verification
- JWT token generation & verification with `userId`, `roles`, and `sellerId` claims
- Secure password hashing (BCrypt)
- Custom `JwtAuthenticationFilter` in Spring Security chain
- Method-level authorization via `@PreAuthorize` and `CustomPermissionEvaluator`
- Principal-based request identity using `JwtAuthenticatedPrincipal`

**Status**: ✅ Complete

---

### 2.2 Catalog & Discovery
**Controllers**: `BookController`, `CategoryController`, `AdminBookController`  
**Services**: `BookService`, `CategoryService`  
**Repositories**: `BookRepository`, `CategoryRepository`  
**Entities**: `Book`, `Category`

**Features**:
- 8+ custom queries for search, filter, discover
- `searchApprovedBooks()` - full-text search across title, author, description
- `findSuggestions()` - relevance ranking with CASE statements
- `findBestSellingBooks()` - ORDER BY SUM(quantity) DESC
- `findTrendingBooks()` - recent purchases filtered by order date
- Category management with parent/child hierarchy
- Book approval workflow (PENDING → APPROVED/REJECTED)

**Status**: ✅ Complete

---

### 2.3 Shopping Cart
**Controllers**: `CartController`  
**Services**: `CartService`  
**Repositories**: `CartRepository`, `CartItemRepository`  
**Entities**: `Cart`, `CartItem`

**Features**:
- Add/update/remove items
- Stock validation (must be in inventory)
- Only approved books allowed
- Cart persistence per buyer
- Cart clear after checkout

**Status**: ✅ Complete

---

### 2.4 Multi-Vendor Order Processing
**Controllers**: `OrderController`  
**Services**: `OrderService`  
**Repositories**: `OrderRepository`, `SubOrderRepository`, `OrderItemRepository`  
**Entities**: `Order`, `SubOrder`, `OrderItem`

**Features**:
- Smart order splitting by seller
- Status lifecycle: PENDING_PAYMENT → PROCESSING → SHIPPING → COMPLETED/CANCELLED
- Per-seller sub-orders (SubOrder entities)
- Order history with advanced filtering
- Buyer/Seller-specific order views

**Data Model**:
```
Order (1) ──── (N) SubOrder (1) ──── (N) OrderItem
   |                  |
   +─ buyer_id        +─ seller_id
   +─ total_amount    +─ status
   +─ created_at      +─ quantity
```

**Status**: ✅ Complete

---

### 2.5 Seller Marketplace
**Controllers**: `SellerShopController`  
**Services**: `SellerShopService`  
**Repositories**: `SellerShopRepository`  
**Entities**: `SellerShop`

**Features**:
- Create/update seller profiles
- Unique shop slug for public URLs
- Approval workflow (PENDING/APPROVED/REJECTED)
- Public shop browsing (when approved)
- Seller inventory management

**Status**: ✅ Complete

**Notes**: Seller self-approval is blocked; approval remains an admin-controlled action.

---

### 2.6 Real-Time Notifications
**Controllers**: `NotificationController`  
**Services**: `NotificationService`, `NotificationSseService`, `NotificationDeliveryQueue`, `HeartbeatService`  
**Repositories**: `NotificationRepository`, `NotificationDeliveryRepository`  
**Entities**: `Notification`, `NotificationDelivery`

**Features**:
- SSE-based real-time delivery (Server-Sent Events)
- Exponential backoff retry (2s base, 2^n multiplier, max 5 attempts)
- Per-user SSE emitter management
- Unread count tracking
- Mark-as-read functionality
- Queue worker polling every 1 second
- Heartbeat refresh every 15 seconds

**Architecture**:
```
Notification created
    ↓
NotificationDelivery row inserted (PENDING)
    ↓
Queue worker polls every 1s
    ↓
SSE send attempted
    ↓
Success? Mark SENT : Retry (exponential backoff)
    ↓
Max retries exceeded? Mark DROPPED
```

**⚠️ Architecture Issue**: In-memory SSE map not cluster-safe (needs sticky sessions or pub/sub)

**Status**: ✅ Functional (architecture risk in multi-instance)

---

### 2.7 Buyer Profile & Security
**Controllers**: `BuyerProfileController`  
**Services**: `BuyerProfileService`  
**Repositories**: `UserAddressRepository`, `UserSecurityEventRepository`  
**Entities**: `UserAddress`, `UserSecurityEvent`

**Features**:
- Update profile information
- Manage multiple delivery addresses
- Default address support
- Password change with history
- Security event audit log
- CSRF protection

**Status**: ✅ Complete

**Notes**: Buyer identity is resolved from the authenticated principal; no fallback to the first user remains.

---

### 2.8 Wishlist
**Controllers**: `WishlistController`  
**Services**: `WishlistService`  
**Repositories**: `UserRepository` (ManyToMany relation)  
**Entities**: `User` (wishlist relationship)

**Features**:
- Add/remove books from wishlist
- View wishlist collection
- Simple persistence (no complex inventory)

**Status**: ✅ Complete

---

### 2.9 Recommendations (REFACTORED - Database-Backed)
**Controllers**: (No direct controller; accessed via `PageController`)  
**Services**: `RecommendationService`, `RecommendationJob`  
**Repositories**: `AssociationRuleRepository`, `OrderItemRepository`  
**Entities**: `AssociationRule` (NEW - V15 migration)  
**Algorithms**: `PairMiningAlgorithm`, `CosineSimilarityAlgorithm`

**What's New (May 2026 Refactor)**:
- ✅ Database-backed `association_rules` table (V15 migration)
- ✅ Sliding window queries (30 days) to prevent OOM
- ✅ Bulk insert via `saveAll()` instead of in-memory aggregation
- ✅ Removed `RecommendationCache` and `RecommendationCacheHolder`
- ✅ `CosineSimilarityAlgorithm` now actively used for similar books
- ✅ Cluster-safe (no in-memory state)

**"Bought Together" (Association Rules)**:
- Uses PairMiningAlgorithm to extract frequent item pairs
- Calculates: support, confidence, lift
- Filters by confidence >= 30% and lift > 1.0
- Query: `SELECT bookB FROM association_rules WHERE bookA = ? ORDER BY confidence DESC, lift DESC`

**"Similar Books" (Cosine Similarity)**:
- Fetches all books in same category
- Scores each using: (40% author match) + (40% category match) + (20% text TF-IDF)
- Returns top N by score

**Fallback Engine**:
- If insufficient recommendations from DB/algorithm, returns books by:
  - Same author (priority 1)
  - Same category (priority 2)

**Status**: ✅ Complete & Refactored (Production Ready)

---

### 2.10 Admin Controls
**Controllers**: `AdminBookController`, `AdminController` (implied)  
**Services**: `AdminService` (implied)

**Features**:
- Book approval workflow
- System monitoring
- Admin dashboard

**Status**: ✅ Complete

**Notes**: Admin endpoints rely on Spring Security annotations and authenticated principals; no request-attribute role propagation is used.

---

### 2.11 Health & Monitoring
**Controllers**: `HealthCheckController`

**Endpoints**:
- `GET /api/health` - General health
- `GET /api/health/live` - Liveness probe
- `GET /api/health/ready` - Readiness probe
- `GET /api/health/detailed` - Detailed system info
- `GET /api/health/queue-worker` - Background job status
- `GET /api/health/sse` - SSE connection pool status

**Status**: ✅ Complete (K8s ready)

---

### 2.12 Scheduled Background Jobs
**Services**: `RecommendationJob`, `HeartbeatService`, `NotificationDeliveryQueue`, `DatabaseSeederService`

| Job | Interval | Purpose |
|-----|----------|---------|
| `RecommendationJob` | Configurable (default 1h) | Precompute association rules + similar books |
| `HeartbeatService` | 15s | Refresh distributed lock, send client heartbeat |
| `NotificationDeliveryQueue` | 1s | Poll DB for pending notifications, retry failed |
| `DatabaseSeederService` | On startup (one-time) | Seed categories, users, books from CSV |

**Distributed Coordination**:
- `DistributedLockService` uses SQL Server stored procedures
- Only one instance acquires lock and runs job
- Lock TTL: 30s, refresh interval: 15s
- Graceful release on shutdown

**Status**: ✅ Complete

---

## 3. Database Schema

### Core Entities (15 tables)

| Entity | Purpose | Key Fields |
|--------|---------|-----------|
| `users` | Multi-role user accounts | id, username, email, role, passwordHash |
| `books` | Product catalog | id, title, author, category_id, price, approval_status |
| `categories` | Book categories | id, name, slug |
| `cart` | Buyer shopping cart | id, buyer_id |
| `cart_items` | Cart contents | id, cart_id, book_id, quantity |
| `orders` | Parent order records | id, buyer_id, total_amount, status |
| `sub_orders` | Seller-specific orders | id, order_id, seller_id, status |
| `order_items` | Items per sub-order | id, sub_order_id, book_id, quantity |
| `seller_shops` | Marketplace seller profiles | id, seller_id, name, slug, approval_status |
| `notifications` | Notification messages | id, user_id, type, title, message, read_at |
| `notification_delivery` | SSE delivery tracking | id, notification_id, status, attempt_count, next_retry_at |
| `user_addresses` | Delivery addresses | id, user_id, street, city, default_address |
| `user_security_events` | Audit log | id, user_id, event_type, timestamp |
| `association_rules` | **NEW (V15)** Recommendation pairs | rule_id, book_id_a, book_id_b, support, confidence, lift |
| `distributed_locks` | Queue coordination | lock_id, lock_name, holder_id, expires_at |

### Key Indexes

```sql
-- Association Rules (NEW - V15)
CREATE NONCLUSTERED INDEX IX_AssociationRules_BookA_Confidence_Lift
ON association_rules (book_id_a, confidence DESC, lift DESC)
INCLUDE (book_id_b);

CREATE NONCLUSTERED INDEX IX_AssociationRules_BookB
ON association_rules (book_id_b);

-- Book search optimization
CREATE NONCLUSTERED INDEX IX_Books_ApprovalStatus_Category
ON books (approval_status, category_id);

-- Notification delivery
CREATE NONCLUSTERED INDEX IX_NotificationDelivery_Status_NextRetry
ON notification_delivery (status, next_retry_at);
```

---

## 4. API Endpoints Reference

### Authentication (8 endpoints)
- `POST /api/auth/register-buyer`
- `POST /api/auth/register-seller`
- `POST /api/auth/request-otp`
- `POST /api/auth/verify-otp`
- `POST /api/auth/login-jwt`
- `GET /api/auth/me`
- `POST /api/auth/logout`
- `POST /api/auth/change-password`

### Books & Catalog (20+ endpoints)
- `GET /api/books` - Browse approved books
- `GET /api/books?keyword=...&category=...` - Search & filter
- `GET /api/books/{id}` - Book detail + recommendations
- `GET /api/books/suggestions` - Trending + suggestions
- `GET /api/categories` - Browse categories
- `POST /api/seller/books` - Create book listing
- `PUT /api/seller/books/{id}` - Update inventory
- `DELETE /api/seller/books/{id}` - Remove listing
- `GET /api/admin/books/pending` - Admin approval queue
- `PUT /api/admin/books/{id}/approve` - Admin approve

### Shopping & Orders (15 endpoints)
- `POST /api/cart/add` - Add to cart
- `PUT /api/cart/{itemId}` - Update quantity
- `DELETE /api/cart/{itemId}` - Remove item
- `GET /api/cart` - View cart
- `POST /api/checkout` - Place order
- `GET /api/orders` - Buyer order history
- `GET /api/orders/{id}` - Order detail
- `GET /api/seller/sub-orders` - Seller orders
- `PUT /api/seller/sub-orders/{id}/status` - Update order status
- `DELETE /api/orders/{id}/cancel` - Cancel order

### Recommendations (2 endpoints)
- `GET /api/books/{id}/bought-together` - Association rules
- `GET /api/books/{id}/similar` - Cosine similarity

### Notifications (5 endpoints)
- `GET /api/notifications` - List (paginated, unread count)
- `GET /api/notifications/{id}` - Get detail
- `PUT /api/notifications/{id}/read` - Mark read
- `PUT /api/notifications/read-all` - Mark all read
- `GET /api/notifications/me/subscribe` - SSE subscribe (WebSocket)

### Profile & Addresses (8 endpoints)
- `GET /api/profile` - Get profile
- `PUT /api/profile` - Update profile
- `POST /api/addresses` - Add address
- `GET /api/addresses` - List addresses
- `PUT /api/addresses/{id}` - Update address
- `DELETE /api/addresses/{id}` - Delete address
- `PUT /api/addresses/{id}/default` - Set default
- `GET /api/security-events` - View audit log

### Seller Shop (4 endpoints)
- `POST /api/seller/me/shop` - Create shop
- `GET /api/seller/me/shop` - Get my shop
- `PUT /api/seller/me/shop` - Update shop
- `GET /api/shops/{slug}` - Public shop view

### Wishlist (3 endpoints)
- `GET /api/wishlist` - List items
- `POST /api/wishlist/{bookId}` - Add to wishlist
- `DELETE /api/wishlist/{bookId}` - Remove from wishlist

### Health & Admin (6 endpoints)
- `GET /api/health` - General health
- `GET /api/health/live` - K8s liveness
- `GET /api/health/ready` - K8s readiness
- `GET /api/health/detailed` - System details
- `GET /api/health/queue-worker` - Job status
- `GET /api/health/sse` - SSE pool status

---

## 5. Security Model

### Authentication
- **JWT Tokens**: 
  - Generated on login (claims: userId, roles, sellerId, expiry)
  - Validated by `JwtAuthenticationFilter`
  - Custom `JwtTokenProvider` handles encode/decode

- **OTP Flow**:
  - 6-digit OTP generated, stored in-memory
  - Email sent via Spring Mail (if SMTP configured)
  - Must verify before registration

### Authorization
- **Role-Based Access Control (RBAC)**:
  - `UserRole` enum: BUYER, SELLER, ADMIN
  - Stored in `users.role` column
  - Enforced by `@PreAuthorize` and method security

- **Resource-Based Access Control (ABAC/Ownership)**:
  - `CustomPermissionEvaluator` checks resource ownership using repository-backed queries
  - Books, orders, sub-orders, and user/profile access avoid LAZY traversal for authorization

- **Data Isolation**:
  - Buyers see only own orders/profile/addresses
  - Sellers see only own shop/sub-orders/inventory
  - Admins see all pending books/approvals

### Password Security
- BCrypt hashing (Spring Security default)
- Password stored only as hash, never in plain text
- Password change creates `UserSecurityEvent` entry

### CORS & CSRF
- `@CrossOrigin` decorators on controllers
- Spring Security CSRF protection enabled
- Thymeleaf CSRF token in forms

### ⚠️ Known Security Issues
1. Seller can self-approve own shop
2. Security audit logging is still not centralized

---

## 6. Production Concerns

### Scalability Issues
- **SSE Connections**: Stored in-memory per instance (not cluster-safe)
  - **Fix**: Sticky sessions or shared pub/sub (Redis)
  
- **File Uploads**: Stored in `src/main/resources/static/` (local filesystem)
  - **Fix**: Use S3, Azure Blob Storage, or MinIO
  
- **N+1 Queries**: Potential in order/notification queries
  - **Fix**: Use fetch joins, projections, and pagination

### Performance Bottlenecks
- Recommendation job loads all orders initially (now fixed with sliding window)
- No pagination in some admin endpoints
- Lazy loading without careful query optimization

### Reliability Concerns
- Notification retry depends on DB lock/stored procedure
- No circuit breaker for external services (mail, Gemini)
- No centralized logging/metrics
- Missing cleanup jobs for old notification delivery logs

---

## 7. Recent Changes & Roadmap

### ✅ Completed (May 2026)
**Recommendation Engine Refactor**:
- Migrated from in-memory cache → database-backed
- Sliding window (30 days) to prevent OOM
- Integrated CosineSimilarityAlgorithm
- Removed `RecommendationCache` and `RecommendationCacheHolder`
- Added `AssociationRule` entity with composite index

### 🔄 In Progress
- None (stable phase)

### 📋 Planned
1. **Authentication Overhaul**:
   - Standardize role propagation to Spring Security context
   - Add role-based method security (@PreAuthorize)
   - Fix admin access control

2. **Payment Integration**:
   - Add payment gateway (Stripe/PayPal)
   - Implement transaction tracking
   - Support multiple payment methods

3. **Shipping & Fulfillment**:
   - Integrate shipping providers
   - Real-time order tracking
   - Return/refund workflow

4. **Multi-Instance Ready**:
   - SSE → Redis pub/sub
   - File uploads → S3/Blob Storage
   - Session → Redis or DB

5. **Monitoring & Observability**:
   - Centralized logging (ELK stack)
   - Metrics (Prometheus + Grafana)
   - Distributed tracing (Jaeger)

---

## 8. Code Quality & Standards

### Naming Conventions
- Classes: PascalCase (e.g., `BookService`, `OrderRepository`)
- Methods: camelCase (e.g., `findApprovedBooks`, `createOrder`)
- Constants: UPPER_SNAKE_CASE
- Database tables: snake_case
- Database columns: snake_case

### Project Structure
```
src/
├── main/
│   ├── java/com/example/bookstore/
│   │   ├── config/           (Spring configuration)
│   │   ├── controller/        (80+ controllers)
│   │   ├── service/           (Business logic)
│   │   ├── repository/        (Data access)
│   │   ├── model/             (JPA entities + enums)
│   │   ├── dto/               (Request/response)
│   │   ├── converter/         (Entity ↔ DTO mapping)
│   │   ├── security/          (JWT, auth filters)
│   │   ├── sse/               (Real-time notifications)
│   │   ├── distributed/       (Distributed coordination)
│   │   ├── lifecycle/         (App startup/shutdown)
│   │   └── util/              (Helpers, sanitizers)
│   └── resources/
│       ├── application.properties
│       ├── db/migration/      (Flyway V1-V15)
│       ├── templates/         (Thymeleaf views)
│       └── static/            (CSS, JS, images)
└── test/
    ├── java/                  (Unit tests)
    └── resources/             (Test configs, fixtures)
```

### Testing Strategy
- Unit tests for services (mocking repositories)
- Integration tests for repositories (H2 in-memory DB)
- Controller tests (MockMvc)
- Coverage target: 60%+

---

## 9. Deployment & Infrastructure

### Local Development
```bash
# 1. Prerequisites
java -version        # Java 17+
mvn -version         # Maven 3.8+

# 2. Database setup
# - Create SQL Server instance
# - Update application.properties with connection

# 3. Run application
mvn clean spring-boot:run

# 4. Access
# - Web: http://localhost:8080
# - API: http://localhost:8080/api
# - Swagger: http://localhost:8080/swagger-ui.html
```

### Docker Deployment
```dockerfile
FROM openjdk:17-slim
WORKDIR /app
COPY target/bookstore.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

### Kubernetes Readiness
- Health checks for liveness/readiness probes
- Graceful shutdown component
- Distributed lock for job coordination

---

## 10. Troubleshooting Guide

### Common Issues

**Q: OTP not sending**  
A: Check `application.properties` SMTP settings. Default dev mode uses mock.

**Q: Recommendation cache empty**  
A: Check `RecommendationJob` scheduled job logs. Job must complete successfully at least once.

**Q: SSE connection drops in multi-instance**  
A: Expected behavior. SSE is in-memory per instance. Fix: use sticky sessions or Redis pub/sub.

**Q: Admin book approval returns 403**  
A: Ensure the caller has `ADMIN` role and a valid JWT principal. Do not hardcode roles in the filter.

**Q: N+1 query performance issue**  
A: Use fetch joins in repository queries, add paging, review lazy-loaded associations.

---

**Last Updated**: May 16, 2026  
**Version**: 1.5 (Post-Recommendation Refactor)  
**Next Review**: August 2026

---

## Recent Merge: d769cfe (2026-05-17)

Overview: The `Mar1cc` merge standardizes JWT principal extraction and updates security-related components across the codebase. This affects authentication, method-level authorization, and several admin controllers.

Architectural impact:
- `JwtAuthenticationFilter` and `SecurityConfig` updated to consistently populate a typed authenticated principal (`JwtAuthenticatedPrincipal`) with `userId`, `roles`, and `sellerId` claims.
- Controller and service layers now rely on the authenticated principal for identity/ownership checks; legacy request-attribute patterns were removed.
- Ensure any integration points (API clients, authentication service, tokens) include the expected claims to avoid authorization failures.

Rollout recommendations:
- Deploy authentication provider and application code in the same release window.
- Run the security regression suite and perform smoke tests on admin and principal-based endpoints.
- Monitor `401`/`403` metrics and audit logs for denials immediately after deployment.

