package com.smarttracker.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

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

import android.content.Context;
import android.util.Log;
import com.smarttracker.core.sync.SyncManager;
import com.smarttracker.data.remote.BackendApiClient;

import java.util.List;

public class TransactionRepository {

    private final Context applicationContext;
    private final TransactionDao transactionDao;
    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final SavingGoalDao savingGoalDao;
    private final UpcomingExpenseDao upcomingExpenseDao;
    private final TripDao tripDao;

    public TransactionRepository(Application application) {
        this.applicationContext = application.getApplicationContext();
        AppDatabase db = AppDatabase.getDatabase(application);
        this.transactionDao = db.transactionDao();
        this.accountDao = db.accountDao();
        this.categoryDao = db.categoryDao();
        this.savingGoalDao = db.savingGoalDao();
        this.upcomingExpenseDao = db.upcomingExpenseDao();
        this.tripDao = db.tripDao();
    }

    public LiveData<List<TransactionEntity>> getAllTransactionsLive(String userId) {
        return transactionDao.getAllTransactionsLive(userId);
    }

    public LiveData<List<TransactionEntity>> getRecentTransactionsLive(String userId, int limit) {
        return transactionDao.getRecentTransactionsLive(userId, limit);
    }

    public LiveData<List<AccountEntity>> getAccountsLive(String userId) {
        return accountDao.getAccountsLive(userId);
    }

    public LiveData<List<CategoryEntity>> getCategoriesLive(String userId) {
        return categoryDao.getCategoriesLive(userId);
    }

    public LiveData<SavingGoalEntity> getPrimaryGoalLive(String userId) {
        return savingGoalDao.getPrimaryGoalLive(userId);
    }

    public LiveData<UpcomingExpenseEntity> getNextUpcomingLive(String userId) {
        return upcomingExpenseDao.getNextUpcomingLive(userId);
    }

    public void insertTransaction(TransactionEntity transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            transactionDao.insert(transaction);
            if (applicationContext != null) {
                SyncManager.getInstance(applicationContext).triggerSync();
            }
        });
    }

    public void deleteTransaction(String id) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            transactionDao.deleteById(id);
            if (applicationContext != null) {
                BackendApiClient.getInstance(applicationContext).deleteTransaction(id, new BackendApiClient.ApiCallback<com.google.gson.JsonObject>() {
                    @Override
                    public void onSuccess(com.google.gson.JsonObject response) {
                        Log.i("TransactionRepository", "Cloud transaction deleted: " + id);
                    }

                    @Override
                    public void onError(String error) {
                        Log.w("TransactionRepository", "Cloud transaction deletion queued: " + error);
                        SyncManager.getInstance(applicationContext).queueDeletedTransaction(id);
                    }
                });
                SyncManager.getInstance(applicationContext).triggerSync();
            }
        });
    }

    // ==========================================
    // TRIPS OPERATIONS
    // ==========================================

    public LiveData<List<TripEntity>> getTripsByUserLive(String userId) {
        return tripDao.getTripsByUserLive(userId);
    }

    public LiveData<TripEntity> getTripByIdLive(String id) {
        return tripDao.getTripByIdLive(id);
    }

    public void insertTrip(TripEntity trip) {
        AppDatabase.databaseWriteExecutor.execute(() -> tripDao.insert(trip));
    }

    public void updateTrip(TripEntity trip) {
        AppDatabase.databaseWriteExecutor.execute(() -> tripDao.update(trip));
    }

    public void deleteTrip(String id) {
        AppDatabase.databaseWriteExecutor.execute(() -> tripDao.deleteById(id));
    }

    // ==========================================
    // PERIOD & EXPENSE GROUPS LIVE DATA
    // ==========================================

    public LiveData<List<TransactionEntity>> getExpensesByDayLive(String userId, String datePrefix) {
        return transactionDao.getExpensesByDayLive(userId, datePrefix);
    }

    public LiveData<List<TransactionEntity>> getExpensesByMonthLive(String userId, String monthPrefix) {
        return transactionDao.getExpensesByMonthLive(userId, monthPrefix);
    }

    public LiveData<List<TransactionEntity>> getTravelExpensesLive(String userId) {
        return transactionDao.getTravelExpensesLive(userId);
    }

    public LiveData<List<TransactionEntity>> getTransactionsByTripLive(String userId, String tripId) {
        return transactionDao.getTransactionsByTripLive(userId, tripId);
    }

    public LiveData<List<TransactionEntity>> getSpecialExpensesLive(String userId) {
        return transactionDao.getSpecialExpensesLive(userId);
    }
}
