-- Migration 004: Expense Groups & Travel Trips
-- Adds structured period and classification tracking without duplicating transactions

-- 1. TRIPS TABLE
CREATE TABLE IF NOT EXISTS trips (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    destination VARCHAR(150) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'completed', 'archived')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_trips_user ON trips(user_id);
CREATE INDEX IF NOT EXISTS idx_trips_dates ON trips(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_trips_status ON trips(status);

-- 2. ALTER TRANSACTIONS FOR EXPENSE GROUP & TRIP LINKING
ALTER TABLE transactions 
    ADD COLUMN IF NOT EXISTS expense_group VARCHAR(20) NOT NULL DEFAULT 'GENERAL' 
    CHECK (expense_group IN ('GENERAL', 'TRAVEL', 'SPECIAL'));

ALTER TABLE transactions 
    ADD COLUMN IF NOT EXISTS trip_id UUID REFERENCES trips(id) ON DELETE SET NULL;

ALTER TABLE transactions 
    ADD COLUMN IF NOT EXISTS is_important BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_transactions_group ON transactions(expense_group);
CREATE INDEX IF NOT EXISTS idx_transactions_trip ON transactions(trip_id);
