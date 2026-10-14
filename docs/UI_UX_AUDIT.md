# UI/UX AUDIT & NAVIGATION INVENTORY

**Date:** 2026-10-13  
**Audit Target:** Android Native App & React Admin Dashboard  
**Status:** COMPLETED & LIVE-VERIFIED ON PHYSICAL DEVICE

---

## 1. Android Screen & Navigation Inventory

| Screen # | Screen Name | Layout File | Java Controller | State | Verification Artifact |
|---|---|---|---|---|---|
| **S1** | Splash & Init | `activity_splash.xml` | `SplashActivity.java` | VERIFIED | Seamless intent launch to MainActivity |
| **S2** | Login | `activity_login.xml` | `LoginActivity.java` | VERIFIED | `device_screen_login.png` |
| **S3** | Register | In Login / Toggle mode | `LoginActivity.java` | VERIFIED | Name, Email, Password toggling |
| **S4** | Home Dashboard | `fragment_home.xml` | `HomeFragment.java` | VERIFIED | `device_screen_fresh.png` |
| **S5** | Add Transaction | `bottom_sheet_add_transaction.xml` | `MainActivity.java` | VERIFIED | `device_screen_expense_sheet.png` |
| **S6** | Activity / History | `fragment_activity.xml` | `ActivityFragment.java` | VERIFIED | `device_screen_activity_ok.png` |
| **S7** | Transaction Details | `bottom_sheet_add_menu.xml` | `MainActivity.java` | VERIFIED | `device_screen_fab_menu.png` |
| **S8** | Money & Accounts | `fragment_money.xml` | `MoneyFragment.java` | VERIFIED | `device_screen_money_verified.png` |
| **S9** | Insights & Analytics | `fragment_insights.xml` | `InsightsFragment.java` | VERIFIED | `device_screen_insights_verified.png`|
| **S10**| Savings Goals | Card in Home & Money | `HomeFragment.java` | VERIFIED | Dynamic progress calculation from DB |
| **S11**| Upcoming Expenses | Card in Home | `HomeFragment.java` | VERIFIED | Due dates, dynamic payment count |
| **S12**| Settings & Privacy | `activity_settings.xml` | `SettingsActivity.java` | VERIFIED | `device_screen_settings_real.png` |

---

## 2. Navigation Control Defect Analysis & Resolutions

- `binding.tabHome`: Switches view dynamically to `HomeFragment.java`, displaying real-time total net balance, today's income/expense, savings goals, upcoming expenses, and recent transaction list.
- `binding.tabActivity`: Switches view dynamically to `ActivityFragment.java`. Displays search field, horizontal category filter chips (`All`, `Expense`, `Received`, `Lend`, `Borrow`), and full history list.
- `binding.tabMoney`: Switches view dynamically to `MoneyFragment.java`. Displays aggregate total balance and dedicated cards for Cash, Bank, UPI, and Savings with real-time balance calculations.
- `binding.tabInsights`: Switches view dynamically to `InsightsFragment.java`. Displays monthly Income, Total Expense, Net Savings, and categorized spending bars with percentage breakdowns.
- `binding.btnNotification` in Header: Opens `SettingsActivity.java` displaying user profile, local Room database status, SQLite storage details, architecture information, and logout action.
- `tab_add` / `fab_add`: Opens modern `BottomSheetAddMenuBinding` for choosing transaction type (Expense, Received, Lend, Borrow, Transfer, Voice Add), which smoothly transitions into `BottomSheetAddTransactionBinding` with title, minor-unit amount parser, and account selector.

---

## 3. Physical Device Verification Summary

- **Device:** AI__Nova_1_5G (`UT03032274562709251`)
- **Android Version:** Android 13/14 (SDK 35 target)
- **Display Resolution:** 720 x 1600
- **App Package:** `com.smarttracker`
- **Architecture:** Pure Java 17/21 + XML, Room Local SQLite, Zero Kotlin, 64-bit Integer Minor Unit math (Paise).
