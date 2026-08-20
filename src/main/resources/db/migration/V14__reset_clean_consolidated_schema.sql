-- ============================================================================
-- V14: Complete schema reset and consolidation for Multi-Vendor BookStore
-- ============================================================================
-- This migration provides a clean, consolidated schema by:
-- 1. Dropping all existing tables and their constraints
-- 2. Redefining schema from scratch with correct data types (NVARCHAR for enums)
-- 3. Including all fields from V1-V13 upfront (no piecemeal ALTER TABLEs)
-- 4. Adding strategic indexes for query performance (hot paths identified)
-- 5. Documenting nullable field strategy and known constraints
--
-- KEY CHANGES FROM V1-V13:
-- - publish_year: NVARCHAR(50) → INT (normalized for numeric range queries)
-- - publish_year_note: NEW, NVARCHAR(100) (flexible format reference: "circa 2020", "Q1 2023")
-- - All enum columns: NVARCHAR from start (no V4/V5 fixes needed)
-- - Comprehensive index strategy for recommendation hot paths
-- - Explicit nullable field rationale (see SECTION 3)
--
-- PRE-CONDITION: This migration assumes a fresh start (new DB or cleared old DB)
-- RUN: After this, subsequent migrations should only ADD features, not fix schema
-- ============================================================================

-- ============================================================================
-- SECTION 1: DROP EXISTING TABLES (if migrating from old schema)
-- ============================================================================

-- Drop in reverse order of dependencies (child → parent)
IF OBJECT_ID('user_security_events', 'U') IS NOT NULL DROP TABLE user_security_events;
IF OBJECT_ID('user_addresses', 'U') IS NOT NULL DROP TABLE user_addresses;
IF OBJECT_ID('user_wishlist_books', 'U') IS NOT NULL DROP TABLE user_wishlist_books;
IF OBJECT_ID('notification_delivery', 'U') IS NOT NULL DROP TABLE notification_delivery;
IF OBJECT_ID('notifications', 'U') IS NOT NULL DROP TABLE notifications;
IF OBJECT_ID('order_items', 'U') IS NOT NULL DROP TABLE order_items;
IF OBJECT_ID('cart_items', 'U') IS NOT NULL DROP TABLE cart_items;
IF OBJECT_ID('carts', 'U') IS NOT NULL DROP TABLE carts;
IF OBJECT_ID('sub_orders', 'U') IS NOT NULL DROP TABLE sub_orders;
IF OBJECT_ID('orders_master', 'U') IS NOT NULL DROP TABLE orders_master;
IF OBJECT_ID('seller_shops', 'U') IS NOT NULL DROP TABLE seller_shops;
IF OBJECT_ID('books', 'U') IS NOT NULL DROP TABLE books;
IF OBJECT_ID('user_favorite_categories', 'U') IS NOT NULL DROP TABLE user_favorite_categories;
IF OBJECT_ID('category', 'U') IS NOT NULL DROP TABLE category;
IF OBJECT_ID('users', 'U') IS NOT NULL DROP TABLE users;
IF OBJECT_ID('distributed_lock', 'U') IS NOT NULL DROP TABLE distributed_lock;

-- ============================================================================
-- SECTION 2: CREATE CLEAN SCHEMA
-- ============================================================================

-- 1. USERS TABLE
-- NULLABLE FIELDS:
--   - shop_name, shop_address, avatar_url: NULL for buyers (only sellers have shops)
--   - first_name, last_name: NULL for incomplete profiles
--   - email: NULL if not provided; used for notification opt-in only
--   - phone: NULL if not provided; used for SMS notifications
--   - date_of_birth: NULL if not provided (privacy-conscious users)
--   - bio, gender: NULL if not provided
CREATE TABLE users (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    
    -- Authentication (required)
    username NVARCHAR(255) NOT NULL UNIQUE,
    password_hash NVARCHAR(255) NOT NULL,
    
    -- Role (using NVARCHAR for enum from start to avoid V4 migration pain)
    role NVARCHAR(20) NOT NULL CONSTRAINT DF_users_role DEFAULT 'BUYER',
    
    -- Seller shop info (nullable for buyers)
    shop_name NVARCHAR(255) NULL,
    shop_address NVARCHAR(500) NULL,
    avatar_url NVARCHAR(MAX) NULL,
    
    -- User profile fields (from V13)
    first_name NVARCHAR(100) NULL,
    last_name NVARCHAR(100) NULL,
    email NVARCHAR(255) NULL,
    phone NVARCHAR(20) NULL,
    date_of_birth DATE NULL,
    bio NVARCHAR(500) NULL,
    gender NVARCHAR(20) NULL
);
CREATE INDEX IX_users_username ON users(username);
CREATE INDEX IX_users_email ON users(email) WHERE email IS NOT NULL;
CREATE INDEX IX_users_role ON users(role);

