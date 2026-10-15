package com.smarttracker.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.smarttracker.data.local.entity.UpcomingExpenseEntity;

import java.util.List;

@Dao
public interface UpcomingExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(UpcomingExpenseEntity expense);

    @Query("SELECT * FROM upcoming_expenses WHERE userId = :userId AND status = 'pending' ORDER BY dueDate ASC LIMIT 1")
    LiveData<UpcomingExpenseEntity> getNextUpcomingLive(String userId);

    @Query("SELECT * FROM upcoming_expenses WHERE userId = :userId ORDER BY dueDate ASC")
    LiveData<List<UpcomingExpenseEntity>> getAllUpcomingLive(String userId);

    @Query("UPDATE upcoming_expenses SET userId = :newUserId")
    void reassignAllUpcomingToUser(String newUserId);
}
