-- Multi-vendor schema initialization for SQL Server

-- 1. TẠO BẢNG USERS
IF OBJECT_ID('users', 'U') IS NULL
BEGIN
CREATE TABLE users (
                       id BIGINT IDENTITY(1,1) PRIMARY KEY,
                       username NVARCHAR(255) NOT NULL UNIQUE,
                       password_hash NVARCHAR(255) NOT NULL,
                       role VARCHAR(20) NOT NULL CONSTRAINT DF_users_role DEFAULT 'BUYER',
                       shop_name NVARCHAR(255) NULL,
                       shop_address NVARCHAR(500) NULL,
                       avatar_url NVARCHAR(MAX) NULL
);
END;

-- 2. TẠO BẢNG CATEGORY
IF OBJECT_ID('category', 'U') IS NULL
BEGIN
CREATE TABLE category (
                          id BIGINT IDENTITY(1,1) PRIMARY KEY,
                          name NVARCHAR(255) NOT NULL UNIQUE,
                          description NVARCHAR(MAX) NULL -- <-- BẠN THÊM DÒNG NÀY VÀO ĐÂY
);
END;

-- TẠO BẢNG USER_FAVORITE_CATEGORIES
IF OBJECT_ID('user_favorite_categories', 'U') IS NULL
BEGIN
CREATE TABLE user_favorite_categories (
                                          user_id BIGINT NOT NULL,
                                          category_id BIGINT NOT NULL,
                                          PRIMARY KEY (user_id, category_id),
                                          CONSTRAINT FK_user_fav_cat_user FOREIGN KEY (user_id) REFERENCES users(id),
                                          CONSTRAINT FK_user_fav_cat_category FOREIGN KEY (category_id) REFERENCES category(id)
);
END;
-- 3. TẠO BẢNG BOOKS (Đã bao gồm cột seller_id và approval_status)
IF OBJECT_ID('books', 'U') IS NULL
BEGIN
CREATE TABLE books (
                       id BIGINT IDENTITY(1,1) PRIMARY KEY,
                       title NVARCHAR(500) NOT NULL,
                       author NVARCHAR(255) NOT NULL,
                       description NVARCHAR(MAX) NULL,
                       price FLOAT NULL,
                       stock_quantity INT NULL,
                       image_url NVARCHAR(500) NULL,
                       publisher NVARCHAR(255) NULL,
                       publish_year NVARCHAR(50) NULL,
                       category_id BIGINT NULL,
                       seller_id BIGINT NOT NULL,
                       approval_status VARCHAR(20) NOT NULL CONSTRAINT DF_books_approval_status DEFAULT 'PENDING',

    -- Ràng buộc khóa ngoại
                       CONSTRAINT FK_books_seller_id_users FOREIGN KEY (seller_id) REFERENCES users(id),
                       CONSTRAINT FK_books_category_id FOREIGN KEY (category_id) REFERENCES category(id)
);
END;

-- 4. TẠO BẢNG CARTS
IF OBJECT_ID('carts', 'U') IS NULL
BEGIN
CREATE TABLE carts (
                       id BIGINT IDENTITY(1,1) PRIMARY KEY,
                       buyer_id BIGINT NOT NULL,
                       CONSTRAINT UK_carts_buyer UNIQUE (buyer_id),
                       CONSTRAINT FK_carts_buyer_users FOREIGN KEY (buyer_id) REFERENCES users(id)
);
END;

-- 5. TẠO BẢNG CART_ITEMS
IF OBJECT_ID('cart_items', 'U') IS NULL
BEGIN
CREATE TABLE cart_items (
                            id BIGINT IDENTITY(1,1) PRIMARY KEY,
                            cart_id BIGINT NOT NULL,
                            book_id BIGINT NOT NULL,
                            quantity INT NOT NULL,
                            CONSTRAINT FK_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id),
                            CONSTRAINT FK_cart_items_book FOREIGN KEY (book_id) REFERENCES books(id)
);
END;

-- 6. TẠO BẢNG ORDERS MASTER
IF OBJECT_ID('orders_master', 'U') IS NULL
BEGIN
CREATE TABLE orders_master (
                               id BIGINT IDENTITY(1,1) PRIMARY KEY,
                               buyer_id BIGINT NOT NULL,
                               total_amount FLOAT NOT NULL,
                               shipping_address NVARCHAR(500) NOT NULL,
                               created_at DATETIME2 NOT NULL,
                               CONSTRAINT FK_orders_master_buyer FOREIGN KEY (buyer_id) REFERENCES users(id)
);
END;

-- 7. TẠO BẢNG SUB_ORDERS
IF OBJECT_ID('sub_orders', 'U') IS NULL
BEGIN
CREATE TABLE sub_orders (
                            id BIGINT IDENTITY(1,1) PRIMARY KEY,
                            order_id BIGINT NOT NULL,
                            seller_id BIGINT NOT NULL,
                            status VARCHAR(30) NOT NULL CONSTRAINT DF_sub_orders_status DEFAULT 'PENDING_PAYMENT',
                            sub_total FLOAT NOT NULL,
                            CONSTRAINT FK_sub_orders_order FOREIGN KEY (order_id) REFERENCES orders_master(id),
                            CONSTRAINT FK_sub_orders_seller FOREIGN KEY (seller_id) REFERENCES users(id)
);
END;

-- 8. TẠO BẢNG ORDER_ITEMS
IF OBJECT_ID('order_items', 'U') IS NULL
BEGIN
CREATE TABLE order_items (
                             id BIGINT IDENTITY(1,1) PRIMARY KEY,
                             sub_order_id BIGINT NOT NULL,
                             book_id BIGINT NOT NULL,
                             unit_price FLOAT NOT NULL,
                             quantity INT NOT NULL,
                             CONSTRAINT FK_order_items_sub_order FOREIGN KEY (sub_order_id) REFERENCES sub_orders(id),
                             CONSTRAINT FK_order_items_book FOREIGN KEY (book_id) REFERENCES books(id)
);
END;