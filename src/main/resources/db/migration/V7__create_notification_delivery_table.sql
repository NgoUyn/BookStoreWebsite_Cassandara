-- ============================================================================
-- V7: Create notification_delivery table for delivery tracking & audit trail
-- ============================================================================
-- Purpose:
--   Track each delivery attempt (SSE, email, SMS) for each notification
--   Enable retry logic to be DB-backed instead of in-memory
--   Support multi-instance deployments (shared DB queue)
--   Provide audit trail: when sent, success/fail, error reason
--
-- Design Notes:
--   - One row per notification-user-channel combination
--   - status: PENDING (not sent yet), SENT (success), FAILED (drop after retries), etc.
--   - attempt_count tracks number of retry attempts
--   - next_retry_at guides the queue worker: poll for rows where next_retry_at <= NOW
--   - last_error stores exception message for debugging
-- ============================================================================

CREATE TABLE notification_delivery (
    -- Primary key
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    
    -- Foreign key to parent notification
    notification_id BIGINT NOT NULL,
    
    -- Delivery channel: 'SSE', 'EMAIL', 'PUSH', 'SMS' (for future extensibility)
    channel NVARCHAR(50) NOT NULL,
    
    -- Status enum: PENDING, SENT, FAILED, DROPPED
    -- PENDING   = awaiting first send attempt or retry
    -- SENT      = delivered successfully
    -- FAILED    = failed after max retries, manually reviewable
    -- DROPPED   = exceeded retry limit, abandoned
    status NVARCHAR(20) NOT NULL,
    
    -- Timestamp when notification was actually sent successfully (NULL if not yet sent)
    sent_at DATETIME2 NULL,
    
    -- Error message from last failed attempt (for debugging)
    last_error NVARCHAR(500) NULL,
    
    -- How many times we've tried to send this (starts at 0, incremented on each retry)
    attempt_count INT NOT NULL DEFAULT 0,
    
    -- When to try again (NULL if sent or dropped)
    -- Queue worker polls: SELECT * WHERE status='PENDING' AND next_retry_at <= SYSUTCDATETIME()
    next_retry_at DATETIME2 NULL,
    
    -- Audit: when this record was created (same as or shortly after notification.created_at)
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    
    -- Audit: when this record was last updated (tracking attempt history)
    updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    
    -- Foreign key constraint: cascade delete when notification is deleted
    CONSTRAINT FK_notification_delivery_notification 
        FOREIGN KEY (notification_id) 
        REFERENCES notifications(id) 
        ON DELETE CASCADE
);

-- ============================================================================
-- Index 1: For queue worker polling
-- Purpose: Find pending deliveries that are ready to retry
-- Usage: SELECT * FROM notification_delivery 
--        WHERE status='PENDING' AND next_retry_at <= @now 
--        ORDER BY next_retry_at ASC 
--        LIMIT 100
-- ============================================================================
CREATE INDEX IX_notification_delivery_pending_retry
    ON notification_delivery (status, next_retry_at)
    WHERE status = 'PENDING';

-- ============================================================================
-- Index 2: For audit & debugging queries
-- Purpose: Find all delivery attempts for a notification
-- Usage: SELECT * FROM notification_delivery 
--        WHERE notification_id = ? 
--        ORDER BY created_at DESC
-- ============================================================================
CREATE INDEX IX_notification_delivery_by_notification
    ON notification_delivery (notification_id, created_at DESC);

-- ============================================================================
-- Index 3: For failed delivery investigation
-- Purpose: Find delivery failures in a time window
-- Usage: SELECT * FROM notification_delivery 
--        WHERE status='FAILED' AND created_at >= @start_date 
--        ORDER BY created_at DESC
-- ============================================================================
CREATE INDEX IX_notification_delivery_failed
    ON notification_delivery (status, created_at DESC)
    WHERE status = 'FAILED';

-- ============================================================================
-- Index 4: For per-channel monitoring
-- Purpose: Monitor specific channel health (e.g., all SSE deliveries)
-- Usage: SELECT status, COUNT(*) FROM notification_delivery 
--        WHERE channel = 'SSE' AND created_at >= DATEADD(hour, -1, SYSUTCDATETIME())
--        GROUP BY status
-- ============================================================================
CREATE INDEX IX_notification_delivery_channel
    ON notification_delivery (channel, created_at DESC, status);
