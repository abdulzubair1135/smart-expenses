-- ==============================================================================
-- SMART EXPENSES TRACKER - COMPLETE PRODUCTION DATABASE MIGRATION
-- Target: Supabase PostgreSQL (Public Schema)
-- Description: Creates all core financial tables, indexes, constraints, 
--              RLS isolation policies, auto-provisioning triggers, and views.
-- ==============================================================================

-- 0. Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Function for automatic updated_at timestamping
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ==============================================================================
-- 1. PROFILES (user_profiles)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    account_status VARCHAR(30) NOT NULL DEFAULT 'verified' CHECK (account_status IN ('verified', 'unverified', 'suspended', 'requires_review')),
    terms_accepted BOOLEAN NOT NULL DEFAULT TRUE,
    terms_version VARCHAR(20) NOT NULL DEFAULT '2026.1',
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER update_profiles_modtime
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 2. ACCOUNTS (financial_accounts)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL CHECK (type IN ('cash', 'bank', 'upi', 'savings', 'credit')),
    opening_balance_minor BIGINT NOT NULL DEFAULT 0,
    currency_code VARCHAR(10) NOT NULL DEFAULT 'INR',
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_accounts_user ON public.accounts(user_id);

CREATE TRIGGER update_accounts_modtime
    BEFORE UPDATE ON public.accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 3. CATEGORIES
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE, -- NULL indicates system shared default
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('expense', 'income', 'both')),
    icon_key VARCHAR(50) NOT NULL DEFAULT 'ic_other',
    color_hex VARCHAR(10) NOT NULL DEFAULT '#6B7280',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_categories_user ON public.categories(user_id);

