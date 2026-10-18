-- ========================================================
-- SMART EXPENSES TRACKER - COMPLETE SUPABASE SQL SCHEMA
-- Run this in Supabase SQL Editor (New Query)
-- ========================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- --------------------------------------------------------
-- 1. PROFILES TABLE
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS profiles (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  phone TEXT,
  account_status TEXT NOT NULL DEFAULT 'unverified'
    CHECK (account_status IN ('verified', 'unverified', 'suspended', 'requires_review')),
  password_hash TEXT,
  terms_version TEXT DEFAULT '2026.1',
  privacy_version TEXT DEFAULT '2026.1',
  terms_consented_at TIMESTAMPTZ,
  last_login_at TIMESTAMPTZ,
  fcm_token TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- --------------------------------------------------------
-- 2. ACCOUNTS TABLE
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS accounts (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  type TEXT NOT NULL DEFAULT 'cash'
    CHECK (type IN ('cash', 'bank', 'upi', 'savings', 'credit')),
  opening_balance_minor BIGINT NOT NULL DEFAULT 0,
  currency_code TEXT NOT NULL DEFAULT 'INR',
  archived_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_accounts_user_id ON accounts(user_id);

-- --------------------------------------------------------
-- 3. CATEGORIES TABLE
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  type TEXT NOT NULL DEFAULT 'expense'
    CHECK (type IN ('expense', 'income', 'both')),
  icon_key TEXT NOT NULL DEFAULT 'ic_other',
  color_hex TEXT NOT NULL DEFAULT '#6B7280',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_categories_user_id ON categories(user_id);

-- Insert default system categories (user_id = NULL means global/shared)
INSERT INTO categories (user_id, name, type, icon_key, color_hex) VALUES
  (NULL, 'Food / Dining', 'expense', 'ic_food', '#EF4444'),
  (NULL, 'Transport', 'expense', 'ic_travel', '#F59E0B'),
  (NULL, 'Shopping', 'expense', 'ic_shopping', '#3B82F6'),
  (NULL, 'Bills & Utilities', 'expense', 'ic_bills', '#8B5CF6'),
  (NULL, 'Healthcare', 'expense', 'ic_health', '#EC4899'),
  (NULL, 'Education', 'expense', 'ic_edu', '#06B6D4'),
  (NULL, 'Entertainment', 'expense', 'ic_entertainment', '#F97316'),
  (NULL, 'Others', 'expense', 'ic_other', '#6B7280'),
  (NULL, 'Salary', 'income', 'ic_salary', '#10B981'),
  (NULL, 'Business', 'income', 'ic_business', '#22C55E'),
  (NULL, 'Freelance', 'income', 'ic_freelance', '#84CC16'),
  (NULL, 'Earnings', 'income', 'ic_earnings', '#14B8A6')
ON CONFLICT DO NOTHING;

-- --------------------------------------------------------
-- 4. TRIPS TABLE
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS trips (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  destination TEXT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  description TEXT,
  status TEXT NOT NULL DEFAULT 'active'
    CHECK (status IN ('active', 'completed', 'archived')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trips_user_id ON trips(user_id);

-- --------------------------------------------------------
-- 5. TRANSACTIONS TABLE
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,
  type TEXT NOT NULL
    CHECK (type IN ('expense', 'received', 'lend', 'borrow', 'transfer')),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  currency_code TEXT NOT NULL DEFAULT 'INR',
  category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
  description TEXT DEFAULT '',
  transaction_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  status TEXT NOT NULL DEFAULT 'completed'
    CHECK (status IN ('completed', 'pending', 'cancelled')),
  idempotency_key TEXT UNIQUE,
  related_person TEXT,
  destination_account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,
  expense_group TEXT DEFAULT 'GENERAL'
    CHECK (expense_group IN ('GENERAL', 'TRAVEL', 'SPECIAL')),
  trip_id UUID REFERENCES trips(id) ON DELETE SET NULL,
  is_important BOOLEAN DEFAULT FALSE,
  deleted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transactions_user_id ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(user_id, transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(user_id, type);
CREATE INDEX IF NOT EXISTS idx_transactions_deleted ON transactions(deleted_at) WHERE deleted_at IS NULL;

-- --------------------------------------------------------
-- 6. SAVING GOALS TABLE
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS saving_goals (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  target_amount_minor BIGINT NOT NULL CHECK (target_amount_minor > 0),
  current_amount_minor BIGINT NOT NULL DEFAULT 0,
  target_date DATE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_saving_goals_user_id ON saving_goals(user_id);

-- --------------------------------------------------------
-- 7. UPCOMING EXPENSES TABLE
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS upcoming_expenses (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  expected_amount_minor BIGINT NOT NULL CHECK (expected_amount_minor > 0),
  due_date DATE NOT NULL,
  reminder_enabled BOOLEAN DEFAULT TRUE,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'paid', 'cancelled')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_upcoming_expenses_user_id ON upcoming_expenses(user_id);

-- --------------------------------------------------------
-- 8. DEVICE TOKENS TABLE (FCM Push Notifications)
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_tokens (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  token TEXT NOT NULL UNIQUE,
  platform TEXT DEFAULT 'android',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user_id ON device_tokens(user_id);

-- --------------------------------------------------------
-- 9. ACTIVITY EVENTS TABLE
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS activity_events (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  event_type TEXT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  result TEXT NOT NULL DEFAULT 'success'
    CHECK (result IN ('success', 'failure', 'pending')),
  request_id TEXT,
  metadata JSONB DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_activity_events_user_id ON activity_events(user_id);
CREATE INDEX IF NOT EXISTS idx_activity_events_occurred ON activity_events(occurred_at DESC);

-- --------------------------------------------------------
-- 10. USER PRESENCE TABLE
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_presence (
  user_id UUID PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  client_platform TEXT DEFAULT 'android',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- --------------------------------------------------------
-- 11. BACKUP RECORDS TABLE
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS backup_records (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  backup_type TEXT NOT NULL
    CHECK (backup_type IN ('json_export', 'cloud_snapshot', 'full_database')),
  status TEXT NOT NULL DEFAULT 'started'
    CHECK (status IN ('started', 'completed', 'failed')),
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ,
  record_count INTEGER DEFAULT 0,
  error_code TEXT,
  storage_reference TEXT,
  checksum TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- --------------------------------------------------------
-- 12. PASSWORD RESETS TABLE
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_resets (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email TEXT NOT NULL,
  otp_hash TEXT NOT NULL,
  reset_token TEXT,
  expires_at TIMESTAMPTZ NOT NULL,
  is_used BOOLEAN DEFAULT FALSE,
  attempts INTEGER DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_password_resets_email ON password_resets(email);

-- --------------------------------------------------------
-- 13. ADMIN AUDIT LOGS TABLE
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_audit_logs (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  admin_id TEXT NOT NULL,
  action TEXT NOT NULL,
  target_type TEXT NOT NULL,
  target_id TEXT,
  details JSONB DEFAULT '{}',
  ip_address TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- --------------------------------------------------------
-- 14. MONITORING EVENTS TABLE
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS monitoring_events (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  event_type TEXT NOT NULL,
  severity TEXT NOT NULL DEFAULT 'INFO'
    CHECK (severity IN ('INFO', 'WARNING', 'ERROR', 'CRITICAL')),
  source TEXT NOT NULL DEFAULT 'ANDROID'
    CHECK (source IN ('ANDROID', 'BACKEND', 'DATABASE', 'SYNC', 'ADMIN')),
  error_code TEXT NOT NULL DEFAULT 'UNKNOWN',
  message TEXT NOT NULL,
  stack_trace TEXT,
  screen_name TEXT,
  operation_name TEXT,
  app_version TEXT NOT NULL DEFAULT '1.0.0',
  app_build TEXT NOT NULL DEFAULT '1',
  android_version TEXT,
  device_model TEXT,
  environment TEXT NOT NULL DEFAULT 'production',
  user_id UUID REFERENCES profiles(id) ON DELETE SET NULL,
  installation_id TEXT NOT NULL DEFAULT '',
  correlation_id TEXT NOT NULL DEFAULT '',
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  status TEXT NOT NULL DEFAULT 'NEW'
    CHECK (status IN ('NEW', 'ACKNOWLEDGED', 'RESOLVED', 'IGNORED')),
  occurrence_count INTEGER DEFAULT 1,
  metadata_json JSONB DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_monitoring_severity ON monitoring_events(severity, status);
CREATE INDEX IF NOT EXISTS idx_monitoring_occurred ON monitoring_events(occurred_at DESC);

-- --------------------------------------------------------
-- 15. MONITORING EVENT STATUS HISTORY TABLE
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS monitoring_event_status_history (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  event_id UUID NOT NULL REFERENCES monitoring_events(id) ON DELETE CASCADE,
  old_status TEXT NOT NULL,
  new_status TEXT NOT NULL,
  changed_by TEXT NOT NULL,
  reason TEXT,
  changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ========================================================
-- ROW LEVEL SECURITY (RLS)
-- ========================================================

-- Enable RLS on all user-data tables
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE trips ENABLE ROW LEVEL SECURITY;
ALTER TABLE saving_goals ENABLE ROW LEVEL SECURITY;
ALTER TABLE upcoming_expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE activity_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_presence ENABLE ROW LEVEL SECURITY;
ALTER TABLE backup_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_resets ENABLE ROW LEVEL SECURITY;

-- Service role bypasses RLS (backend uses service_role key)
-- These policies are for future direct client access if needed

CREATE POLICY "Users can view own profile" ON profiles
  FOR SELECT USING (id = auth.uid());

CREATE POLICY "Users can update own profile" ON profiles
  FOR UPDATE USING (id = auth.uid());

CREATE POLICY "Users can view own accounts" ON accounts
  FOR ALL USING (user_id = auth.uid());

CREATE POLICY "Users can view all categories" ON categories
  FOR SELECT USING (user_id = auth.uid() OR user_id IS NULL);

CREATE POLICY "Users can manage own categories" ON categories
  FOR INSERT WITH CHECK (user_id = auth.uid());

CREATE POLICY "Users can view own transactions" ON transactions
  FOR ALL USING (user_id = auth.uid());

CREATE POLICY "Users can view own trips" ON trips
  FOR ALL USING (user_id = auth.uid());

CREATE POLICY "Users can view own savings goals" ON saving_goals
  FOR ALL USING (user_id = auth.uid());

CREATE POLICY "Users can view own upcoming expenses" ON upcoming_expenses
  FOR ALL USING (user_id = auth.uid());

CREATE POLICY "Users can manage own device tokens" ON device_tokens
  FOR ALL USING (user_id = auth.uid());

CREATE POLICY "Users can view own activity" ON activity_events
  FOR SELECT USING (user_id = auth.uid());

CREATE POLICY "Users can view own presence" ON user_presence
  FOR ALL USING (user_id = auth.uid());

CREATE POLICY "Users can view own backups" ON backup_records
  FOR SELECT USING (user_id = auth.uid());

-- ========================================================
-- DONE! All tables created with RLS enabled.
-- ========================================================
