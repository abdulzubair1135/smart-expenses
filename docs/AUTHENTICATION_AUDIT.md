# AUTHENTICATION & SESSION MANAGEMENT AUDIT

**Target:** Smart Expenses Tracker (Android Client + Express Backend + React Admin)  
**Date:** 2026-10-13  
**Audit Status:** DEFECTS IDENTIFIED — REMEDIATION IN PROGRESS

---

## 1. Executive Summary

A comprehensive audit of authentication, session management, registration, and logout flows was performed across the Android app (`android/`), Backend API (`backend/`), and Admin Portal (`admin/`). 

### Critical Defects Identified:
1. **Broken Logout Loop:** In `SettingsActivity.java`, clicking Logout displayed a Toast and redirected via `Intent(SettingsActivity.this, SplashActivity.class)`. However, `SplashActivity.java` unconditionally forwarded to `MainActivity.java` after 1.2s without checking authentication state. Consequently, logging out immediately brought the user right back to the authenticated dashboard!
2. **Missing Session Manager:** No dedicated `SessionManager.java` existed on Android. Tokens, user identity, and session expiry were not saved into `SharedPreferences` or Room user entities.
3. **No Dedicated Signup Activity:** `LoginActivity.java` merely triggered a Toast on clicking "Register", with no fields for Full Name, Confirm Password, or Consent.
4. **Missing Legal Consent Enforcement:** Terms & Conditions and Privacy Policy were not present as individual, clickable interactive destinations during registration, and there was no mandatory unchecked consent checkbox.
5. **No Legal Document Screens:** No `TermsActivity.java` or `PrivacyPolicyActivity.java` existed in the Android application.
6. **Backend Missing Dedicated Registration & Logout Endpoints:** `backend/src/routes/auth.ts` supported only `/login` and `/profile`. It lacked `/register` with terms consent logging and `/logout` for token invalidation.

---

## 2. Root Cause Analysis

### A. Android Client
- **Splash Screen Bypass:** `SplashActivity` lacked a session check `SessionManager.getInstance(this).isLoggedIn()`.
- **Settings Activity Logout Defect:** `SettingsActivity` did not show a confirmation dialog ("Cancel" vs "Logout"), did not clear session state, and redirected to `SplashActivity` rather than `LoginActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`.
- **Back Stack Vulnerability:** Because clear-task flags and finish-affinity were omitted, pressing Back after logout could expose previous authenticated Activities.
- **Data Isolation:** User ID was hardcoded to a single demo user rather than dynamically bound to the authenticated user's ID in SQLite Room transactions.

### B. Backend API
- `POST /api/v1/auth/login` created on-the-fly demo profiles, but did not validate password matching or track logout invalidations.
- Consent timestamps and policy version metadata (`termsVersion: '2026.1'`, `privacyVersion: '2026.1'`) were not tracked in user profile stores.

---

## 3. Detailed Remediation Plan

1. **Implement `SessionManager.java`:**
   - SharedPreferences-based secure local session storage.
   - Keys: `KEY_IS_LOGGED_IN`, `KEY_USER_TOKEN`, `KEY_USER_ID`, `KEY_USER_NAME`, `KEY_USER_EMAIL`, `KEY_TERMS_ACCEPTED_AT`.
   - Methods: `saveLoginSession()`, `isLoggedIn()`, `getUserDetails()`, `clearSession()`.

2. **Fix `SplashActivity.java`:**
   - Query `SessionManager.isLoggedIn()`.
   - If `true` -> Navigate to `MainActivity`.
   - If `false` -> Navigate to `LoginActivity`.

3. **Implement Full `RegisterActivity.java` & `activity_register.xml`:**
   - Fields: Full Name, Email, Password, Confirm Password.
   - Form state retention: Saved across lifecycle and back navigation.
   - Checkbox: Initially unchecked `[ ] I agree to the Terms & Conditions and acknowledge the Privacy Policy.`
   - Interactive Clickable Spans:
     - "Terms & Conditions" -> Opens `TermsActivity.class`.
     - "Privacy Policy" -> Opens `PrivacyPolicyActivity.class`.
   - Input validation: Email format, 6+ char password, matching passwords, consent requirement.

4. **Implement Legal Document Screens:**
   - `TermsActivity.java` + `activity_terms.xml`: 20 comprehensive sections tailored strictly to the application's real capabilities (personal finance tool, local-first Room, no fake banking claims).
   - `PrivacyPolicyActivity.java` + `activity_privacy_policy.xml`: 20 comprehensive sections tailored to local SQLite storage, camera/mic permissions, and verified data handling.

5. **Fix Logout Completely in `SettingsActivity.java`:**
   - On Logout click: Display MaterialAlertDialog ("Cancel" / "Logout").
   - "Cancel": Dismiss dialog, preserve session.
   - "Logout": Clear `SessionManager`, invoke backend `/api/v1/auth/logout`, launch `LoginActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`, call `finishAffinity()`.
   - Back button on Login: Exits the app; previous dashboard is inaccessible.

6. **Enhance Backend Auth Endpoints:**
   - `POST /api/v1/auth/register` with validation, duplicate email check, and consent logging.
   - `POST /api/v1/auth/logout` with token invalidation.
