# Smart Expenses Tracker

> Simple • Secure • Offline First

A production-grade, full-stack personal finance and expenses tracker built with **Android (Java + XML)**, **Node.js/Express (TypeScript)**, **PostgreSQL (Supabase)**, and a modern **React + Vite Admin Portal**.

---

## Architecture Overview

```
+------------------------------------+
| Android Mobile App (Java + XML)   |  <- Local Room SQLite DB (Offline First)
+-----------------+------------------+
                  | REST API / JSON
                  v
+-----------------+------------------+
| Backend Service (Node/Express/TS)  |  <- Financial Engine (Integer minor units: paise)
+--------+------------------+--------+
         |                  |
         v                  v
+--------+-------+  +-------+--------+
| Supabase / PG  |  | React Admin    |
| (RLS Security) |  | Dashboard (Web)|
+----------------+  +----------------+
```

---

## Technology Stack

- **Mobile App:** Pure Java 17/21, Android XML Layouts, Room Database, Retrofit 2, OkHttp 3, Material 3, ViewBinding.
- **Backend:** Node.js v25, TypeScript, Express, Zod, Helmet, CORS, Jose/JWT.
- **Database:** PostgreSQL with Row Level Security (RLS) policies and migrations.
- **Admin Dashboard:** React 18, TypeScript, Vite, Tailwind CSS, Lucide icons.
- **Automation:** Windows batch scripts (`START_ALL.bat`, `STOP_ALL.bat`, `INSTALL_ANDROID_USB.bat`, `RUN_AND_INSTALL.bat`).

---

## Quick Start (Windows)

1. **Launch Services:** Double-click `START_ALL.bat` to launch the backend API on port `4000` and the React admin portal on port `5173`.
2. **Install APK to Phone:** Connect your Android phone via USB with USB Debugging enabled, then double-click `INSTALL_ANDROID_USB.bat`.
3. **Stop Services:** Double-click `STOP_ALL.bat` to stop all running development processes.

---

## Core Financial Engine & Accuracy

- **Integer Minor Units:** All monetary values are strictly stored as paise (`BIGINT` / `long`) to guarantee zero floating-point calculation inaccuracies.
- **Balance Formula:**
  $$\text{Calculated Balance} = \text{Opening Balance} + \sum \text{Received} + \sum \text{Borrow} - \sum \text{Expense} - \sum \text{Lend} + \Delta\text{Transfers}$$
