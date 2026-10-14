# SMART EXPENSES TRACKER - AUTHENTICATION & RECOVERY SPECIFICATION

## 1. Authentication Architecture
The system employs a production-grade authentication flow rooted in **Supabase Auth** with local session caching and token validation:
- **Client (Android):** JWT Bearer token stored in encrypted `SharedPreferences` via `SessionManager`.
- **Backend (Express):** Token validation middleware (`authenticateUser`) extracting user claims (`id`, `email`, `role`).
- **Cloud Database:** PostgreSQL Row Level Security (RLS) policies checking `auth.uid() = user_id`.

---

## 2. Registration Flow
1. **User Input:** Full Name, Email, Password (min 6 characters), Confirm Password, and explicit Terms & Privacy Policy acceptance checkbox (`termsAccepted: true`).
2. **Email Normalization:** Lowercased and trimmed (`user@example.com`).
3. **Uniqueness Enforcement:** Checked against Supabase Auth identity table and profile store. Duplicate registration returns `409 Conflict` with generic message preventing account enumeration.
4. **Initial Account Setup:** Creates profile linked to identity and initializes primary financial accounts (`Cash`, `Bank`, `UPI`) with zero opening balances.

---

## 3. Login Flow
1. **Credentials:** Email and Password.
2. **Session Generation:** Server verifies credentials, issues signed JWT token, updates `last_login_at` in profile, and records `LOGIN_SUCCESS` event in `activity_events`.
3. **Failure Handling:** Failed attempts return `401 Unauthorized` with `LOGIN_FAILURE` event logged. Plaintext passwords or specific user existence clues are never disclosed.
4. **Session Termination (Logout):** Invalidates active session, clears local credentials, and logs `LOGOUT` event.

---

## 4. Password Recovery & OTP Specification

### 4.1 Controlled Development Mode (`development_fixed_otp`)
- **Trigger:** Configured via `PASSWORD_RESET_MODE=development_fixed_otp`.
- **Production Guardrail:** Strictly rejected if `NODE_ENV === 'production'`. The server aborts with `403 Forbidden` if fixed OTP is attempted in production.
- **Fixed OTP Value:** `1234` (for college evaluation and offline testing).
- **Security Constraints:**
  - Expiry: 5 minutes (300 seconds).
  - Rate Limiting: Maximum 3 verification attempts before permanent lock.
  - Flow Binding: OTP must be verified before password update; verification issues a single-use crypto `resetToken`.
  - Single-Use: Once used, the reset record is immediately marked `is_used = true` and rejected for any subsequent attempt.

### 4.2 Production Recovery Flow
- When `PASSWORD_RESET_MODE=production_supabase`:
  - Triggers Supabase Auth native password recovery (`supabase.auth.resetPasswordForEmail`).
  - Sends cryptographically secure magic link or 6-digit email OTP.
  - Verifies reset token before executing `supabase.auth.updateUser`.

---

## 5. Security & Privacy Safeguards
- Plaintext passwords are **NEVER** stored in the database.
- OTPs, access tokens, and passwords are **NEVER** included in `activity_events` metadata or application logs.
