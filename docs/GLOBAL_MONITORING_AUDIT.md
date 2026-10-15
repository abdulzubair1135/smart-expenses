# Global Monitoring & Synchronization Audit

**Project:** Smart Expenses Tracker  
**Date:** October 15, 2026  
**Auditor:** Senior Full-Stack & Systems QA Engineer  

---

## 1. Executive Summary

This audit assesses the current state of **Smart Expenses Tracker** across the Android client (Pure Java + XML), Node.js Express TypeScript backend, Supabase PostgreSQL database, and Vite React Admin Panel. The goal is to establish resilient global error/event telemetry, a persistent offline-first Room queue, safe bi-directional account data synchronization, and an Admin Control Panel monitoring suite without breaking existing financial guarantees or introducing Kotlin.

---

## 2. Component-by-Component Audit

### 2.1 Android Application Architecture (Java + XML)
- **Language & UI:** 100% Pure Java + XML. Zero Kotlin files exist.
- **Application Lifecycle (`SmartExpensesApp.java`):** Initializes `AppDatabase.getDatabase(this)`. Currently lacks a centralized `UncaughtExceptionHandler` and does not schedule periodic background sync via WorkManager upon boot.
- **Session Management (`SessionManager.java`):** Stores JWT, user ID (`d0000000-0000-0000-0000-000000000001` fallback), user name, email, and theme mode in `SharedPreferences`. Properly wipes tokens on logout.
- **Local Database (`AppDatabase.java`, Room v2):**
  - Current entities: `TransactionEntity`, `AccountEntity`, `CategoryEntity`, `SavingGoalEntity`, `UpcomingExpenseEntity`, `TripEntity`.
  - Database schema version: 2 with migration `MIGRATION_1_2`.
  - **Missing:** A dedicated Room entity and DAO for persistent monitoring events (`MonitoringEventEntity`, `MonitoringEventDao`) to queue errors/crashes across app restarts.
  - **Missing:** Sync timestamps/revisions (`updatedAt`, `deletedAt`, `syncStatus`, `serverRevision`) on `AccountEntity`, `SavingGoalEntity`, and `UpcomingExpenseEntity`.
- **API Client (`BackendApiClient.java`):**
  - Uses OkHttp3 with primary cloud fallback (`https://smart-expenses-8pf3.onrender.com` -> `127.0.0.1:4000` -> `10.0.2.2:4000`).
  - Supports auth login/register/reset, heartbeat, and backup export.
  - **Missing:** Batch monitoring event submission (`POST /api/v1/monitoring/events/batch`) and full two-way entity sync (`/api/v1/sync/pull`, `/api/v1/sync/push`).
- **WorkManager:**
  - `androidx.work:work-runtime:2.9.1` is in `build.gradle`, but no concrete `Worker` classes are currently scheduled.
  - **Required:** `MonitoringUploadWorker` (with exponential backoff and network constraints) and `AccountSyncWorker` (with periodic and on-demand triggers).

### 2.2 Backend API (Node.js + Express + TypeScript)
- **Deployment:** Render web service (`https://smart-expenses-8pf3.onrender.com`).
- **Data Layer:** `dataAdapter.ts` handles hybrid Supabase PostgreSQL persistence with local resilient in-memory fallback for offline test execution.
- **Routes Present:**
  - `/api/v1/auth`: Login, Register, Forgot Password, Reset Password.
  - `/api/v1/accounts`: Account listing and creation.
  - `/api/v1/categories`: Category management.
  - `/api/v1/transactions`: Transaction ledger with minor unit validation.
  - `/api/v1/trips`: Trips management and expense aggregation.
  - `/api/v1/expenses`: Period-wise summaries (Daily, Monthly, Special).
  - `/api/v1/sync`: Basic `/push` and `/pull` skeleton for transactions only.
  - `/api/v1/admin`: Overview metrics, user status management, audit logs.
  - `/health` & `/health/ready`: Uptime and dynamic Supabase probe.
