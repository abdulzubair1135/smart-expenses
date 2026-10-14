# AGENTS.md - Developer & Agent Guidelines

## Strict Implementation Rules
1. **Language Policy:** The Android mobile application MUST use **Pure Java and XML**. Kotlin files (`.kt`) or Kotlin-only modules are strictly prohibited.
2. **Financial Rules:** All financial amounts must be processed as integer minor units (`long` in Java, paise in INR). Never use `float` or `double` for balances or transaction storage.
3. **Local-First Architecture:** Transactions must always save to Room local storage first, update UI state immediately, and queue for background sync with idempotency keys.
4. **Data Isolation:** User data must be strictly isolated using Row Level Security (RLS) in PostgreSQL and token validation in Express middleware.
5. **No Fake Data Claims:** Never generate fake statistics or claim cloud deployment/physical device installation without real, verified filesystem or device output.
