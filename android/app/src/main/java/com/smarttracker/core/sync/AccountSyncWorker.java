package com.smarttracker.core.sync;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Background WorkManager worker executing periodic data synchronization.
 */
public class AccountSyncWorker extends Worker {

    public AccountSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        SyncManager.getInstance(getApplicationContext()).triggerSync();
        return Result.success();
    }
}
