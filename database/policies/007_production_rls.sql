-- ==============================================================================
-- 007_PRODUCTION_RLS.SQL
-- Row Level Security policies for production tables
-- Enforces zero cross-user visibility and privileged isolation.
-- ==============================================================================

-- 1. Enable RLS on all newly added tables
ALTER TABLE activity_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_presence ENABLE ROW LEVEL SECURITY;
ALTER TABLE backup_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_resets ENABLE ROW LEVEL SECURITY;

-- 2. ACTIVITY EVENTS POLICIES
-- Users can only read their own activity records
CREATE POLICY "activity_events_owner_select" ON activity_events
    FOR SELECT USING (auth.uid() = user_id);

-- Users can only insert activity records for themselves
CREATE POLICY "activity_events_owner_insert" ON activity_events
    FOR INSERT WITH CHECK (auth.uid() = user_id);

-- 3. USER PRESENCE POLICIES
-- Users can read their own presence
CREATE POLICY "user_presence_owner_select" ON user_presence
    FOR SELECT USING (auth.uid() = user_id);

-- Users can upsert their own presence
CREATE POLICY "user_presence_owner_insert" ON user_presence
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "user_presence_owner_update" ON user_presence
    FOR UPDATE USING (auth.uid() = user_id);

-- 4. BACKUP RECORDS POLICIES
-- Users can read only their own backup records
CREATE POLICY "backup_records_owner_select" ON backup_records
    FOR SELECT USING (auth.uid() = user_id);

-- Users can insert and update their own backups
CREATE POLICY "backup_records_owner_insert" ON backup_records
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "backup_records_owner_update" ON backup_records
    FOR UPDATE USING (auth.uid() = user_id);

-- 5. PASSWORD RESETS POLICIES
-- Normal users cannot directly query the password_resets table (restricted to service role / backend API)
CREATE POLICY "password_resets_service_only" ON password_resets
    FOR ALL USING (auth.role() = 'service_role');
