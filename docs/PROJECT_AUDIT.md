# PROJECT AUDIT: SMART EXPENSES TRACKER

**Date:** 2026-10-13  
**Auditor:** Senior Engineering Team (Full-Stack, Android, Security, DevOps)

---

## 1. Environment & Workspace Status

- **Root Directory:** `C:\Users\abdul\OneDrive\ドキュメント\divya`
- **Initial State:** Empty directory (no legacy conflicting files or broken implementations).
- **Git Repository:** Not initialized initially (`fatal: not a git repository`). Initialized for structured development.
- **Node.js:** `v25.1.0` (Verified)
- **npm:** `11.6.2` (Verified)
- **System Java:** Oracle JDK 25 (`build 25.0.1+8-LTS-27`)
- **Android Studio JBR:** OpenJDK 21 LTS (`21.0.10+-14961533-b1163.108`) located at `C:\Program Files\Android\Android Studio\jbr`.
  *Note:* Gradle 8.x + AGP 8.x works with Java 17 and Java 21 LTS. Gradle scripts and Windows batch scripts will default `JAVA_HOME` to the Android Studio JBR (Java 21) for building Android smoothly.
- **Android SDK:** Installed at `C:\Users\abdul\AppData\Local\Android\Sdk`.
- **ADB Tooling:** Verified at `C:\Users\abdul\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- **Physical USB Device Status:** Currently `List of devices attached` returned 0 devices. (Scripts will handle device detection, unauthorized status, offline status, and guide the user).

---

## 2. Requirement & Architectural Gaps Analysis

1. **Android App Architecture:**
   - Strict Constraint: **Pure Java + XML**, never Kotlin.
   - Design Match: 30-screen UI/UX specification provided in master reference image (`media_1791905542412_1a075d84.jpg`):
     - Splash, Onboarding/Welcome, Home Dashboard, Add Transaction BottomSheet/Menu, Add Expense, Add Received, Add Lend, Add Borrow, Add Transfer, Voice Add, Voice Confirmation, Activity / Transaction History, Transaction Details, Money Screen, Savings Goals, Insights with Category breakdown, Offline Working dialogs, Input Validation, Security & Room persistence.
   - Technologies: Room Database with SQLite, Retrofit 2 + OkHttp 3, WorkManager, ViewModel + LiveData, Navigation Component, Material Design 3.

2. **Backend Architecture:**
   - Stack: Node.js + TypeScript + Express + Zod + Supabase PostgreSQL client (`@supabase/supabase-js`) + Jose/JWT auth validation.
   - Modular monolith structure: `config`, `routes`, `controllers`, `services`, `repositories`, `middleware`, `validators`.
   - Health check endpoints (`/health`, `/health/ready`), CRUD APIs for accounts, categories, transactions, dashboard, insights, goals, upcoming expenses, sync (`/push`, `/pull`), FCM tokens, and Admin routes.

3. **Database Architecture:**
   - PostgreSQL schema with Row Level Security (RLS) policies.
   - Tables: `profiles`, `accounts`, `categories`, `transactions`, `saving_goals`, `upcoming_expenses`, `loans`, `transfers`, `sync_operations`, `notification_devices`, `admin_audit_logs`.
   - Strict financial rule: All monetary amounts stored as `BIGINT` minor units (paise, e.g., ₹250.00 = 25000).

4. **Admin Panel Architecture:**
   - Stack: React + TypeScript + Vite + Tailwind/Modern CSS + Lucide icons.
   - Features: Protected Admin Login, Overview Metrics (users, verification status, API health, DB connectivity, sync failures), User Management, Health & Diagnostics, Audit Logs.

5. **Windows Automation Scripts:**
   - `START_ALL.bat`: Launches Backend & Admin servers in separate consoles with PID tracking and port check.
   - `STOP_ALL.bat`: Reliably stops project services.
   - `INSTALL_ANDROID_USB.bat`: Checks ADB, detects devices/authorization, builds debug APK via Gradle wrapper, installs to phone, launches app.
   - `RUN_AND_INSTALL.bat`: Single master launcher.

---

## 3. Immediate Action Plan

Proceed to write:
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/TASK_TRACKER.md`
- `docs/REQUIREMENTS_TRACEABILITY.md`
- Core architecture and documentation files.
Then implement Database migrations, Backend API, Android application, Admin panel, Windows automation scripts, and test suites.
