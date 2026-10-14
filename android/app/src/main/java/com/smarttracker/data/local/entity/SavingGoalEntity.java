package com.smarttracker.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "saving_goals")
public class SavingGoalEntity {

    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String userId;

    @NonNull
    public String title;

    public long targetAmountMinor;

    public long currentAmountMinor;

    public String targetDate;

    public SavingGoalEntity(@NonNull String id, @NonNull String userId, @NonNull String title,
                            long targetAmountMinor, long currentAmountMinor, String targetDate) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.targetAmountMinor = targetAmountMinor;
        this.currentAmountMinor = currentAmountMinor;
        this.targetDate = targetDate;
    }
}
