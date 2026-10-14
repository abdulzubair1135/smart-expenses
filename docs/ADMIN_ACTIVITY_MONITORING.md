# SMART EXPENSES TRACKER - ADMIN ACTIVITY & PRESENCE MONITORING

## 1. Monitoring Purpose & Financial Privacy Wall
The Administrator Panel is designed purely for **technical system health, account lifecycle, presence monitoring, and operational diagnostic audit**.

> [!IMPORTANT]
> **Strict Financial Privacy Wall:**
> Under no circumstances does the admin panel display, query, or expose:
> - User transaction amounts or currency values
> - User balances or account values
> - Transaction descriptions or private memos
> - Travel trip itineraries or destinations
> - Personal lender/borrower contact names

---

## 2. Metrics & Operational Definitions

### 2.1 Overview Metrics
- **Total Registered Users:** Total count of unique profile identities in the database.
- **Verified / Unverified / Suspended Users:** Account status distribution.
- **Daily Active Users (DAU):** Users with at least one authenticated activity event in the last 24 hours.
- **Weekly Active Users (WAU):** Users with at least one authenticated activity event in the last 7 days.
- **Online Users:** Users with an authenticated heartbeat received within the last **120 seconds** (2 minutes).
- **Offline / Inactive Users:** Registered users with no heartbeat within the last 120 seconds.
- **Backup Operations:** Total successful vs. failed operations from `backup_records`.
- **Sync Failures:** Offline sync operations that encountered validation or conflict errors.
- **Server Health & Uptime:** Live process uptime, node version, and database connection state.

---

## 3. Presence Tracking Architecture
- **Heartbeat Endpoint:** `POST /api/v1/presence/heartbeat` (requires valid user JWT).
- **Client Protocol:** The Android application transmits a lightweight heartbeat every 60 seconds while in the foreground.
- **Server Storage:** Upserts into `user_presence` table (`last_seen_at`, `last_heartbeat_at`, `client_platform`).
- **Presence Calculation:**
  $$\text{Status} = \begin{cases} \text{Online} & \text{if } (\text{Now} - \text{last\_heartbeat\_at}) \le 120\text{s} \\ \text{Offline} & \text{otherwise} \end{cases}$$

---

## 4. Privacy-Conscious Activity Events
The `activity_events` audit table tracks operational state transitions:
- `ACCOUNT_REGISTERED`
- `LOGIN_SUCCESS` / `LOGIN_FAILURE` / `LOGOUT`
- `TRANSACTION_CREATED` / `TRANSACTION_UPDATED` / `TRANSACTION_DELETED` *(Event type and timestamp ONLY; zero amounts)*
- `TRIP_CREATED` / `TRIP_ARCHIVED` *(Metadata excludes personal notes)*
- `BACKUP_STARTED` / `BACKUP_SUCCEEDED` / `BACKUP_FAILED`
- `SYNC_SUCCEEDED` / `SYNC_FAILED`
- `PASSWORD_RESET_REQUESTED` / `PASSWORD_RESET_SUCCEEDED`
