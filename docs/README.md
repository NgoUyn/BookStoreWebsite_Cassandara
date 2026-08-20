# 📚 BOOKOM - Multi-Vendor E-Commerce Bookstore Platform

**Status**: ✅ Phase 1 Complete | 🚀 Production Ready | 📊 Active Development

**Build**: Spring Boot 3.2.4 | **Runtime**: Java 17 | **Database**: SQL Server | **Frontend**: Thymeleaf + Tailwind CSS

---

## 🎯 What is BOOKOM?

A modern multi-vendor e-commerce platform for books with:
- **👥 Multi-Role Support**: Buyers, Sellers, Admins
- **🛍️ Smart Shopping**: Real-time cart, one-click checkout, order tracking
- **🏪 Seller Marketplace**: Manage shops, inventory, fulfillment, earnings
- **📢 Smart Recommendations**: FP-Growth association rules + Cosine similarity
- **🔔 Real-Time Notifications**: SSE-based instant updates with exponential backoff retry
- **🔐 Secure Auth**: JWT tokens + OTP verification + method-level access control
- **🌐 Distributed Ready**: DB-backed distributed locks, cluster-safe architecture

---

## 📖 Documentation Structure

### 🏗️ [TECHNICAL_ARCHITECTURE.md](./TECHNICAL_ARCHITECTURE.md)
Complete technical design covering:
- System architecture (5-layer stack)
- Core modules & components (12+ major features)
- Database schema with all entities
- API endpoints reference
- Security model (JWT, OTP, RBAC)
- Real-time infrastructure (SSE, notification queue)
- Production concerns & known issues

### 📋 [OPERATIONS_GUIDE.md](./OPERATIONS_GUIDE.md)
Operations & deployment guide covering:
- Local development setup
- Docker & containerization
- SQL Server setup instructions
- Database migrations (Flyway)
- Health checks & monitoring
- Deployment checklist
- Troubleshooting guide

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- SQL Server 2019+
- Docker (optional, for containerized setup)

### Local Development
```bash
# 1. Clone repository
git clone <repo-url>
cd NgPhucs2375-DA_BookStore

# 2. Setup database
# - Create SQL Server instance
# - Update application.properties with connection details
# - Flyway migrations run automatically on startup

# 3. Build & Run
mvn clean spring-boot:run

# 4. Access application
# - Web UI: http://localhost:8080
# - API: http://localhost:8080/api
# - Swagger UI: http://localhost:8080/swagger-ui.html
```

