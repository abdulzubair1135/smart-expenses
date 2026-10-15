package com.smarttracker.core.monitoring;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.smarttracker.data.local.AppDatabase;
import com.smarttracker.data.local.dao.MonitoringEventDao;
import com.smarttracker.data.local.entity.MonitoringEventEntity;
import com.smarttracker.data.session.SessionManager;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Central Android telemetry, diagnostic and crash monitoring service.
 * Safely persists normalized events into Room before scheduling reliable WorkManager delivery.
 */
public class MonitoringManager {

    private static final String TAG = "MonitoringManager";
    private static final String PREF_NAME = "smart_expenses_monitoring";
    private static final String KEY_INSTALLATION_ID = "key_installation_id";

    private static MonitoringManager instance;
    private final Context context;
    private final AppDatabase database;
    private final SessionManager sessionManager;
    private final String installationId;
    private final String appVersion;
    private final String appBuild;

    private MonitoringManager(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getDatabase(this.context);
        this.sessionManager = SessionManager.getInstance(this.context);

        SharedPreferences prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_INSTALLATION_ID, null);
        if (id == null) {
            id = "inst-" + UUID.randomUUID().toString();
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply();
        }
        this.installationId = id;

        String vName = "1.0.0";
        String vCode = "1";
        try {
            vName = this.context.getPackageManager().getPackageInfo(this.context.getPackageName(), 0).versionName;
            vCode = String.valueOf(this.context.getPackageManager().getPackageInfo(this.context.getPackageName(), 0).versionCode);
        } catch (Exception ignored) {
        }
        this.appVersion = vName;
        this.appBuild = vCode;

        initCrashHandler();
    }

    public static synchronized MonitoringManager getInstance(Context context) {
        if (instance == null) {
            instance = new MonitoringManager(context);
        }
        return instance;
    }

    public static synchronized void init(Context context) {
        getInstance(context);
    }

    public String getInstallationId() {
        return installationId;
    }

    // =========================================================================
    // EVENT LOGGING APIS
    // =========================================================================

    public void recordError(String errorCode, String message, Throwable throwable, String screenName, String operationName) {
        logEvent("APPLICATION_ERROR", "ERROR", "ANDROID", errorCode, message, throwable, screenName, operationName, null);
    }

    public void recordUiFailure(String screenName, String operationName, String message) {
        logEvent("UI_ERROR", "WARNING", "UI", "ERR_UI_FAILURE", message, null, screenName, operationName, null);
    }

    public void recordBackgroundFailure(String workerName, String message, Throwable throwable) {
        logEvent("BACKGROUND_ERROR", "ERROR", "BACKGROUND", "ERR_WORKER_FAILURE", message, throwable, null, workerName, null);
    }

    public void recordNetworkFailure(String endpoint, int httpCode, String errorMessage) {
        String severity = (httpCode >= 500 || httpCode == 0) ? "ERROR" : "WARNING";
        String errCode = "HTTP_" + httpCode;
        logEvent("NETWORK_FAILURE", severity, "SYNC", errCode, errorMessage, null, null, endpoint, null);
    }

    public void recordPerformanceWarning(String operationName, long durationMs) {
        if (durationMs > 2000) {
            String msg = "Slow operation detected: " + operationName + " took " + durationMs + "ms";
            logEvent("PERFORMANCE_WARNING", "WARNING", "ANDROID", "WARN_SLOW_OP", msg, null, null, operationName, null);
        }
    }

    public void recordSyncEvent(String status, String details, boolean isError) {
        String severity = isError ? "ERROR" : "INFO";
        logEvent("SYNC_EVENT", severity, "SYNC", isError ? "ERR_SYNC" : "SYNC_SUCCESS", details, null, null, "AccountSync", null);
    }

    public void recordCrash(Throwable fatalException) {
        String stack = Sanitizer.extractStackTrace(fatalException);
        String msg = fatalException != null ? Sanitizer.sanitize(fatalException.getMessage()) : "Fatal uncaught crash";

        MonitoringEventEntity crashEvent = new MonitoringEventEntity(
                UUID.randomUUID().toString(),
                "CRASH",
                "CRITICAL",
                "ANDROID",
                "FATAL_UNCAUGHT_EXCEPTION",
                msg != null && !msg.isEmpty() ? msg : "Fatal exception",
                stack,
                "CrashHandler",
                "processTermination",
                appVersion,
                appBuild,
                Build.VERSION.RELEASE,
                Build.MANUFACTURER + " " + Build.MODEL,
                sessionManager.getUserId(),
                installationId,
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                "PENDING",
                0,
                "{}"
        );

        // Blocking synchronous insert for crash handler before process termination
        try {
            database.monitoringEventDao().insert(crashEvent);
        } catch (Throwable ignored) {
        }
    }

    // =========================================================================
    // CORE ASYNCHRONOUS PIPELINE
    // =========================================================================

    private void logEvent(String eventType, String severity, String source, String errorCode,
                          String rawMessage, Throwable throwable, String screenName,
                          String operationName, String metadataJson) {
        try {
            String cleanMsg = Sanitizer.sanitize(rawMessage);
            String stack = Sanitizer.extractStackTrace(throwable);
            String correlationId = "corr-" + UUID.randomUUID().toString().substring(0, 8);
            String userId = sessionManager.isLoggedIn() ? sessionManager.getUserId() : null;

            MonitoringEventEntity entity = new MonitoringEventEntity(
                    UUID.randomUUID().toString(),
                    eventType,
                    severity,
                    source,
                    errorCode != null ? errorCode : "UNKNOWN",
                    cleanMsg != null ? cleanMsg : "",
                    stack,
                    screenName,
                    operationName,
                    appVersion,
                    appBuild,
                    Build.VERSION.RELEASE,
                    Build.MANUFACTURER + " " + Build.MODEL,
                    userId,
                    installationId,
                    correlationId,
                    System.currentTimeMillis(),
                    "PENDING",
                    0,
                    metadataJson != null ? metadataJson : "{}"
            );

            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    database.monitoringEventDao().insert(entity);
                    scheduleUploadWorker();
                } catch (Throwable e) {
                    Log.e(TAG, "Failed to persist monitoring event to Room", e);
                }
            });
        } catch (Throwable t) {
            // Never allow monitoring failure to crash the calling application
            Log.e(TAG, "MonitoringManager internal error", t);
        }
    }

    private void scheduleUploadWorker() {
        try {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            OneTimeWorkRequest uploadRequest = new OneTimeWorkRequest.Builder(MonitoringUploadWorker.class)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .build();

            WorkManager.getInstance(context).enqueue(uploadRequest);
        } catch (Throwable ignored) {
        }
    }

    private void initCrashHandler() {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                recordCrash(throwable);
            } catch (Throwable ignored) {
            } finally {
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }
            }
        });
    }
}
