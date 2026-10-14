-- ==============================================================================
-- 006_PRODUCTION_INTEGRATION.SQL
-- Completes Database Schema: Activity Events, User Presence, Backup Records,
-- Password Resets, and Audit Log Hardening.
-- ==============================================================================

-- 1. ACTIVITY EVENTS (Privacy-Conscious Audit Trail)
-- Strictly stores event metadata WITHOUT sensitive financial amounts or memos.
CREATE TABLE IF NOT EXISTS activity_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'SESSION_REFRESH',
        'ACCOUNT_REGISTERED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_SUCCEEDED',
        'TRANSACTION_CREATED', 'TRANSACTION_UPDATED', 'TRANSACTION_DELETED',
        'TRIP_CREATED', 'TRIP_UPDATED', 'TRIP_ARCHIVED',
        'BACKUP_STARTED', 'BACKUP_SUCCEEDED', 'BACKUP_FAILED',
        'SYNC_SUCCEEDED', 'SYNC_FAILED'
    )),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    result VARCHAR(20) NOT NULL DEFAULT 'success' CHECK (result IN ('success', 'failure', 'pending')),
    request_id VARCHAR(100),
    metadata JSONB DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_activity_user_time ON activity_events(user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_event_type ON activity_events(event_type, occurred_at DESC);

-- 2. USER PRESENCE (Heartbeat & Last Seen Tracking)
CREATE TABLE IF NOT EXISTS user_presence (
    user_id UUID PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    client_platform VARCHAR(30) DEFAULT 'android',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_presence_heartbeat ON user_presence(last_heartbeat_at DESC);

-- 3. BACKUP RECORDS (Real Operation Tracking)
CREATE TABLE IF NOT EXISTS backup_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    backup_type VARCHAR(30) NOT NULL DEFAULT 'json_export' CHECK (backup_type IN ('json_export', 'cloud_snapshot', 'full_database')),
    status VARCHAR(20) NOT NULL DEFAULT 'started' CHECK (status IN ('started', 'completed', 'failed')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    record_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(50),
    storage_reference TEXT,
    checksum VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_backups_user ON backup_records(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_backups_status ON backup_records(status);

-- 4. PASSWORD RESETS (Controlled OTP & Token Storage)
CREATE TABLE IF NOT EXISTS password_resets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    otp_hash VARCHAR(128) NOT NULL,
    reset_token VARCHAR(128),
    expires_at TIMESTAMPTZ NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_resets_email_active ON password_resets(email, is_used, expires_at);

-- 5. PROFILE EXTENSIONS
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS terms_accepted BOOLEAN DEFAULT TRUE;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS terms_version VARCHAR(20) DEFAULT '2026.1';
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;
