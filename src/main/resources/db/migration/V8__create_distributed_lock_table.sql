-- ============================================================================
-- V8: Create distributed_lock table for queue worker coordination
-- ============================================================================
-- Purpose:
--   Ensure only ONE instance of NotificationDeliveryQueue processes notifications
--   Prevent duplicate sends when running 3+ replicas in Docker Compose
--
-- How it works:
--   1. Each replica attempts to acquire a lock at startup
--   2. Lock holder (lock_holder_id = instance UUID) has exclusive rights
--   3. If holder crashes, lock expires (lock_expires_at check)
--   4. Other replicas detect expiry and acquire lock
--   5. Allows failover without manual intervention
--
-- Lock TTL:
--   - Normal: 30 seconds (refresh every 15 seconds)
--   - If holder doesn't refresh within 30s, lock is considered stale
--   - Other replicas can force-acquire after expiry
-- ============================================================================

CREATE TABLE distributed_lock (
    -- Lock identifier (only 1 row: 'NOTIFICATION_QUEUE_WORKER')
    lock_name NVARCHAR(100) PRIMARY KEY,
    
    -- Hostname or instance UUID of current lock holder
    -- Example: 'bookom-app-1' or UUID
    lock_holder_id NVARCHAR(255) NOT NULL,
    
    -- When the lock was acquired
    acquired_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    
    -- When the lock expires (if holder crashes)
    -- Refresh before this time to keep lock
    lock_expires_at DATETIME2 NOT NULL,
    
    -- Last heartbeat from lock holder (for monitoring)
    last_heartbeat_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);

-- ============================================================================
-- Add unique index to ensure only one lock holder per lock_name
-- ============================================================================
CREATE UNIQUE INDEX UX_distributed_lock_name 
    ON distributed_lock (lock_name);

-- ============================================================================
-- Initialize the lock record (if not exists)
-- ============================================================================
-- Initial owner: 'UNOWNED' (any instance can take it at startup)
-- Expires immediately so first instance to start wins
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM distributed_lock WHERE lock_name = 'NOTIFICATION_QUEUE_WORKER')
BEGIN
    INSERT INTO distributed_lock (
        lock_name,
        lock_holder_id,
        acquired_at,
        lock_expires_at,
        last_heartbeat_at
    )
    VALUES (
        'NOTIFICATION_QUEUE_WORKER',
        'UNOWNED',
        SYSUTCDATETIME(),
        SYSUTCDATETIME(),
        SYSUTCDATETIME()
    );
END;

