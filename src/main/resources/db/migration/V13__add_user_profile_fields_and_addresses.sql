-- Add profile fields to users table
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'first_name')
BEGIN
    ALTER TABLE users ADD first_name NVARCHAR(100) NULL;
END;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'last_name')
BEGIN
    ALTER TABLE users ADD last_name NVARCHAR(100) NULL;
END;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'email')
BEGIN
    ALTER TABLE users ADD email NVARCHAR(255) NULL;
END;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'phone')
BEGIN
    ALTER TABLE users ADD phone NVARCHAR(20) NULL;
END;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'date_of_birth')
BEGIN
    ALTER TABLE users ADD date_of_birth DATE NULL;
END;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'bio')
BEGIN
    ALTER TABLE users ADD bio NVARCHAR(500) NULL;
END;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'gender')
BEGIN
    ALTER TABLE users ADD gender NVARCHAR(20) NULL;
END;

-- Create user addresses table
IF OBJECT_ID('user_addresses', 'U') IS NULL
BEGIN
    CREATE TABLE user_addresses (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id BIGINT NOT NULL,
        address_type NVARCHAR(50) NOT NULL,
        recipient_name NVARCHAR(100) NOT NULL,
        recipient_phone NVARCHAR(20) NOT NULL,
        address_line NVARCHAR(500) NOT NULL,
        ward NVARCHAR(100) NULL,
        district NVARCHAR(100) NOT NULL,
        province NVARCHAR(100) NOT NULL,
        postal_code NVARCHAR(20) NULL,
        is_default BIT DEFAULT 0,
        created_at DATETIME DEFAULT GETDATE(),
        updated_at DATETIME DEFAULT GETDATE(),
        CONSTRAINT FK_user_addresses_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );
END;

-- Create user security events table for tracking password changes and security activities
IF OBJECT_ID('user_security_events', 'U') IS NULL
BEGIN
    CREATE TABLE user_security_events (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id BIGINT NOT NULL,
        event_type NVARCHAR(50) NOT NULL, -- PASSWORD_CHANGED, EMAIL_CHANGED, etc.
        event_description NVARCHAR(500) NULL,
        ip_address NVARCHAR(50) NULL,
        user_agent NVARCHAR(500) NULL,
        created_at DATETIME DEFAULT GETDATE(),
        CONSTRAINT FK_security_events_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );
END;

-- Create indexes for better query performance
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name='IX_user_addresses_user_id' AND object_id = OBJECT_ID('user_addresses'))
BEGIN
    CREATE INDEX IX_user_addresses_user_id ON user_addresses(user_id);
END;

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name='IX_user_security_events_user_id' AND object_id = OBJECT_ID('user_security_events'))
BEGIN
    CREATE INDEX IX_user_security_events_user_id ON user_security_events(user_id);
END;
