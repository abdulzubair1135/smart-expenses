package com.smarttracker.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.smarttracker.data.local.AppDatabase;
import com.smarttracker.data.local.dao.AccountDao;
import com.smarttracker.data.local.dao.CategoryDao;
import com.smarttracker.data.local.dao.SavingGoalDao;
import com.smarttracker.data.local.dao.TransactionDao;
import com.smarttracker.data.local.dao.UpcomingExpenseDao;
import com.smarttracker.data.local.entity.AccountEntity;
import com.smarttracker.data.local.entity.CategoryEntity;
import com.smarttracker.data.local.entity.SavingGoalEntity;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.data.local.entity.UpcomingExpenseEntity;

import java.util.List;

public class TransactionRepository {

    private final TransactionDao transactionDao;
    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final SavingGoalDao savingGoalDao;
    private final UpcomingExpenseDao upcomingExpenseDao;

    public TransactionRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        this.transactionDao = db.transactionDao();
        this.accountDao = db.accountDao();
        this.categoryDao = db.categoryDao();
        this.savingGoalDao = db.savingGoalDao();
        this.upcomingExpenseDao = db.upcomingExpenseDao();
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
        AppDatabase.databaseWriteExecutor.execute(() -> transactionDao.insert(transaction));
    }

    public void deleteTransaction(String id) {
        AppDatabase.databaseWriteExecutor.execute(() -> transactionDao.deleteById(id));
    }
}
