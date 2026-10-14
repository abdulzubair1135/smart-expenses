package com.smarttracker.app;

import android.app.Application;
import com.smarttracker.data.local.AppDatabase;

public class SmartExpensesApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Warm up Room Database
        AppDatabase.getDatabase(this);
    }
}
