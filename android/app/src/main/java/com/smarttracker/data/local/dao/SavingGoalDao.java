package com.smarttracker.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.smarttracker.data.local.entity.SavingGoalEntity;

import java.util.List;

@Dao
public interface SavingGoalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SavingGoalEntity goal);

    @Query("SELECT * FROM saving_goals WHERE userId = :userId LIMIT 1")
    LiveData<SavingGoalEntity> getPrimaryGoalLive(String userId);

    @Query("SELECT * FROM saving_goals WHERE userId = :userId")
    LiveData<List<SavingGoalEntity>> getAllGoalsLive(String userId);

    @Query("UPDATE saving_goals SET userId = :newUserId")
    void reassignAllGoalsToUser(String newUserId);
}
