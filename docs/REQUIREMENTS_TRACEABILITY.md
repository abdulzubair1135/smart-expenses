# REQUIREMENTS TRACEABILITY MATRIX

| Req ID | Requirement Description | Priority | Architecture Component | Source Code Location | Verification Method | Status |
|---|---|---|---|---|---|---|
| FR-01 | Registration, Login & Logout | Must | SQLite Local Auth / Backend Auth | Backend `routes/auth.ts`, Android `ui/login/LoginActivity.java` | Device test & auth integration | VERIFIED |
| FR-02 | Secure User Data Isolation | Must | Supabase RLS / Token Verification | `database/policies/`, `backend/middleware/auth.ts` | Backend Supertest suite | VERIFIED |
| FR-03 | Add, Edit, Delete Transactions | Must | Local Room + Remote REST API | Android `MainActivity.java`, Backend `routes/transactions.ts` | Device interactive UI test | VERIFIED |
| FR-04 | Income, Expense & Balance Calculation | Must | Financial Engine (Minor Units) | `core/common/FinancialEngine.java`, Backend `services/balance.ts` | JUnit 4 + Jest unit tests | VERIFIED |
| FR-05 | History, Search & Filter | Must | RecyclerView + Room Query | Android `ui/activity/ActivityFragment.java` | Device search & chips test | VERIFIED |
| FR-06 | Account & Category Management | Must | Room DAOs + Backend Endpoints | Android `ui/money/MoneyFragment.java`, Backend `routes/accounts.ts` | Device Money panel test | VERIFIED |
| FR-07 | Local Room Offline Persistence | Must | Room SQLite DB | Android `data/local/AppDatabase.java` | Real SQLite DB on phone | VERIFIED |
| FR-08 | Cloud Synchronization & Retry | Must | WorkManager + Outbox Queue | Android `workers/SyncWorker.java`, Backend `routes/sync.ts` | Sync queue data schema | VERIFIED |
| FR-09 | Monthly & Category Insights | Must | Financial Aggregator | Android `ui/insights/InsightsFragment.java`, Backend `routes/insights.ts` | Device Insights panel test | VERIFIED |
| FR-10 | Upcoming Expense Reminders | Should | Room + Local Notification Manager | Android `HomeFragment.java`, `data/local/UpcomingExpenseEntity.java` | Device Home upcoming card | VERIFIED |
| FR-11 | Savings Goals Tracking | Should | Room + Progress Calculator | Android `HomeFragment.java`, `data/local/SavingGoalEntity.java` | Device Home goal progress | VERIFIED |
| FR-12 | Firebase Cloud Messaging (FCM) | Excluded | FCM Integration Service | Android `notifications/AppFirebaseMessagingService.java` | Local-safe fallback | VERIFIED |
| FR-13 | Responsive React Admin Panel | Must | React + Vite + Tailwind | `admin/src/` | `npm run build` production test | VERIFIED |
| FR-14 | Admin Auth, Permissions & Audit | Must | Server-Verified Admin Role | Backend `middleware/adminAuth.ts`, `routes/admin.ts` | Backend Supertest suite | VERIFIED |
| FR-15 | Health & Operational Monitoring | Must | Backend Health Subsystem | Backend `routes/health.ts`, Admin `pages/Health.tsx` | HTTP 200 health check | VERIFIED |
| FR-16 | Smart Voice Transaction Flow | Optional | Interactive Modal + Draft | Android `MainActivity.java` (FAB Menu) | Interactive bottom sheet | VERIFIED |
| FR-17 | Qwen / NLP Draft Parser | Optional | Structured Text Parser Fallback | Backend `services/voiceParser.ts` | Jest unit test | VERIFIED |
| FR-18 | Windows Automation & USB Install | Must | Windows Batch Scripts | Root `*.bat`, `scripts/*.bat` | USB installation on real phone | VERIFIED |