For detailed setup, see [OPERATIONS_GUIDE.md](./OPERATIONS_GUIDE.md#local-development-setup)

---

## 📊 Project Statistics

| Metric | Value |
|--------|-------|
| **Total Classes** | 120+ |
| **Total Repositories** | 15+ |
| **Scheduled Jobs** | 5 (auth seed, recommendations, heartbeat, queue worker, etc.) |
| **REST Endpoints** | 80+ |
| **Database Tables** | 15 |
| **Flyway Migrations** | 15 |
| **Test Coverage** | ~40% (growing) |

---

## 🎯 Key Features & Status

### ✅ Implemented & Production Ready

| Feature | Module | Status | Notes |
|---------|--------|--------|-------|
| Multi-role Authentication | `AuthController` | ✅ Complete | JWT + OTP |
| Book Catalog & Search | `BookController` | ✅ Complete | 8 custom queries |
| Shopping Cart | `CartController` | ✅ Complete | Stock validation |
| Multi-vendor Orders | `OrderController` | ✅ Complete | Sub-order per seller |
| Seller Marketplace | `SellerShopController` | ✅ Complete | Approval blocked for seller self-approval |
| Real-time Notifications | `NotificationController` | ✅ Complete | SSE + exponential backoff |
| Buyer Profile & Addresses | `BuyerProfileController` | ✅ Complete | JWT principal-based identity |
| Wishlists | `WishlistController` | ✅ Complete | Simple CRUD |
| Recommendations | `RecommendationService` | ✅ Refactored | DB-backed FP-Growth |
| Admin Controls | `AdminBookController` | ✅ Complete | Secured by Spring Security annotations |
| Health Monitoring | `HealthCheckController` | ✅ Complete | 6 endpoints |

### 🔄 Refactored (Latest)

- **Recommendation Engine**: Migrated from in-memory cache → Database-backed association rules
  - Sliding window queries (30 days) to prevent OOM
  - Bulk insert via JdbcTemplate
  - CosineSimilarity for "similar books"
  - Fully cluster-safe

### 🔄 Security Hardening (May 2026)

- **JWT Principal**: Tokens now carry `userId`, `roles`, and `sellerId`
- **Principal-Based Controllers**: Identity now comes from the authenticated principal, not `X-User-Id` or request attributes
- **Authorization Layering**: `@PreAuthorize` and `CustomPermissionEvaluator` now handle route-level and resource-level checks
- **Legacy Cleanup**: Transitional `CURRENT_*` request attributes were removed from controllers and the JWT filter

---

## ⚠️ Known Issues & Recommendations

### Security Issues (Priority: HIGH)
1. **Audit Logging Gap**: Security failures and denials still need a centralized audit trail

### Performance Issues (Priority: MEDIUM)
1. **File Upload to Local Filesystem**: `BookService.uploadAndVerifyCoverImage()` - not production-safe for multi-instance
2. **SSE In-Memory**: Notification connections stored in-memory per instance - needs sticky sessions or pub/sub
3. **Entity Lazy Loading**: Many endpoints return JPA entities directly → N+1 query risk

### Architecture Issues (Priority: LOW)
1. **API Response Leakage**: Controllers return entities instead of DTOs
2. **Tight Coupling**: Service layer tightly coupled to repository interfaces

---

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────┐
│         Browser / Mobile App            │
├─────────────────────────────────────────┤
│    Spring MVC Controller Layer (80+)    │
├─────────────────────────────────────────┤
│    Service Layer (Biz Logic, 12+)       │
├─────────────────────────────────────────┤
│    Spring Data JPA Repository (15+)     │
├─────────────────────────────────────────┤
│    JPA Entity Layer (15 entities)        │
├─────────────────────────────────────────┤
│    SQL Server Database (v1903)           │
└─────────────────────────────────────────┘

Infrastructure:
- JWT Authentication Filter
- Method Security + `CustomPermissionEvaluator`
- OAuth/RBAC (User roles)
- Distributed Lock Service (for background jobs)
- SSE Notification Pipeline
- Scheduled Background Jobs (5)
- Flyway Schema Migrations
```

---

## 📚 Module Dependencies

```
Authentication
├── AuthController → AuthService → AuthOtpService
└── JwtAuthenticationFilter

Catalog
├── BookController → BookService → BookRepository
├── CategoryController
└── AdminBookController → AdminService

Shopping
├── CartController → CartService → CartRepository
└── OrderController → OrderService → OrderRepository

Sellers
├── SellerShopController → SellerShopService
└── PanelController (seller dashboard)

Buyers
├── BuyerProfileController → BuyerProfileService
├── WishlistController
└── UserAddressService

Recommendations
├── RecommendationService → AssociationRuleRepository
├── RecommendationJob (scheduled)
├── PairMiningAlgorithm (FP-Growth)
└── CosineSimilarityAlgorithm

Notifications
├── NotificationController → NotificationService
├── NotificationDeliveryQueue (background job, 1s interval)
├── NotificationSseService (SSE emitter management)
├── HeartbeatService (15s refresh)
└── DistributedLockService (DB-backed locks)

Admin
├── HealthCheckController (6 endpoints)
└── AdminBookController (book approval)
```

---

## 🔍 Recent Changes (May 2026)

### Recommendation Engine Refactor ✅
**Status**: COMPLETE

**What Changed**:
- ❌ Removed: In-memory cache (`RecommendationCacheHolder`, `RecommendationCache`)
- ✅ Added: Database-backed `association_rules` table (V15 migration)
- ✅ Added: `AssociationRuleRepository` with optimized queries
- ✅ Updated: `RecommendationJob` uses sliding window (30 days) + bulk insert
- ✅ Updated: `RecommendationService` queries DB instead of cache
- ✅ Integrated: `CosineSimilarityAlgorithm` for similar books

**Why**:
- Eliminates OOM risk from loading all-time order data
- Enables cluster-safe multi-instance deployment
- Provides real metrics (confidence, lift) for recommendations
- Reduces computation by 10x with sliding window

**Impact**: 
- ✅ Smaller memory footprint
- ✅ Cluster-ready
- ✅ Production-safe

---

## 🤝 Contributing

See [TECHNICAL_ARCHITECTURE.md](./TECHNICAL_ARCHITECTURE.md) for code standards and design patterns.

---

## 📞 Support & Questions

For detailed documentation:
- **Technical**: See [TECHNICAL_ARCHITECTURE.md](./TECHNICAL_ARCHITECTURE.md)
- **Operations**: See [OPERATIONS_GUIDE.md](./OPERATIONS_GUIDE.md)
- **API Reference**: Swagger at http://localhost:8080/swagger-ui.html

---

**Last Updated**: May 16, 2026  
**Maintainers**: Development Team

---

## Recent Merge: d769cfe (2026-05-17)

Summary: Merged branch `Mar1cc` — standardised JWT principal handling and related security updates; documentation refreshed.

Key changes included in the merge:
- Security: refactor of `JwtAuthenticationFilter` and `SecurityConfig` to extract a consistent authenticated principal (claims now include `userId`, `roles`, and `sellerId`).
- Controllers: updates to admin and principal-based controllers (notably `AdminBookController`) to rely on the authenticated principal instead of legacy request attributes.
- Docs: supporting documentation files under `docs/` and `PROJECT_REQUIREMENTS_ANALYSIS.md` updated to reflect the new principal-based identity model.

Impact & Notes:
- Deploy these security changes together (JWT tokens and server) to avoid `401/403` spikes.
- Verify tokens used by external clients include the required claims before rolling out to production.

If you want, I can add a short migration checklist to the `OPERATIONS_GUIDE.md` and `TECHNICAL_ARCHITECTURE.md` with rollout steps and smoke tests.
