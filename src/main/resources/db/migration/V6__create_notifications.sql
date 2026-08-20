IF OBJECT_ID('notifications', 'U') IS NULL
BEGIN
CREATE TABLE notifications (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type NVARCHAR(50) NOT NULL,
    title NVARCHAR(255) NOT NULL,
    message NVARCHAR(MAX) NULL,
    payload_json NVARCHAR(MAX) NULL,
    is_read BIT NOT NULL CONSTRAINT DF_notifications_is_read DEFAULT 0,
    priority NVARCHAR(20) NOT NULL CONSTRAINT DF_notifications_priority DEFAULT 'NORMAL',
    created_at DATETIME2 NOT NULL CONSTRAINT DF_notifications_created_at DEFAULT SYSUTCDATETIME(),
    read_at DATETIME2 NULL,

    CONSTRAINT FK_notifications_users FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
);
END;

IF OBJECT_ID('notifications', 'U') IS NOT NULL
    AND NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = 'IX_notifications_user_is_read_created_at'
          AND object_id = OBJECT_ID('notifications')
    )
BEGIN
CREATE INDEX IX_notifications_user_is_read_created_at
    ON notifications (user_id, is_read, created_at DESC);
END;

IF OBJECT_ID('notifications', 'U') IS NOT NULL
    AND NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = 'IX_notifications_created_at'
          AND object_id = OBJECT_ID('notifications')
    )
BEGIN
CREATE INDEX IX_notifications_created_at
    ON notifications (created_at DESC);
END;
