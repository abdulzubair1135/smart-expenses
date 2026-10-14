# SMART EXPENSES TRACKER - DATABASE SCHEMA & MIGRATIONS

## 1. Overview
The database operates on a local-first architecture with dual persistence:
1. **Android Client:** Room (SQLite) with versioned migrations (`v1 -> v2`).
2. **Cloud Backend:** Supabase (PostgreSQL 15+) with Row Level Security (RLS).

---

## 2. Table Schemas

### `trips` Table
Stores travel trip buckets for grouping travel expenses without creating pseudo-accounts.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `VARCHAR(36)` / `UUID` | `PRIMARY KEY` | Unique identifier (UUIDv4) |
| `user_id` | `VARCHAR(36)` / `UUID` | `NOT NULL, REFERENCES users(id)` | Owner user ID |
| `name` | `VARCHAR(100)` | `NOT NULL` | Trip label (e.g. "Mumbai Visit") |
| `destination`| `VARCHAR(150)` | `NOT NULL` | Destination city/place |
| `start_date` | `VARCHAR(10)` | `NOT NULL` | Start date (`YYYY-MM-DD`) |
| `end_date` | `VARCHAR(10)` | `NOT NULL` | End date (`YYYY-MM-DD`) |
| `description`| `TEXT` | `NULLABLE` | Optional notes |
| `status` | `VARCHAR(20)` | `DEFAULT 'active'` | `active`, `completed`, `archived` |
| `created_at` | `BIGINT` / `TIMESTAMPTZ`| `DEFAULT NOW()` | Creation timestamp |
| `updated_at` | `BIGINT` / `TIMESTAMPTZ`| `DEFAULT NOW()` | Update timestamp |

**Indexes:**
- `idx_trips_user_id ON trips(user_id)`
- `idx_trips_status ON trips(user_id, status)`

---

### `transactions` Table (Enhanced)
Core ledger table containing all income, expenses, lending, borrowing, and transfers.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `VARCHAR(36)` | `PRIMARY KEY` | UUIDv4 |
| `user_id` | `VARCHAR(36)` | `NOT NULL, REFERENCES users(id)` | Owner user ID |
| `account_id` | `VARCHAR(50)` | `NOT NULL` | Account (`acc_upi`, `acc_cash`, `acc_bank`) |
| `type` | `VARCHAR(20)` | `NOT NULL` | `expense`, `received`, `lend`, `borrow`, `transfer` |
| `amount_minor`| `BIGINT` | `NOT NULL, CHECK (amount_minor > 0)` | Minor units (paise in INR) |
| `currency_code`| `VARCHAR(3)`| `DEFAULT 'INR'` | ISO 4217 code |
| `category_id` | `VARCHAR(50)`| `NULLABLE` | Category identifier |
| `description`| `VARCHAR(255)`| `NULLABLE` | Transaction memo |
| `transaction_date`| `VARCHAR(30)` | `NOT NULL` | ISO 8601 formatted date |
| `status` | `VARCHAR(20)` | `DEFAULT 'completed'` | `completed`, `pending`, `cancelled` |
| `idempotency_key`| `VARCHAR(100)`| `NULLABLE, UNIQUE` | Sync deduplication key |
| `related_person`| `VARCHAR(100)`| `NULLABLE` | Contact for lend/borrow |
| `destination_account_id`| `VARCHAR(50)`| `NULLABLE` | For account transfers |
| `is_synced` | `BOOLEAN` | `DEFAULT FALSE` | Local-First sync state |
| `created_at` | `BIGINT` | `NOT NULL` | Epoch milliseconds |
| `expense_group` | `VARCHAR(20)` | `DEFAULT 'GENERAL'` | `GENERAL`, `TRAVEL`, `SPECIAL` |
| `trip_id` | `VARCHAR(36)` | `NULLABLE, REFERENCES trips(id)` | Linked trip ID |
| `is_important` | `BOOLEAN` | `DEFAULT FALSE` | Special / Emergency marker |

**Indexes:**
- `idx_transactions_user_date ON transactions(user_id, transaction_date DESC)`
- `idx_transactions_group ON transactions(user_id, expense_group)`
- `idx_transactions_trip ON transactions(trip_id)`

---

## 3. Row Level Security (RLS) Policies

All PostgreSQL tables enforce Row Level Security:
```sql
ALTER TABLE trips ENABLE ROW LEVEL SECURITY;

CREATE POLICY "trips_owner_select" ON trips
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "trips_owner_insert" ON trips
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "trips_owner_update" ON trips
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "trips_owner_delete" ON trips
    FOR DELETE USING (auth.uid() = user_id);
```

---

## 4. Room Migration (1 -> 2)
```java
public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("ALTER TABLE transactions ADD COLUMN expenseGroup TEXT DEFAULT 'GENERAL'");
        database.execSQL("ALTER TABLE transactions ADD COLUMN tripId TEXT");
        database.execSQL("ALTER TABLE transactions ADD COLUMN isImportant INTEGER NOT NULL DEFAULT 0");

        database.execSQL("CREATE TABLE IF NOT EXISTS trips (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "userId TEXT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "destination TEXT NOT NULL, " +
                "startDate TEXT NOT NULL, " +
                "endDate TEXT NOT NULL, " +
                "description TEXT, " +
                "status TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL, " +
                "updatedAt INTEGER NOT NULL)");
    }
};
```
