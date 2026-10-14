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

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY createdAt DESC LIMIT 1")
    TransactionEntity getLatestTransaction(String userId);
}
