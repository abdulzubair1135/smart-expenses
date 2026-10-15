package com.smarttracker.core.monitoring;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.JsonObject;
import com.smarttracker.data.local.AppDatabase;
import com.smarttracker.data.local.dao.MonitoringEventDao;
import com.smarttracker.data.local.entity.MonitoringEventEntity;
import com.smarttracker.data.remote.BackendApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reliable WorkManager worker for uploading persistent Room monitoring events
 * with exponential backoff, bounded retries, and offline queue preservation.
 */
public class MonitoringUploadWorker extends Worker {

    private static final String TAG = "MonitoringUploadWorker";
    private static final int BATCH_SIZE = 20;

    public MonitoringUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
        MonitoringEventDao dao = db.monitoringEventDao();

        // 1. Fetch batch of pending events
        List<MonitoringEventEntity> pending = dao.getPendingEvents(BATCH_SIZE);
        if (pending == null || pending.isEmpty()) {
            return Result.success();
        }

        List<String> eventIds = new ArrayList<>();
        for (MonitoringEventEntity e : pending) {
            eventIds.add(e.id);
        }

        // 2. Upload synchronously using CountDownLatch
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        BackendApiClient.getInstance(getApplicationContext()).uploadMonitoringEvents(pending, new BackendApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                success.set(true);
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                Log.w(TAG, "Monitoring events upload failed: " + errorMessage);
                success.set(false);
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(15, TimeUnit.SECONDS);
            if (!completed) {
                dao.incrementRetryCount(eventIds);
                return Result.retry();
            }
        } catch (InterruptedException e) {
            dao.incrementRetryCount(eventIds);
            return Result.retry();
        }

        if (success.get()) {
            dao.markEventsSynced(eventIds);
            // Prune synced events older than 7 days
            long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            dao.pruneOldSyncedEvents(sevenDaysAgo);
            // Cap maximum queue capacity to 500 items to conserve device storage
            dao.pruneExcessCapacity(500);
            return Result.success();
        } else {
            dao.incrementRetryCount(eventIds);
            return Result.retry();
        }
    }
}
