package com.smarttracker.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trips")
public class TripEntity {

    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String userId;

    @NonNull
    public String name;

    @NonNull
    public String destination;

    @NonNull
    public String startDate; // yyyy-MM-dd

    @NonNull
    public String endDate; // yyyy-MM-dd

    public String description;

    @NonNull
    public String status; // active, completed, archived

    public long createdAt;

    public long updatedAt;

    public boolean isSynced;

    public TripEntity(@NonNull String id, @NonNull String userId, @NonNull String name,
                      @NonNull String destination, @NonNull String startDate, @NonNull String endDate,
                      String description, @NonNull String status, long createdAt, long updatedAt,
                      boolean isSynced) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isSynced = isSynced;
    }
}
