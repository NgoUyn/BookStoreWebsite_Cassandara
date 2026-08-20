-- ============================================================================
-- V15: Create association_rules table for FP-Growth based recommendation
-- ============================================================================
-- Purpose: Store mined association rules from customer purchases
-- - book_id_a: Source book (when customer buys this, they likely buy book_id_b)
-- - book_id_b: Target book (frequently bought together with book_id_a)
-- - support: % of all transactions containing both items
-- - confidence: % of transactions with book_id_a that also contain book_id_b
-- - lift: Ratio of observed vs expected frequency (>1 means strong correlation)
-- ============================================================================

CREATE TABLE association_rules (
    rule_id BIGINT PRIMARY KEY IDENTITY(1,1),
    book_id_a BIGINT NOT NULL,
    book_id_b BIGINT NOT NULL,
    support DECIMAL(5, 4) NOT NULL,        -- 0.0000 to 1.0000 (0% to 100%)
    confidence DECIMAL(5, 4) NOT NULL,     -- 0.0000 to 1.0000 (0% to 100%)
    lift DECIMAL(10, 4) NOT NULL,          -- 0.0000 to 9999.9999
    updated_at DATETIME2 DEFAULT GETDATE(),
    
    -- Constraints
    CONSTRAINT FK_AssociationRules_BookA FOREIGN KEY (book_id_a) REFERENCES books(id),
    CONSTRAINT FK_AssociationRules_BookB FOREIGN KEY (book_id_b) REFERENCES books(id),
    CONSTRAINT CK_BookA_NotEqual_BookB CHECK (book_id_a <> book_id_b),
    CONSTRAINT CK_Support_Range CHECK (support >= 0 AND support <= 1),
    CONSTRAINT CK_Confidence_Range CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT CK_Lift_Range CHECK (lift > 0)
);

-- ============================================================================
-- Composite Index: For efficient retrieval of recommendations by book_id_a
-- Sorted by confidence DESC, lift DESC to return strongest rules first
-- INCLUDE (book_id_b) allows covering queries without table lookup
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_AssociationRules_BookA_Confidence_Lift
ON association_rules (book_id_a, confidence DESC, lift DESC)
INCLUDE (book_id_b);

-- ============================================================================
-- Secondary Index: For reverse lookups (if book Y is recommended, find all X)
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_AssociationRules_BookB
ON association_rules (book_id_b);

-- ============================================================================
-- Index for cleanup operations (finding old/low-confidence rules)
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_AssociationRules_UpdatedAt_Confidence
ON association_rules (updated_at DESC, confidence DESC);

-- ============================================================================
-- Seed comment: Table ready for Flyway V15
-- Initial data will be populated by RecommendationJob after deployment
-- ============================================================================