-- ==============================================================================
-- 4. TRIPS
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    destination VARCHAR(150) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'completed', 'archived')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trips_user ON public.trips(user_id);
CREATE INDEX IF NOT EXISTS idx_trips_dates ON public.trips(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_trips_status ON public.trips(status);

CREATE TRIGGER update_trips_modtime
    BEFORE UPDATE ON public.trips
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 5. TRANSACTIONS
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    type VARCHAR(30) NOT NULL CHECK (type IN ('expense', 'received', 'lend', 'borrow', 'transfer')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency_code VARCHAR(10) NOT NULL DEFAULT 'INR',
    category_id UUID REFERENCES public.categories(id) ON DELETE SET NULL,
    description TEXT,
    transaction_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'completed' CHECK (status IN ('completed', 'pending', 'cancelled')),
    idempotency_key VARCHAR(100) UNIQUE,
    related_person VARCHAR(100), -- For lend / borrow liabilities
    destination_account_id UUID REFERENCES public.accounts(id) ON DELETE RESTRICT, -- For transfer
    expense_group VARCHAR(20) NOT NULL DEFAULT 'GENERAL' CHECK (expense_group IN ('GENERAL', 'TRAVEL', 'SPECIAL')),
    trip_id UUID REFERENCES public.trips(id) ON DELETE SET NULL,
    is_important BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_transactions_user ON public.transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_account ON public.transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date ON public.transactions(transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_group ON public.transactions(expense_group);
CREATE INDEX IF NOT EXISTS idx_transactions_trip ON public.transactions(trip_id);
CREATE INDEX IF NOT EXISTS idx_transactions_idempotency ON public.transactions(idempotency_key);

CREATE TRIGGER update_transactions_modtime
    BEFORE UPDATE ON public.transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 6. SAVING GOALS
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.saving_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    target_amount_minor BIGINT NOT NULL CHECK (target_amount_minor > 0),
    current_amount_minor BIGINT NOT NULL DEFAULT 0 CHECK (current_amount_minor >= 0),
    target_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_goals_user ON public.saving_goals(user_id);

CREATE TRIGGER update_goals_modtime
    BEFORE UPDATE ON public.saving_goals
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 7. UPCOMING EXPENSES
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.upcoming_expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    expected_amount_minor BIGINT NOT NULL CHECK (expected_amount_minor > 0),
    due_date DATE NOT NULL,
    reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'paid', 'cancelled')),
    linked_transaction_id UUID REFERENCES public.transactions(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_upcoming_user ON public.upcoming_expenses(user_id);

CREATE TRIGGER update_upcoming_modtime
    BEFORE UPDATE ON public.upcoming_expenses
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 8. ACTIVITY EVENTS (Privacy Wall Enforced: Zero Financial Amounts)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.activity_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
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

CREATE INDEX IF NOT EXISTS idx_activity_user_time ON public.activity_events(user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_type_time ON public.activity_events(event_type, occurred_at DESC);

-- ==============================================================================
-- 9. USER PRESENCE (Heartbeat & Online Tracking)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.user_presence (
    user_id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    client_platform VARCHAR(30) DEFAULT 'android',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_presence_heartbeat ON public.user_presence(last_heartbeat_at DESC);

-- ==============================================================================
-- 10. BACKUP RECORDS (Verified Checksums)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.backup_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
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

CREATE INDEX IF NOT EXISTS idx_backups_user ON public.backup_records(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_backups_status ON public.backup_records(status);

-- ==============================================================================
-- 11. PASSWORD RESETS (Controlled OTP Recovery)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.password_resets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    otp_hash VARCHAR(128) NOT NULL,
    reset_token VARCHAR(128),
    expires_at TIMESTAMPTZ NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_resets_email_active ON public.password_resets(email, is_used, expires_at);

-- ==============================================================================
-- 12. NOTIFICATION DEVICES
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.notification_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    device_token TEXT NOT NULL UNIQUE,
    platform VARCHAR(20) NOT NULL DEFAULT 'android',
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_devices_user ON public.notification_devices(user_id);

-- ==============================================================================
-- 13. ADMIN AUDIT LOGS
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.admin_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    details JSONB,
    ip_address VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_time ON public.admin_audit_logs(created_at DESC);

-- ==============================================================================
-- 14. COMPATIBILITY VIEWS (Seamless support for alternative naming models)
-- ==============================================================================
CREATE OR REPLACE VIEW public.user_profiles AS SELECT * FROM public.profiles;
CREATE OR REPLACE VIEW public.financial_accounts AS SELECT * FROM public.accounts;
CREATE OR REPLACE VIEW public.trip_expenses AS 
    SELECT * FROM public.transactions 
    WHERE trip_id IS NOT NULL OR expense_group = 'TRAVEL';

-- ==============================================================================
-- 15. SEED SHARED SYSTEM CATEGORIES
-- ==============================================================================
INSERT INTO public.categories (id, user_id, name, type, icon_key, color_hex)
VALUES 
    ('d0000000-0000-0000-0000-000000000001', NULL, 'Food', 'expense', 'ic_food', '#EF4444'),
    ('d0000000-0000-0000-0000-000000000002', NULL, 'Travel', 'expense', 'ic_travel', '#F59E0B'),
    ('d0000000-0000-0000-0000-000000000003', NULL, 'Shopping', 'expense', 'ic_shopping', '#3B82F6'),
    ('d0000000-0000-0000-0000-000000000004', NULL, 'Bills', 'expense', 'ic_bills', '#8B5CF6'),
    ('d0000000-0000-0000-0000-000000000005', NULL, 'Others', 'expense', 'ic_other', '#6B7280'),
    ('d0000000-0000-0000-0000-000000000006', NULL, 'Salary', 'income', 'ic_salary', '#10B981')
ON CONFLICT (id) DO NOTHING;

-- ==============================================================================
-- 16. SUPABASE AUTH USER AUTO-PROVISIONING TRIGGER
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS TRIGGER AS $$
DECLARE
    new_user_name VARCHAR(100);
BEGIN
    new_user_name := COALESCE(
        NEW.raw_user_meta_data->>'full_name',
        NEW.raw_user_meta_data->>'name',
        split_part(NEW.email, '@', 1)
    );

    INSERT INTO public.profiles (id, display_name, email, account_status, terms_accepted, terms_version)
    VALUES (
        NEW.id,
        new_user_name,
        NEW.email,
        'verified',
        TRUE,
        '2026.1'
    )
    ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        last_login_at = NOW();

    -- Provision default Cash account
    INSERT INTO public.accounts (user_id, name, type, opening_balance_minor, currency_code)
    VALUES (NEW.id, 'Cash', 'cash', 0, 'INR')
    ON CONFLICT DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Attach trigger to auth.users if auth schema is present
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'auth' AND table_name = 'users') THEN
        DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
        CREATE TRIGGER on_auth_user_created
            AFTER INSERT ON auth.users
            FOR EACH ROW EXECUTE FUNCTION public.handle_new_auth_user();
    END IF;
END $$;

-- ==============================================================================
-- 17. ROW LEVEL SECURITY (RLS) POLICIES
-- ==============================================================================

-- Enable RLS across all user tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.trips ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saving_goals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.upcoming_expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.activity_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_presence ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.backup_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.password_resets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notification_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_audit_logs ENABLE ROW LEVEL SECURITY;

-- Drop existing policies if any to prevent duplicate errors
DO $$
DECLARE
    pol RECORD;
BEGIN
    FOR pol IN SELECT schemaname, tablename, policyname FROM pg_policies WHERE schemaname = 'public' LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I.%I', pol.policyname, pol.schemaname, pol.tablename);
    END LOOP;
END $$;

-- 17.1 Profiles: Users can view and update their own profile
CREATE POLICY "profiles_owner_select" ON public.profiles FOR SELECT USING (auth.uid() = id);
CREATE POLICY "profiles_owner_update" ON public.profiles FOR UPDATE USING (auth.uid() = id);

-- 17.2 Accounts: Users can only manage their own accounts
CREATE POLICY "accounts_owner_select" ON public.accounts FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "accounts_owner_insert" ON public.accounts FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "accounts_owner_update" ON public.accounts FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "accounts_owner_delete" ON public.accounts FOR DELETE USING (auth.uid() = user_id);

-- 17.3 Categories: Users can view system categories (user_id IS NULL) and manage own categories
CREATE POLICY "categories_select" ON public.categories FOR SELECT USING (user_id IS NULL OR auth.uid() = user_id);
CREATE POLICY "categories_owner_insert" ON public.categories FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "categories_owner_update" ON public.categories FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "categories_owner_delete" ON public.categories FOR DELETE USING (auth.uid() = user_id);

-- 17.4 Trips: User isolated
CREATE POLICY "trips_owner_select" ON public.trips FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "trips_owner_insert" ON public.trips FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "trips_owner_update" ON public.trips FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "trips_owner_delete" ON public.trips FOR DELETE USING (auth.uid() = user_id);

-- 17.5 Transactions: User isolated
CREATE POLICY "transactions_owner_select" ON public.transactions FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "transactions_owner_insert" ON public.transactions FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "transactions_owner_update" ON public.transactions FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "transactions_owner_delete" ON public.transactions FOR DELETE USING (auth.uid() = user_id);

-- 17.6 Saving Goals: User isolated
CREATE POLICY "goals_owner_manage" ON public.saving_goals FOR ALL USING (auth.uid() = user_id);

-- 17.7 Upcoming Expenses: User isolated
CREATE POLICY "upcoming_owner_manage" ON public.upcoming_expenses FOR ALL USING (auth.uid() = user_id);

-- 17.8 Activity Events: User isolated
CREATE POLICY "activity_owner_select" ON public.activity_events FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "activity_owner_insert" ON public.activity_events FOR INSERT WITH CHECK (auth.uid() = user_id);

-- 17.9 User Presence: User isolated
CREATE POLICY "presence_owner_select" ON public.user_presence FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "presence_owner_insert" ON public.user_presence FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "presence_owner_update" ON public.user_presence FOR UPDATE USING (auth.uid() = user_id);

-- 17.10 Backup Records: User isolated
CREATE POLICY "backups_owner_select" ON public.backup_records FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "backups_owner_insert" ON public.backup_records FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "backups_owner_update" ON public.backup_records FOR UPDATE USING (auth.uid() = user_id);

-- 17.11 Notification Devices: User isolated
CREATE POLICY "devices_owner_manage" ON public.notification_devices FOR ALL USING (auth.uid() = user_id);

-- 17.12 Password Resets & Admin Audit Logs: Restricted to Service Role
CREATE POLICY "resets_service_only" ON public.password_resets FOR ALL USING (auth.role() = 'service_role');
CREATE POLICY "audit_service_only" ON public.admin_audit_logs FOR ALL USING (auth.role() = 'service_role');
