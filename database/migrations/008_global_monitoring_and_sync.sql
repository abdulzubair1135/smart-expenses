-- ============================================================================
-- MIGRATION 008: GLOBAL MONITORING TELEMETRY & MULTI-ENTITY SYNC METADATA
-- Target: Supabase PostgreSQL (public schema)
-- ============================================================================

-- 1. MONITORING EVENTS TABLE
CREATE TABLE IF NOT EXISTS public.monitoring_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'ERROR', 'CRITICAL')),
    source VARCHAR(32) NOT NULL CHECK (source IN ('ANDROID', 'BACKEND', 'DATABASE', 'SYNC', 'ADMIN')),
    error_code VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN_ERROR',
    message TEXT NOT NULL,
    stack_trace TEXT,
    screen_name VARCHAR(128),
    operation_name VARCHAR(128),
    app_version VARCHAR(32) NOT NULL DEFAULT '1.0.0',
    app_build VARCHAR(32) NOT NULL DEFAULT '1',
    android_version VARCHAR(32),
    device_model VARCHAR(128),
    environment VARCHAR(32) NOT NULL DEFAULT 'production',
    user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    installation_id VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'ACKNOWLEDGED', 'RESOLVED', 'IGNORED')),
    occurrence_count INT NOT NULL DEFAULT 1,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- Indexes for efficient admin querying and deduplication
CREATE INDEX IF NOT EXISTS idx_monitoring_events_severity ON public.monitoring_events(severity);
CREATE INDEX IF NOT EXISTS idx_monitoring_events_source ON public.monitoring_events(source);
CREATE INDEX IF NOT EXISTS idx_monitoring_events_status ON public.monitoring_events(status);
CREATE INDEX IF NOT EXISTS idx_monitoring_events_occurred_at ON public.monitoring_events(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_monitoring_events_correlation ON public.monitoring_events(correlation_id);
CREATE INDEX IF NOT EXISTS idx_monitoring_events_user_id ON public.monitoring_events(user_id);

-- 2. MONITORING EVENT STATUS HISTORY
CREATE TABLE IF NOT EXISTS public.monitoring_event_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES public.monitoring_events(id) ON DELETE CASCADE,
    old_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    changed_by TEXT NOT NULL,
    reason TEXT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_monitoring_history_event_id ON public.monitoring_event_status_history(event_id);

-- 3. ADD SYNC METADATA COLUMNS TO FINANCIAL TABLES (IF MISSING)
DO $$
BEGIN
    -- Accounts table sync metadata
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'accounts' AND column_name = 'server_revision') THEN
        ALTER TABLE public.accounts ADD COLUMN server_revision BIGINT NOT NULL DEFAULT 1;
    END IF;

    -- Saving Goals sync metadata
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'saving_goals' AND column_name = 'deleted_at') THEN
        ALTER TABLE public.saving_goals ADD COLUMN deleted_at TIMESTAMPTZ;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'saving_goals' AND column_name = 'server_revision') THEN
        ALTER TABLE public.saving_goals ADD COLUMN server_revision BIGINT NOT NULL DEFAULT 1;
    END IF;

    -- Upcoming Expenses sync metadata
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'upcoming_expenses' AND column_name = 'deleted_at') THEN
        ALTER TABLE public.upcoming_expenses ADD COLUMN deleted_at TIMESTAMPTZ;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'upcoming_expenses' AND column_name = 'server_revision') THEN
        ALTER TABLE public.upcoming_expenses ADD COLUMN server_revision BIGINT NOT NULL DEFAULT 1;
    END IF;

    -- Trips sync metadata
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'trips' AND column_name = 'deleted_at') THEN
        ALTER TABLE public.trips ADD COLUMN deleted_at TIMESTAMPTZ;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'trips' AND column_name = 'server_revision') THEN
        ALTER TABLE public.trips ADD COLUMN server_revision BIGINT NOT NULL DEFAULT 1;
    END IF;
END $$;

-- 4. ROW LEVEL SECURITY (RLS) POLICIES
ALTER TABLE public.monitoring_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.monitoring_event_status_history ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    -- Allow authenticated or anonymous clients to submit monitoring events
    DROP POLICY IF EXISTS "Clients can insert monitoring events" ON public.monitoring_events;
    CREATE POLICY "Clients can insert monitoring events" ON public.monitoring_events
        FOR INSERT WITH CHECK (true);

    -- Allow users to view only their own monitoring events
    DROP POLICY IF EXISTS "Users can read own monitoring events" ON public.monitoring_events;
    CREATE POLICY "Users can read own monitoring events" ON public.monitoring_events
        FOR SELECT USING (auth.uid() = user_id);

    -- Service role and admin functions have unrestricted access
END $$;
