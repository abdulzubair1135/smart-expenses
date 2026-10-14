# COLLEGE PRESENTATION & VIVA PREPARATION GUIDE

### Q1: What is Smart Expenses Tracker?
**Answer:** It is a full-stack, offline-first personal financial management application designed with an Android frontend (pure Java + XML), an Express/Node.js TypeScript backend, a PostgreSQL database (Supabase with Row Level Security), and a React admin dashboard.

### Q2: Why did you choose Java and XML instead of Kotlin or Flutter?
**Answer:** Java with native XML layouts provides maximum architectural clarity and compatibility with Android system internals without additional runtime overhead. It adheres strictly to the classic Android SDK standards and core computer science fundamentals.

### Q3: What is Room and why is it used?
**Answer:** Room is an Android Jetpack persistence library that provides an abstraction layer over SQLite. It offers compile-time verification of SQL statements, reduces boilerplate code, and supports observable data patterns with LiveData.

### Q4: How does the application support offline functionality?
**Answer:** It follows a **Local-First Architecture**. When the user creates a transaction, it is validated and immediately saved into the local Room database, updating the UI instantaneously. The operation is queued with an idempotency key and synchronized with the cloud backend in the background using Android WorkManager.

### Q5: How do you prevent floating-point calculation errors in financial totals?
**Answer:** All monetary figures are stored and calculated as integer minor units (paise in INR, e.g., ₹250.00 = 25000 paise). This completely eliminates floating-point rounding errors and precision issues common with standard double/float arithmetic.

### Q6: What is Row Level Security (RLS) in PostgreSQL?
**Answer:** RLS ensures that database engine policies restrict row access based on the authenticated user's ID (`auth.uid() = user_id`). Even if a client executes a query, the PostgreSQL engine itself guarantees that User A cannot read or modify User B's financial records.

### Q7: Why is an Account Transfer not treated as income or expense?
**Answer:** A transfer moves funds between two accounts belonging to the same user (e.g. from Bank to Cash). The source account decreases while the destination account increases by the exact same amount. The net change to overall net worth is zero.

### Q8: What does the React Admin Panel do?
**Answer:** The Admin Panel monitors system health, active users, account verification status, sync issues, and security audit logs using server-verified administrator authentication. Importantly, following privacy-by-design principles, administrators do not have arbitrary access to view private user transaction descriptions or balances.
