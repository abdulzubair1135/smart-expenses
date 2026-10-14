# IMPLEMENTATION PLAN: SMART EXPENSES TRACKER

This plan defines the step-by-step roadmap to build the entire full-stack system.

---

## Stage 1: Specification & Documentation Foundation
- [x] Audit workspace & host environment (`PROJECT_AUDIT.md`).
- [x] Traceability matrix (`REQUIREMENTS_TRACEABILITY.md`).
- [x] Task tracker (`TASK_TRACKER.md`).
- [x] Architecture, Database Schema, Financial Rules, API Contract, UI/UX Specs.

## Stage 2: Database Layer & Supabase Schema
- [ ] PostgreSQL migration `001_initial_schema.sql`:
  - `profiles`, `accounts`, `categories`, `transactions`, `transfers`, `loans`, `saving_goals`, `upcoming_expenses`, `notification_devices`, `admin_audit_logs`.
- [ ] PostgreSQL migration `002_rls_policies.sql`:
  - Row Level Security policies enforcing authenticated user isolation.
- [ ] Seed data `003_seed_data.sql`:
  - Default categories (Food, Travel, Shopping, Salary, etc.) and demo profiles for viva/presentation.

## Stage 3: Backend API Service (Node.js + Express + TypeScript)
- [ ] TypeScript configuration (`tsconfig.json`), dependencies (`package.json`).
- [ ] Express server setup with CORS, helmet, morgan, centralized error handling.
- [ ] Supabase client & JWT authentication middleware (verifying Bearer tokens).
- [ ] Input validation schemas using Zod.
- [ ] API Endpoints:
  - Health: `/health`, `/health/ready`
  - Auth/Profile: `/api/v1/profile`
  - Accounts: `/api/v1/accounts` (CRUD, calculate balance)
  - Categories: `/api/v1/categories` (CRUD)
  - Transactions: `/api/v1/transactions` (CRUD, filtering, date range, pagination)
  - Dashboard: `/api/v1/dashboard/summary`, `/api/v1/insights/monthly`, `/api/v1/insights/categories`
  - Goals & Upcoming: `/api/v1/goals`, `/api/v1/upcoming`
  - Sync: `/api/v1/sync/push`, `/api/v1/sync/pull` (idempotency, outbox sync)
  - Notifications: `/api/v1/notifications/devices`
  - Admin: `/api/v1/admin/overview`, `/api/v1/admin/users`, `/api/v1/admin/health`, `/api/v1/admin/audit-logs`
- [ ] Automated Jest / Supertest integration tests.
- [ ] OpenAPI 3.0 specification (`api/openapi.yaml`).

## Stage 4: Admin Panel (React + TypeScript + Vite)
- [ ] Vite project setup with TypeScript and modern Tailwind styling.
- [ ] State management, API client with JWT handling, Protected routes.
- [ ] Pages:
  - Admin Login
  - Overview Dashboard (Real metrics: total users, verified, unverified, suspended, sync failures, API health)
  - User Management (Search, filter, update status, audit trail)
  - Server Health & Diagnostics (Endpoint ping, database status, system stats)
  - Sync & Notification monitoring
  - Security Audit Logs

## Stage 5: Android Mobile Application (Java + XML Only)
- [ ] Gradle build configuration (`app/build.gradle`, root `build.gradle`, `settings.gradle` with Java 17/21 compatibility, Room, Retrofit, Navigation Component, Material 3, ViewBinding).
- [ ] Design Tokens & Themes: `colors.xml`, `strings.xml`, `styles.xml`, vector drawables, layout components.
- [ ] Local Room Database:
  - Entities: `UserEntity`, `AccountEntity`, `CategoryEntity`, `TransactionEntity`, `GoalEntity`, `UpcomingEntity`, `SyncQueueEntity`.
  - DAOs with Room transactions & LiveData/Rx/Executor queries.
  - Room Database instance (`AppDatabase`).
- [ ] Network & Remote Layer:
  - Retrofit API interfaces, OkHttp interceptor with Auth Token and logging.
  - Data Repositories implementing Local-First architecture.
- [ ] UI Screens (strictly following the 30-screen master visual reference):
  1. Splash & Init Screen (`SplashActivity`)
  2. Onboarding / Welcome (`OnboardingActivity`)
  3. Authentication (`LoginActivity`, `RegisterActivity`)
  4. Main Container (`MainActivity` with Navigation Component)
  5. Home Screen: Balance Card, Today's summary, Upcoming banner, Goal progress, Recent transactions
  6. Smart Add Modal / BottomSheet: Expense, Received, Lend, Borrow, Transfer, Voice Add
  7. Add Expense, Add Received, Add Lend, Add Borrow, Add Transfer screens
  8. Voice Assistant Flow: Speech-to-text listener dialog & draft confirmation screen
  9. Activity / Transaction History: Search, Filters (Expense, Received, Lend, Transfer), Date selector
  10. Transaction Details screen: Detail view, Edit dialog, Delete confirmation
  11. Money Screen: Total available balance, Account cards (Cash, Bank, UPI, Savings), Add Account
  12. Savings Goals: Target, saved amount, progress bar, add/edit goal
  13. Insights Screen: Income vs Expense, Net balance, Expense by Category donut/bar breakdown
  14. Settings & Privacy: Session info, Cloud Sync status, Notification toggle, Dark mode, Logout
  15. Offline indicator & Error state dialogs
- [ ] Background Sync: `WorkManager` `SyncWorker` processing pending outbox operations.
- [ ] Unit & Robolectric/Instrumentation tests.

## Stage 6: Windows Automation & USB Installation Scripts
- [ ] `START_ALL.bat`: Starts Backend and Admin servers, performs health verification.
- [ ] `STOP_ALL.bat`: Gracefully stops the background dev processes.
- [ ] `INSTALL_ANDROID_USB.bat`: Checks ADB, builds APK via Gradle, checks device state, installs & launches app.
- [ ] `RUN_AND_INSTALL.bat`: Orchestrates service startup and USB phone install.
- [ ] `scripts/check-environment.bat` & `scripts/check-adb.bat`.

## Stage 7: Documentation & Viva Preparation
- [ ] Complete documentation: `README.md`, `AGENTS.md`, `ARCHITECTURE.md`, `DATABASE_SCHEMA.md`, `FINANCIAL_RULES.md`, `UI_UX_SPECIFICATION.md`, `OFFLINE_SYNC.md`, `PRIVACY_SECURITY.md`, `NOTIFICATIONS.md`, `ADMIN_PANEL.md`, `DEPLOYMENT.md`, `WINDOWS_SETUP.md`, `TEST_PLAN.md`, `VIVA_QUESTIONS.md`.

## Stage 8: End-to-End Verification & APK Build
- [ ] Run backend tests.
- [ ] Run admin build (`npm run build`).
- [ ] Run Android Gradle build (`gradlew assembleDebug`).
- [ ] Verify APK file generated.
