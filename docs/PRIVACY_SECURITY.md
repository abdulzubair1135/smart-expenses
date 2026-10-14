# SMART EXPENSES TRACKER - PRIVACY & SECURITY POLICY

## 1. Zero Knowledge for Administrators
Financial details belong solely to the user.
- **Amounts & Balances:** Encrypted in transit (HTTPS / TLS 1.3), isolated by PostgreSQL Row Level Security (RLS).
- **Admin Visibility:** Administrators have zero visibility into personal spending habits, account balances, merchant names, or trip items.

---

## 2. Row Level Security (RLS) Enforcement
Every user-owned table enforces RLS:
```sql
CREATE POLICY "user_data_isolation" ON transactions
    FOR ALL USING (auth.uid() = user_id);
```
- No user can read, insert, update, or delete another user's financial records.
- Anonymous requests receive empty sets or `401 Unauthorized`.

---

## 3. Secret Credential Management
- Supabase `service_role` key and database passwords are **strictly confined to backend server environment variables**.
- The Android client APK and React admin web bundle **NEVER** package secret keys.
- Client applications utilize restricted tokens with least privilege.

---

## 4. Local-First Data Security
- Android Room SQLite stores user data locally with Android Keystore-backed tokens.
- Upon logout, active local session caches and in-memory tokens are completely cleared.
