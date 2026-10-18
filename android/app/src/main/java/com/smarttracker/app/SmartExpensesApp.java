package com.smarttracker.app;

import android.app.Application;
import com.smarttracker.core.monitoring.MonitoringManager;
import com.smarttracker.data.local.AppDatabase;

public class SmartExpensesApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Warm up Room Database
        AppDatabase.getDatabase(this);
        // Initialize Global Error Telemetry and Crash Monitoring
        MonitoringManager.init(this);
        // Schedule 30-minute periodic expense reminders
        com.smarttracker.notifications.ExpenseReminderWorker.schedulePeriodicReminder(this);
    }
}
