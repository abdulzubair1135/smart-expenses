package com.smarttracker.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.smarttracker.data.local.entity.AccountEntity;

import java.util.List;

@Dao
public interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AccountEntity account);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AccountEntity> accounts);

    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY createdAt ASC")
    LiveData<List<AccountEntity>> getAccountsLive(String userId);

    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY createdAt ASC")
    List<AccountEntity> getAccounts(String userId);

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    AccountEntity getAccountById(String id);

    @Query("UPDATE accounts SET userId = :newUserId")
    void reassignAllAccountsToUser(String newUserId);
}
