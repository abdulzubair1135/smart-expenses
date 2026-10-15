package com.smarttracker.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.smarttracker.data.local.entity.TransactionEntity;

import java.util.List;

@Dao
public interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TransactionEntity transaction);

    @Update
    void update(TransactionEntity transaction);

    @Query("DELETE FROM transactions WHERE id = :id")
    void deleteById(String id);

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    TransactionEntity getTransactionById(String id);

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY transactionDate DESC")
    LiveData<List<TransactionEntity>> getAllTransactionsLive(String userId);

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY transactionDate DESC")
    List<TransactionEntity> getAllTransactions(String userId);

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY transactionDate DESC LIMIT :limit")
    LiveData<List<TransactionEntity>> getRecentTransactionsLive(String userId, int limit);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND type = :type ORDER BY transactionDate DESC")
    LiveData<List<TransactionEntity>> getTransactionsByTypeLive(String userId, String type);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND (accountId = :accountId OR destinationAccountId = :accountId)")
    List<TransactionEntity> getTransactionsByAccount(String userId, String accountId);

    @Query("SELECT * FROM transactions WHERE isSynced = 0")
    List<TransactionEntity> getUnsyncedTransactions();

    @Query("UPDATE transactions SET isSynced = 1 WHERE id = :id")
    void markAsSynced(String id);

    @Query("UPDATE transactions SET userId = :newUserId")
    void reassignAllTransactionsToUser(String newUserId);

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY createdAt DESC LIMIT 1")
    TransactionEntity getLatestTransaction(String userId);

    // ==========================================
    // PERIOD & EXPENSE GROUP SPECIFIC QUERIES
    // ==========================================

    @Query("SELECT * FROM transactions WHERE userId = :userId AND type = 'expense' AND status = 'completed' AND transactionDate LIKE :datePrefix || '%' ORDER BY transactionDate DESC")
    LiveData<List<TransactionEntity>> getExpensesByDayLive(String userId, String datePrefix);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND type = 'expense' AND status = 'completed' AND transactionDate LIKE :datePrefix || '%' ORDER BY transactionDate DESC")
    List<TransactionEntity> getExpensesByDay(String userId, String datePrefix);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND type = 'expense' AND status = 'completed' AND transactionDate LIKE :monthPrefix || '%' ORDER BY transactionDate DESC")
    LiveData<List<TransactionEntity>> getExpensesByMonthLive(String userId, String monthPrefix);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND type = 'expense' AND status = 'completed' AND transactionDate LIKE :monthPrefix || '%' ORDER BY transactionDate DESC")
    List<TransactionEntity> getExpensesByMonth(String userId, String monthPrefix);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND type = 'expense' AND status = 'completed' AND (expenseGroup = 'TRAVEL' OR tripId IS NOT NULL) ORDER BY transactionDate DESC")
    LiveData<List<TransactionEntity>> getTravelExpensesLive(String userId);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND type = 'expense' AND status = 'completed' AND (expenseGroup = 'TRAVEL' OR tripId IS NOT NULL) ORDER BY transactionDate DESC")
    List<TransactionEntity> getTravelExpenses(String userId);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND tripId = :tripId ORDER BY transactionDate DESC")
    LiveData<List<TransactionEntity>> getTransactionsByTripLive(String userId, String tripId);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND tripId = :tripId ORDER BY transactionDate DESC")
    List<TransactionEntity> getTransactionsByTrip(String userId, String tripId);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND type = 'expense' AND status = 'completed' AND expenseGroup = 'SPECIAL' ORDER BY transactionDate DESC")
    LiveData<List<TransactionEntity>> getSpecialExpensesLive(String userId);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND type = 'expense' AND status = 'completed' AND expenseGroup = 'SPECIAL' ORDER BY transactionDate DESC")
    List<TransactionEntity> getSpecialExpenses(String userId);
}