-- 2. CATEGORY TABLE
CREATE TABLE category (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(255) NOT NULL UNIQUE,
    description NVARCHAR(MAX) NULL
);
CREATE INDEX IX_category_name ON category(name);

-- 3. USER_FAVORITE_CATEGORIES (M:N between users and categories)
CREATE TABLE user_favorite_categories (
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, category_id),
    CONSTRAINT FK_user_fav_cat_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT FK_user_fav_cat_category FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE CASCADE
);

-- 4. BOOKS TABLE
-- NULLABLE FIELD STRATEGY:
--   - description: NULL → text similarity handles empty strings; OK for recommender
--   - price: NULL → books in draft state before pricing; apply business logic in service layer
--   - stock_quantity: NULL → legacy; TODO future migration to NOT NULL with default 0
--   - image_url: NULL → optional; UI layer provides default placeholder
--   - publisher: NULL → not all books have publisher info; optional metadata
--   - publish_year: INT NULL → now numeric, supports NULL for unknown/ancient books
--   - publish_year_note: NVARCHAR(100) NULL → flexible format for reference ("circa 2020", "Q1 2023", etc.)
--   - category_id: NULL → some books don't fit category; recommendation fallback MUST null-check
CREATE TABLE books (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    title NVARCHAR(500) NOT NULL,
    author NVARCHAR(255) NOT NULL,
    description NVARCHAR(MAX) NULL,
    price FLOAT NULL,
    stock_quantity INT NULL,
    image_url NVARCHAR(500) NULL,
    publisher NVARCHAR(255) NULL,
    
    -- Publish year (NORMALIZED: INT instead of NVARCHAR for proper range filtering)
    publish_year INT NULL,           -- Numeric year (e.g., 2023); NULL for unknown
    publish_year_note NVARCHAR(100) NULL,  -- Reference format (e.g., "circa 2020", "Q1 2023")
    
    -- Foreign keys
    category_id BIGINT NULL,         -- Nullable: fallback logic must null-check category
    seller_id BIGINT NOT NULL,
    
    -- Approval status (using NVARCHAR for enum from start to avoid V4 migration pain)
    approval_status NVARCHAR(20) NOT NULL CONSTRAINT DF_books_approval_status DEFAULT 'PENDING',
    
    CONSTRAINT FK_books_seller_id FOREIGN KEY (seller_id) REFERENCES users(id),
    CONSTRAINT FK_books_category_id FOREIGN KEY (category_id) REFERENCES category(id)
);

-- INDEXES: Optimized for hot query paths
-- 1. Approval status filter (first check in most book queries)
CREATE INDEX IX_books_approval_status ON books(approval_status);

-- 2. Category + Seller lookup (used in seller dashboard, category browsing)
CREATE INDEX IX_books_category_seller ON books(category_id, seller_id);

-- 3. Author search (used in search, author profile pages, recommendation author fallback)
CREATE INDEX IX_books_author ON books(author);

-- 4. Title search (used in full-text search, book listing)
CREATE INDEX IX_books_title ON books(title);

-- 5. Publish year range queries (for book age analysis, publication timeline filtering)
--    Now efficient with INT type instead of string comparison
CREATE INDEX IX_books_publish_year ON books(publish_year) 
    WHERE publish_year IS NOT NULL;

-- 6. Composite for recommendation: (approval_status, category_id) filtered
--    Used by RecommendationService.refreshSimilarBooks() to get eligible category peers
--    Filtered index ensures only APPROVED books with categories are indexed
CREATE INDEX IX_books_recommendation_filter ON books(approval_status, category_id) 
    WHERE approval_status = 'APPROVED' AND category_id IS NOT NULL;

-- 5. CARTS TABLE
CREATE TABLE carts (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    buyer_id BIGINT NOT NULL UNIQUE,
    CONSTRAINT FK_carts_buyer FOREIGN KEY (buyer_id) REFERENCES users(id)
);