- **Missing Endpoints:**
  - `POST /api/v1/monitoring/events/batch`: Authenticated batch event ingestion with schema validation, sanitization, and deduplication.
  - `GET /api/v1/admin/monitoring/events`: Filtered, paginated event feed for administrators.
  - `GET /api/v1/admin/monitoring/events/:id`: Specific event inspection.
  - `PATCH /api/v1/admin/monitoring/events/:id/status`: Event acknowledgment/resolution tracking.
  - `GET /api/v1/admin/monitoring/summary`: Aggregated error metrics and ingestion rates.
  - `GET /api/v1/admin/monitoring/health`: Component-level health indicators.

### 2.3 Database Schema (Supabase PostgreSQL)
- **Current Migration State:**
  - Master migration `000_complete_production_schema.sql` was applied to remote Supabase project `lbxjzfbjaxplmvdzsvzx`.
  - Tables verified: `profiles`, `accounts`, `categories`, `trips`, `transactions`, `saving_goals`, `upcoming_expenses`, `activity_events`, `user_presence`, `backup_records`, `admin_audit_logs`, `password_resets`, `notification_devices`.
  - Compatibility views verified: `user_profiles`, `financial_accounts`, `trip_expenses`.
  - RLS policies verified: `auth.uid() = user_id` on all user-owned tables.
- **Missing Tables & Columns for Phase 10:**
  - Table: `monitoring_events` for centralized telemetry and error storage.
  - Table: `monitoring_event_status_history` for administrative state change audit trail.
  - Column additions on `accounts`, `saving_goals`, and `upcoming_expenses` for `updated_at`, `deleted_at`, and `server_revision` to support robust conflict resolution.

### 2.4 Admin Control Panel (Vite + React + TypeScript)
- **Location:** `admin/` directory, runs on port 5173.
- **Current State:** Has Overview, Users, and Activity tabs.
- **Missing:**
  - Dedicated **Monitoring Overview** with error counts, unresolved criticals, and ingestion health.
  - **Live Event Feed** with multi-parameter filtering (severity, source, date range, app version, status).
  - **Event Details Modal** supporting status update (Acknowledge, Resolve, Ignore) with audit tracking.
  - Detailed **Service Health** breakdown separating Render API, Database Connectivity, Monitoring Ingestion, Authentication, and Sync status.

---

## 3. What Exists, What Is Missing, and What Must Be Reused

| Component | Status | Action Required |
| :--- | :--- | :--- |
| Pure Java Android App | Exists | Preserve 100%. Add Java Room entity, DAO, MonitoringManager, and Workers. |
| Room DB (v2) | Exists | Migrate to v3 to add `monitoring_events` table and sync columns. |
| WorkManager Runtime | Included in Gradle | Implement `MonitoringUploadWorker` and `AccountSyncWorker`. |
| Financial Minor Units (`long`) | Exists & Enforced | Preserve integer minor units (paise) across all models and sync payloads. |
| Supabase Master Schema | Applied (13 tables) | Add `008_global_monitoring_and_sync.sql` for monitoring tables and sync tracking. |
| Backend Core Routes | Functional (54 tests pass) | Add monitoring routes and enhance sync routes to handle all financial entities. |
| Admin Panel | Functional | Add Monitoring section, live feed, filtering, and event resolution workflow. |

---

## 4. Architectural Rules & Invariants
1. **Never Log Sensitive Data:** Passwords, tokens, transaction amounts, and balance figures must be strictly stripped from error logs and stack traces.
2. **Local-First Queueing:** If Render or Supabase is down, events and financial records must queue locally in Room with bounded capacity.
3. **Idempotency Everywhere:** All monitoring uploads and financial sync operations must use client-generated stable UUIDs and idempotency keys.
4. **User Isolation:** All operations derive user identity from authenticated tokens; User A cannot access or overwrite User B's events or data.
