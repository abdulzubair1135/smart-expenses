package com.smarttracker.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "upcoming_expenses")
public class UpcomingExpenseEntity {

    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String userId;

    @NonNull
    public String title;

    public long expectedAmountMinor;

    @NonNull
    public String dueDate;

    public boolean reminderEnabled;

    @NonNull
    public String status; // pending, paid, cancelled

    public UpcomingExpenseEntity(@NonNull String id, @NonNull String userId, @NonNull String title,
                                 long expectedAmountMinor, @NonNull String dueDate,
                                 boolean reminderEnabled, @NonNull String status) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.expectedAmountMinor = expectedAmountMinor;
        this.dueDate = dueDate;
        this.reminderEnabled = reminderEnabled;
        this.status = status;
    }
}
