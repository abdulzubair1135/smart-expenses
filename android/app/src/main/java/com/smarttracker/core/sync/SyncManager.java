package com.smarttracker.core.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.smarttracker.core.monitoring.MonitoringManager;
import com.smarttracker.data.local.AppDatabase;
import com.smarttracker.data.local.dao.AccountDao;
import com.smarttracker.data.local.dao.CategoryDao;
import com.smarttracker.data.local.dao.SavingGoalDao;
import com.smarttracker.data.local.dao.TransactionDao;
import com.smarttracker.data.local.dao.TripDao;
import com.smarttracker.data.local.dao.UpcomingExpenseDao;
import com.smarttracker.data.local.entity.AccountEntity;
import com.smarttracker.data.local.entity.CategoryEntity;
import com.smarttracker.data.local.entity.SavingGoalEntity;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.data.local.entity.TripEntity;
import com.smarttracker.data.local.entity.UpcomingExpenseEntity;
import com.smarttracker.data.remote.BackendApiClient;
import com.smarttracker.data.session.SessionManager;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production-grade Local-First Synchronization Engine.
 * Safely merges multi-entity cloud records (accounts, transactions, categories, trips, goals)
 * with the local Room database, honoring user isolation, conflict rules and idempotency.
 */
public class SyncManager {

    private static final String TAG = "SyncManager";
    private static final String PREF_NAME = "smart_expenses_sync";
    private static final String KEY_LAST_SYNC_TIME = "key_last_sync_time";
    private static final String SYNC_PERIODIC_WORK = "AccountSyncPeriodicWork";

    public enum SyncStatus {
        IDLE,
        SYNCING,
        SUCCESS,
        ERROR,
        OFFLINE
    }

    private static SyncManager instance;
    private final Context context;
    private final AppDatabase database;
    private final SessionManager sessionManager;
    private final BackendApiClient apiClient;
    private final SharedPreferences prefs;
    private final Handler mainHandler;

    private final MutableLiveData<SyncStatus> syncStatusLive = new MutableLiveData<>(SyncStatus.IDLE);
    private final MutableLiveData<Long> lastSyncTimeLive = new MutableLiveData<>(0L);
    private final AtomicBoolean isSyncing = new AtomicBoolean(false);

    private SyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getDatabase(this.context);
        this.sessionManager = SessionManager.getInstance(this.context);
        this.apiClient = BackendApiClient.getInstance(this.context);
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.mainHandler = new Handler(Looper.getMainLooper());

