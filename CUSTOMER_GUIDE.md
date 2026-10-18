# 🚀 SMART EXPENSES TRACKER - COMPLETE PROJECT PACKAGE
### Ultimate Offline-First Personal Finance System (Android App + Web Admin + Cloud Backend + Database)

---

## 📦 What's Inside This Package

```text
📁 SMART-EXPENSES/
├── 📁 APK/
│   └── 📱 SmartExpenses-v1.0.apk         <-- Ready-to-Install Android App
├── 📁 android/                            <-- Pure Java & XML Android Studio Source Code
├── 📁 admin/                              <-- React + Vite Admin Control Panel (Website)
├── 📁 backend/                            <-- Node.js + Express + TypeScript Backend API
├── 📁 database/                           <-- Supabase / PostgreSQL Database Schemas & Migrations
├── 📄 START_ALL.bat                       <-- 1-Click Launch Local Backend & Admin Panel
├── 📄 STOP_ALL.bat                        <-- 1-Click Stop All Local Services
├── 📄 INSTALL_ANDROID_USB.bat             <-- 1-Click Install APK to Phone via USB Cable
├── 📄 RUN_AND_INSTALL.bat                 <-- Master 1-Click Workflow (Start + Install)
└── 📄 CUSTOMER_GUIDE.md                   <-- Setup & Usage Guide (This File)
```

---

## 📱 Option 1: Mobile App Direct Installation (Sabse Aasan)

### Method A: Direct Phone Install (No PC needed)
1. Phone me `APK/SmartExpenses-v1.0.apk` file bhejein (WhatsApp, Drive ya USB se).
2. Phone me APK file par tap karein aur **Install** dabayein.
3. App open karein — app 100% ready hai!

### Method B: USB 1-Click Installer (PC se)
1. Phone ko USB cable se PC se connect karein aur **USB Debugging** ON karein.
2. Root folder me **`INSTALL_ANDROID_USB.bat`** par double click karein.
3. APK automatically phone me install hokar open ho jayegi!

---

## 💻 Option 2: Running Website & Servers Locally

### Steps to Run:
1. Make sure **Node.js (v18+)** is installed on your computer.
2. Root folder me **`START_ALL.bat`** par double click karein.
3. Dono services automatically start ho jayengi:
   * **Admin Control Panel (Website):** [http://localhost:5173](http://localhost:5173)
   * **Backend API:** [http://localhost:4000](http://localhost:4000)
4. Admin panel me login karne ke liye Admin Key:  
   🔑 **`smart-expenses-admin-secret-2026`**
5. Jab kaam ho jaye, toh **`STOP_ALL.bat`** par double-click karke band karein.

---

## ☁️ Option 3: Live Cloud Setup (Already Configured)

* **Cloud Backend:** `https://smart-expenses-kuob.onrender.com` (Live on Render)
* **Cloud Database:** Supabase PostgreSQL (`dymorpzwrmvfpubgsgqb`)
* **Financial Data Security:**
  * 100% Local-First (Room SQLite) — offline me saare transactions save rehte hain.
  * Jaise hi internet connect hota hai, background sync cloud par save kar deta hai.
  * Integer minor units (Paise) — zero calculation error.

---

## 🌐 Deploying Admin Panel to Vercel (Online Website):
1. [vercel.com/new](https://vercel.com/new) par jayein.
2. Project import karein.
3. Root Directory me **`admin`** select karein.
4. **Deploy** dabayein — aapko aapki live website ka link (jaise `smart-expenses.vercel.app`) mil jayega!

---
© 2026 Smart Expenses Tracker. All rights reserved.