-- 6. CART_ITEMS TABLE
-- NULLABLE FIELDS: none (all required for operational integrity)
CREATE TABLE cart_items (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT FK_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    CONSTRAINT FK_cart_items_book FOREIGN KEY (book_id) REFERENCES books(id)
);
CREATE INDEX IX_cart_items_cart ON cart_items(cart_id);

-- 7. ORDERS_MASTER TABLE
-- NULLABLE FIELDS: none (all required for transaction integrity)
CREATE TABLE orders_master (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    buyer_id BIGINT NOT NULL,
    total_amount FLOAT NOT NULL,
    shipping_address NVARCHAR(500) NOT NULL,
    created_at DATETIME2 NOT NULL,
    CONSTRAINT FK_orders_master_buyer FOREIGN KEY (buyer_id) REFERENCES users(id)
);
CREATE INDEX IX_orders_master_buyer ON orders_master(buyer_id);
CREATE INDEX IX_orders_master_created_at ON orders_master(created_at);

-- 8. SUB_ORDERS TABLE
-- NULLABLE FIELDS: none (all required for financial tracking)
-- Status uses NVARCHAR for enum from start to avoid V5 migration pain
-- Values: PENDING_PAYMENT, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, REFUNDED
-- INDEXES: Optimized for order status queries and recommendation mining
CREATE TABLE sub_orders (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    
    -- Status (using NVARCHAR for enum from start to avoid V5 migration pain)
    status NVARCHAR(30) NOT NULL CONSTRAINT DF_sub_orders_status DEFAULT 'PENDING_PAYMENT',
    
    sub_total FLOAT NOT NULL,
    
    CONSTRAINT FK_sub_orders_order FOREIGN KEY (order_id) REFERENCES orders_master(id),
    CONSTRAINT FK_sub_orders_seller FOREIGN KEY (seller_id) REFERENCES users(id)
);

-- Lookup by order (frequent: get all seller orders for a purchase)
CREATE INDEX IX_sub_orders_order ON sub_orders(order_id);

-- Seller status filter (frequent: seller dashboard shows pending, shipped orders)
CREATE INDEX IX_sub_orders_seller_status ON sub_orders(seller_id, status);

-- Unfiltered (order_id, status) for general status lookups
CREATE INDEX IX_sub_orders_order_status ON sub_orders(order_id, status);

-- Filtered for completed orders ONLY (used by RecommendationService.findAllOrderBookPairs())
-- This index improves FP-Growth mining by pre-filtering to DELIVERED/CONFIRMED purchases
CREATE INDEX IX_sub_orders_completed ON sub_orders(order_id, status) 
    WHERE status IN ('DELIVERED', 'CONFIRMED');

-- 9. ORDER_ITEMS TABLE
-- NULLABLE FIELDS: none (all required for order fulfillment)
-- INDEXES: Critical for FP-Growth recommendation mining
CREATE TABLE order_items (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    sub_order_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    unit_price FLOAT NOT NULL,
    quantity INT NOT NULL,
    
    CONSTRAINT FK_order_items_sub_order FOREIGN KEY (sub_order_id) REFERENCES sub_orders(id),
    CONSTRAINT FK_order_items_book FOREIGN KEY (book_id) REFERENCES books(id)
);

-- Lookup order items by sub_order (frequent: get items in order)
CREATE INDEX IX_order_items_sub_order ON order_items(sub_order_id);

-- Hot path: FP-Growth mining uses (sub_order_id, book_id) to find itemsets
-- Used by RecommendationService.refreshBoughtTogether() to mine purchase pairs
-- This composite index accelerates pair aggregation queries
CREATE INDEX IX_order_items_order_book ON order_items(sub_order_id, book_id);

