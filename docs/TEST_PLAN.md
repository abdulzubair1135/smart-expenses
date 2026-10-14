# SMART EXPENSES TRACKER - COMPREHENSIVE TEST PLAN

## 1. Scope of Verification
This test plan covers automated and physical device testing across:
1. **Authentication:** Registration, duplicate email rejection, login, invalid password, logout, forgot password OTP (`1234` in dev mode), rate limiting, single-use token consumption, and production guardrail rejection.
2. **Database & RLS Security:** Cross-user data isolation, trip isolation, audit log protection.
3. **Admin Monitoring:** Overview metrics calculation (real database queries, zero hardcoded values), online/offline heartbeat detection (120s window), activity events timeline (zero financial details).
4. **Backup & Export:** Verified JSON export artifact, checksum validation, operation recording.
5. **Android Client:** Java/XML compilation, Room SQLite local-first persistence, physical device deployment.

---

## 2. Test Execution Commands
- **Backend API Tests:** `npm test` inside `backend/` directory.
- **Backend Build:** `npm run build` (`tsc`).
- **Admin Frontend Build:** `npm run build` inside `admin/` directory.
- **Android Compilation:** Gradle `assembleDebug`.
- **Physical Device Install:** `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`.