        long savedSyncTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0L);
        lastSyncTimeLive.postValue(savedSyncTime);

        schedulePeriodicSync();
    }

    public static synchronized SyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new SyncManager(context);
        }
        return instance;
    }

    public LiveData<SyncStatus> getSyncStatusLive() {
        return syncStatusLive;
    }

    public LiveData<Long> getLastSyncTimeLive() {
        return lastSyncTimeLive;
    }

    public void triggerSync() {
        if (!sessionManager.isLoggedIn()) {
            Log.d(TAG, "Skipping sync: user is not logged in");
            return;
        }

        if (isSyncing.compareAndSet(false, true)) {
            mainHandler.post(() -> syncStatusLive.setValue(SyncStatus.SYNCING));

            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    performTwoWaySync();
                } catch (Exception e) {
                    Log.e(TAG, "Sync failed with exception", e);
                    MonitoringManager.getInstance(context).recordSyncEvent("ERROR", "Sync exception: " + e.getMessage(), true);
                    mainHandler.post(() -> syncStatusLive.setValue(SyncStatus.ERROR));
                } finally {
                    isSyncing.set(false);
                }
            });
        }
    }

    private void performTwoWaySync() {
        String userId = sessionManager.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            isSyncing.set(false);
            return;
        }

        TransactionDao txDao = database.transactionDao();
        AccountDao accDao = database.accountDao();
        int localTxCount = txDao.getAllTransactions(userId).size();
        int localAccCount = accDao.getAccounts(userId).size();

        long lastSync = prefs.getLong(KEY_LAST_SYNC_TIME, 0L);
        // If local database has 0 transactions or 0 accounts, ALWAYS perform a full pull (sinceStr = null)
        // so data is seamlessly restored when rebooting phone, reinstalling app, or switching users!
        String sinceStr = null;
        if (lastSync > 0 && localTxCount > 0 && localAccCount > 0) {
            sinceStr = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(new java.util.Date(lastSync));
        }

        // STEP 1: PULL CLOUD ENTITIES
        apiClient.pullSyncData(sinceStr, new BackendApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject response) {
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    try {
                        if (response.has("data") && response.get("data").isJsonObject()) {
                            JsonObject data = response.getAsJsonObject("data");
                            mergePullData(userId, data);
                        }

                        // STEP 2: PUSH UNSYNCED LOCAL ENTITIES
                        pushUnsyncedLocalData(userId);
                    } catch (Exception ex) {
                        Log.e(TAG, "Error processing pulled data", ex);
                        mainHandler.post(() -> syncStatusLive.setValue(SyncStatus.ERROR));
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                Log.w(TAG, "Pull sync failed: " + errorMessage);
                mainHandler.post(() -> syncStatusLive.setValue(SyncStatus.OFFLINE));
            }
        });
    }

    private void mergePullData(String currentUserId, JsonObject data) {
        TransactionDao txDao = database.transactionDao();
        AccountDao accDao = database.accountDao();
        CategoryDao catDao = database.categoryDao();
        TripDao tripDao = database.tripDao();
        SavingGoalDao goalDao = database.savingGoalDao();
        UpcomingExpenseDao upcomingDao = database.upcomingExpenseDao();

        // 1. Merge Accounts
        if (data.has("accounts") && data.get("accounts").isJsonArray()) {
            JsonArray accounts = data.getAsJsonArray("accounts");
            for (JsonElement el : accounts) {
                JsonObject a = el.getAsJsonObject();
                String accId = a.get("id").getAsString();
                String accUserId = a.has("userId") && !a.get("userId").isJsonNull() ? a.get("userId").getAsString() : currentUserId;
                if (!currentUserId.equals(accUserId)) continue; // Enforce user isolation

                String name = a.get("name").getAsString();
                String type = a.get("type").getAsString();
                long balance = a.has("openingBalanceMinor") ? a.get("openingBalanceMinor").getAsLong() : 0L;
                String currency = a.has("currencyCode") ? a.get("currencyCode").getAsString() : "INR";
                accDao.insert(new AccountEntity(accId, accUserId, name, type, balance, currency, System.currentTimeMillis()));
            }
        }

        // 2. Merge Categories
        if (data.has("categories") && data.get("categories").isJsonArray()) {
            JsonArray cats = data.getAsJsonArray("categories");
            for (JsonElement el : cats) {
                JsonObject c = el.getAsJsonObject();
                String catId = c.get("id").getAsString();
                String catUserId = c.has("userId") && !c.get("userId").isJsonNull() ? c.get("userId").getAsString() : null;
                String name = c.get("name").getAsString();
                String type = c.get("type").getAsString();
                String icon = c.has("iconKey") ? c.get("iconKey").getAsString() : "ic_other";
                String color = c.has("colorHex") ? c.get("colorHex").getAsString() : "#10B981";
                catDao.insert(new CategoryEntity(catId, catUserId, name, type, icon, color));
            }
        }

        // 3. Merge Trips
        if (data.has("trips") && data.get("trips").isJsonArray()) {
            JsonArray trips = data.getAsJsonArray("trips");
            for (JsonElement el : trips) {
                JsonObject tr = el.getAsJsonObject();
                String tripId = tr.get("id").getAsString();
                String trUserId = tr.has("userId") && !tr.get("userId").isJsonNull() ? tr.get("userId").getAsString() : currentUserId;
                if (!currentUserId.equals(trUserId)) continue;

                String name = tr.get("name").getAsString();
                String dest = tr.has("destination") ? tr.get("destination").getAsString() : "";
                String sDate = tr.get("startDate").getAsString();
                String eDate = tr.get("endDate").getAsString();
                String desc = tr.has("description") ? tr.get("description").getAsString() : "";
                String status = tr.has("status") ? tr.get("status").getAsString() : "active";

                TripEntity existing = tripDao.getTripById(tripId);
                if (existing == null || existing.isSynced) {
                    tripDao.insert(new TripEntity(tripId, trUserId, name, dest, sDate, eDate, desc, status, System.currentTimeMillis(), System.currentTimeMillis(), true));
                }
            }
        }

        // 4. Merge Transactions (Strict conflict rule: do not overwrite unsynced local pending transactions)
        if (data.has("transactions") && data.get("transactions").isJsonArray()) {
            JsonArray txs = data.getAsJsonArray("transactions");
            for (JsonElement el : txs) {
                JsonObject t = el.getAsJsonObject();
                String txId = t.get("id").getAsString();
                String txUserId = t.has("userId") && !t.get("userId").isJsonNull() ? t.get("userId").getAsString() : currentUserId;
                if (!currentUserId.equals(txUserId)) continue;

                TransactionEntity existing = txDao.getTransactionById(txId);
                // If record does not exist or exists and was already synced, apply cloud record
                if (existing == null || existing.isSynced) {
                    String accountId = t.has("accountId") && !t.get("accountId").isJsonNull() ? t.get("accountId").getAsString() : "";
                    String type = t.get("type").getAsString();
                    long amountMinor = t.get("amountMinor").getAsLong();
                    String currency = t.has("currencyCode") ? t.get("currencyCode").getAsString() : "INR";
                    String catId = t.has("categoryId") && !t.get("categoryId").isJsonNull() ? t.get("categoryId").getAsString() : null;
                    String desc = t.has("description") && !t.get("description").isJsonNull() ? t.get("description").getAsString() : "";
                    String date = t.has("transactionDate") && !t.get("transactionDate").isJsonNull() ? t.get("transactionDate").getAsString() : "";
                    String status = t.has("status") ? t.get("status").getAsString() : "completed";
                    String idemp = t.has("idempotencyKey") && !t.get("idempotencyKey").isJsonNull() ? t.get("idempotencyKey").getAsString() : null;
                    String group = t.has("expenseGroup") ? t.get("expenseGroup").getAsString() : "GENERAL";
                    String tripId = t.has("tripId") && !t.get("tripId").isJsonNull() ? t.get("tripId").getAsString() : null;
                    boolean important = t.has("isImportant") && t.get("isImportant").getAsBoolean();

                    txDao.insert(new TransactionEntity(
                            txId, txUserId, accountId, type, amountMinor, currency,
                            catId, desc, date, status, idemp, null, null,
                            true, System.currentTimeMillis(), group, tripId, important
                    ));
                }
            }
        }
    }

    private void pushUnsyncedLocalData(String userId) {
        TransactionDao txDao = database.transactionDao();
        TripDao tripDao = database.tripDao();

        List<TransactionEntity> unsyncedTx = txDao.getUnsyncedTransactions();
        List<TripEntity> unsyncedTrips = tripDao.getUnsyncedTrips();

        if (unsyncedTx.isEmpty() && unsyncedTrips.isEmpty()) {
            finalizeSyncSuccess();
            return;
        }

        JsonObject payload = new JsonObject();

        if (!unsyncedTx.isEmpty()) {
            JsonArray txArray = new JsonArray();
            for (TransactionEntity t : unsyncedTx) {
                JsonObject o = new JsonObject();
                o.addProperty("id", t.id);
                o.addProperty("accountId", t.accountId);
                o.addProperty("type", t.type);
                o.addProperty("amountMinor", t.amountMinor);
                o.addProperty("currencyCode", t.currencyCode);
                o.addProperty("categoryId", t.categoryId);
                o.addProperty("description", t.description);
                o.addProperty("transactionDate", t.transactionDate);
                o.addProperty("status", t.status);
                o.addProperty("idempotencyKey", t.idempotencyKey);
                o.addProperty("expenseGroup", t.expenseGroup);
                o.addProperty("tripId", t.tripId);
                o.addProperty("isImportant", t.isImportant);
                txArray.add(o);
            }
            payload.add("transactions", txArray);
        }

        if (!unsyncedTrips.isEmpty()) {
            JsonArray trArray = new JsonArray();
            for (TripEntity tr : unsyncedTrips) {
                JsonObject o = new JsonObject();
                o.addProperty("id", tr.id);
                o.addProperty("name", tr.name);
                o.addProperty("destination", tr.destination);
                o.addProperty("startDate", tr.startDate);
                o.addProperty("endDate", tr.endDate);
                o.addProperty("description", tr.description);
                o.addProperty("status", tr.status);
                trArray.add(o);
            }
            payload.add("trips", trArray);
        }

        apiClient.pushSyncData(payload, new BackendApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    for (TransactionEntity t : unsyncedTx) {
                        txDao.markAsSynced(t.id);
                    }
                    for (TripEntity tr : unsyncedTrips) {
                        tripDao.markAsSynced(tr.id);
                    }
                    finalizeSyncSuccess();
                });
            }

            @Override
            public void onError(String errorMessage) {
                Log.w(TAG, "Push sync failed: " + errorMessage);
                mainHandler.post(() -> syncStatusLive.setValue(SyncStatus.OFFLINE));
            }
        });
    }

    private void finalizeSyncSuccess() {
        long now = System.currentTimeMillis();
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, now).apply();

        mainHandler.post(() -> {
            lastSyncTimeLive.setValue(now);
            syncStatusLive.setValue(SyncStatus.SUCCESS);
            // Reset to IDLE after 3 seconds
            mainHandler.postDelayed(() -> syncStatusLive.setValue(SyncStatus.IDLE), 3000);
        });

        MonitoringManager.getInstance(context).recordSyncEvent("SUCCESS", "Sync completed successfully at " + now, false);
    }

    private void schedulePeriodicSync() {
        try {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            PeriodicWorkRequest syncWork = new PeriodicWorkRequest.Builder(AccountSyncWorker.class, 15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build();

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    SYNC_PERIODIC_WORK,
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncWork
            );
        } catch (Throwable ignored) {
        }
    }
}