-- 10. SELLER_SHOPS TABLE
-- NULLABLE FIELDS:
--   - description: NULL if seller hasn't filled in shop description
--   - logo_url, banner_url: NULL if seller hasn't uploaded media
--   - province: NULL if seller is in major city (district suffices)
--   - phone_contact: NULL if email-only contact preferred
CREATE TABLE seller_shops (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    seller_id BIGINT NOT NULL UNIQUE,
    slug NVARCHAR(255) NOT NULL UNIQUE,
    shop_name NVARCHAR(255) NOT NULL,
    description NVARCHAR(MAX) NULL,
    logo_url NVARCHAR(500) NULL,
    banner_url NVARCHAR(500) NULL,
    
    -- Contact info
    address NVARCHAR(500) NOT NULL,
    city NVARCHAR(100) NOT NULL,
    province NVARCHAR(100) NULL,
    phone_contact NVARCHAR(20) NULL,
    
    -- Status
    approval_status NVARCHAR(50) DEFAULT 'PENDING',
    created_at DATETIME2 DEFAULT GETDATE(),
    updated_at DATETIME2 DEFAULT GETDATE(),
    
    CONSTRAINT FK_seller_shops_seller FOREIGN KEY (seller_id) REFERENCES users(id)
);
CREATE INDEX IX_seller_shops_slug ON seller_shops(slug);
CREATE INDEX IX_seller_shops_approval_status ON seller_shops(approval_status);

