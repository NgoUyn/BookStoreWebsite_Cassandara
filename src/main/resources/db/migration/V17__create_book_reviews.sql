-- V17: Create Book Reviews table
-- Purpose: Allow buyers to rate and review books they have purchased.

CREATE TABLE book_reviews (
                              id BIGINT IDENTITY(1,1) PRIMARY KEY,
                              book_id BIGINT NOT NULL,
                              user_id BIGINT NOT NULL,
                              rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
                              comment NVARCHAR(MAX) NULL,
                              created_at DATETIME NOT NULL DEFAULT GETDATE(),
                              is_hidden BIT NOT NULL DEFAULT 0,

                              CONSTRAINT FK_book_reviews_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
                              CONSTRAINT FK_book_reviews_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- INDEXES: Optimized for common query patterns
-- 1. Get all visible reviews for a specific book (ordered by date)
CREATE INDEX IX_book_reviews_book_visible ON book_reviews(book_id, is_hidden, created_at DESC);

-- 2. Stats calculation (rating distribution)
CREATE INDEX IX_book_reviews_book_rating ON book_reviews(book_id, rating) WHERE is_hidden = 0;

-- 3. Prevent duplicate reviews (one user per book) - Optional but recommended
-- Let's stick to the plan and see if business logic handles it.
-- Usually, we want to allow only one review per user per book.
-- CREATE UNIQUE INDEX UX_book_reviews_user_book ON book_reviews(user_id, book_id);