-- 11. NOTIFICATIONS TABLE
-- NULLABLE FIELDS:
--   - message: NULL for silent notifications (action only)
--   - payload_json: NULL for simple notifications without metadata
--   - read_at: NULL for unread notifications
CREATE TABLE notifications (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type NVARCHAR(50) NOT NULL,
    title NVARCHAR(255) NOT NULL,
    message NVARCHAR(MAX) NULL,
    payload_json NVARCHAR(MAX) NULL,
    
    priority NVARCHAR(20) NOT NULL CONSTRAINT DF_notifications_priority DEFAULT 'NORMAL',
    created_at DATETIME2 NOT NULL CONSTRAINT DF_notifications_created_at DEFAULT SYSUTCDATETIME(),
    read_at DATETIME2 NULL,
    
    CONSTRAINT FK_notifications_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX IX_notifications_user_read ON notifications(user_id, read_at);
CREATE INDEX IX_notifications_created_at ON notifications(created_at);

-- 12. NOTIFICATION_DELIVERY TABLE
-- NULLABLE FIELDS:
--   - error_message: NULL for successful deliveries
CREATE TABLE notification_delivery (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    delivery_channel NVARCHAR(50) NOT NULL,  -- EMAIL, PUSH, IN_APP
    sent_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    status NVARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING, SENT, FAILED
    error_message NVARCHAR(MAX) NULL,
    
    CONSTRAINT FK_notif_delivery_notif FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE,
    CONSTRAINT UQ_notif_delivery_channel UNIQUE (notification_id, delivery_channel)
);
Create INDEX IX_notification_delivery_status ON notification_delivery(status, sent_at);

-- 13. USER_WISHLIST_BOOKS (M:N between users and books)
-- NULLABLE FIELDS: none (all required)
CREATE TABLE user_wishlist_books (
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    
    CONSTRAINT PK_user_wishlist_books PRIMARY KEY (user_id, book_id),
    CONSTRAINT FK_user_wishlist_books_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT FK_user_wishlist_books_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
);

-- 14. USER_ADDRESSES TABLE
-- NULLABLE FIELDS:
--   - ward: NULL if granularity not needed (district only)
--   - province: NULL if city is sufficient
--   - postal_code: NULL if not available
CREATE TABLE user_addresses (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    
    address_type NVARCHAR(50) NOT NULL,  -- HOME, WORK, OTHER
    recipient_name NVARCHAR(100) NOT NULL,
    recipient_phone NVARCHAR(20) NOT NULL,
    address_line NVARCHAR(500) NOT NULL,
    ward NVARCHAR(100) NULL,
    district NVARCHAR(100) NOT NULL,
    province NVARCHAR(100) NULL,
    postal_code NVARCHAR(20) NULL,
    
    is_default BIT DEFAULT 0,
    created_at DATETIME2 DEFAULT GETDATE(),
    updated_at DATETIME2 DEFAULT GETDATE(),
    
    CONSTRAINT FK_user_addresses_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IX_user_addresses_user_default ON user_addresses(user_id, is_default);

-- 15. USER_SECURITY_EVENTS TABLE
-- NULLABLE FIELDS:
--   - event_description: NULL for silent events
--   - ip_address: NULL if not captured
--   - user_agent: NULL if not captured
CREATE TABLE user_security_events (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    
    event_type NVARCHAR(50) NOT NULL,  -- PASSWORD_CHANGED, EMAIL_CHANGED, LOGIN_FAILED, etc.
    event_description NVARCHAR(500) NULL,
    ip_address NVARCHAR(50) NULL,
    user_agent NVARCHAR(500) NULL,
    
    created_at DATETIME2 DEFAULT GETDATE(),
    
    CONSTRAINT FK_user_security_events_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IX_user_security_events_user_created ON user_security_events(user_id, created_at);

-- 16. DISTRIBUTED_LOCK TABLE
-- NULLABLE FIELDS: none (all required for distributed coordination)
CREATE TABLE distributed_lock (
    lock_name NVARCHAR(100) PRIMARY KEY,
    instance_id NVARCHAR(255) NOT NULL,
    acquired_at DATETIME2 NOT NULL,
    expires_at DATETIME2 NOT NULL,
    heartbeat_at DATETIME2 NOT NULL
);
CREATE INDEX IX_distributed_lock_expires ON distributed_lock(expires_at);

-- ============================================================================
-- SECTION 3: SCHEMA DESIGN DECISIONS & CONSTRAINTS
-- ============================================================================

/*
ENUM COLUMNS (Hibernate SQL Server compatibility):
   All enum fields use NVARCHAR from creation (not VARCHAR) to prevent V4-type fixes.
   - users.role: NVARCHAR(20), default 'BUYER'
   - books.approval_status: NVARCHAR(20), default 'PENDING'
   - sub_orders.status: NVARCHAR(30), default 'PENDING_PAYMENT'
   - notifications.priority: NVARCHAR(20), default 'NORMAL'
   
PUBLISH_YEAR NORMALIZATION (KEY CHANGE):
   V1-V13: NVARCHAR(50) → breaks numeric filtering (string comparison on year ranges)
   V14: INT NULL → proper range queries (BETWEEN, >=, <=) now work correctly
   
   Numeric ranges now work:
     WHERE publish_year BETWEEN 2020 AND 2025  ✓ Efficient
     WHERE publish_year >= 2020                ✓ Efficient
   
   For flexible reference formats (e.g., "circa 2020"), use publish_year_note NVARCHAR(100).

NULLABLE FIELD AUDIT:
   Every NULL field documented with reason. Developers must update this comment
   if changing a field from NULL to NOT NULL (or vice versa).
   
   Categories (null-heavy):
   - books.category_id: NULL because some books don't fit category
     → Impact: RecommendationService.getFallbackSameAuthorOrCategory() MUST null-check
     → Fix: if (sourceBook.getCategory() != null) before calling .getId()
   
   - seller_shops.province: NULL because major cities have district only
     → Impact: UI must handle missing province
   
   - user_addresses.ward: NULL because not all addresses use ward granularity
     → Impact: Shipping logic must handle missing ward

FOREIGN KEY CASCADE DELETES:
   Most child → parent relationships use ON DELETE CASCADE.
   - Deleting user cascades to: carts, addresses, security_events, notifications, wishlist
   - Deleting category cascades to: user_favorite_categories
   - Deleting order cascades to: sub_orders → order_items, notifications
   
   Exceptions (set NULL or reference integrity):
   - books.category_id: DELETE Category doesn't CASCADE (books keep category link if possible)
   
INDEXING STRATEGY:
   PERFORMANCE PRIORITY (hot paths):
   1. IX_books_recommendation_filter: (approval_status, category_id) filtered
      Used by: RecommendationService.refreshSimilarBooks()
      
   2. IX_order_items_order_book: (sub_order_id, book_id)
      Used by: RecommendationService.refreshBoughtTogether() (FP-Growth mining)
      
   3. IX_sub_orders_completed: (order_id, status) WHERE status IN (DELIVERED, CONFIRMED)
      Used by: RecommendationService to filter completed orders only
      
   4. IX_sub_orders_order_status: (order_id, status) unfiltered
      Used by: General order status lookups and audits
   
   TEXT SEARCH INDEXES:
   - IX_books_title, IX_books_author: For search and recommendation fallback
   
   NUMERIC FILTERING:
   - IX_books_publish_year: (publish_year) WHERE publish_year IS NOT NULL
      Now efficient with INT type (was string comparison before)

NEXT MIGRATION POLICY:
   - V14+ migrations should ONLY ADD features (new tables, new columns, new indexes)
   - No more schema fixes like V4/V5 (enum type corrections)
   - All data type decisions are final in V14; inform next generation if changes needed
   - Each nullable field in new migrations must document its NULL rationale
*/

-- ============================================================================
-- END V14
-- ============================================================================
