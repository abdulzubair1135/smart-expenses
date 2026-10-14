package com.smarttracker.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories")
public class CategoryEntity {

    @PrimaryKey
    @NonNull
    public String id;

    public String userId; // null for default system categories

    @NonNull
    public String name;

    @NonNull
    public String type; // expense, income, both

    @NonNull
    public String iconKey;

    @NonNull
    public String colorHex;

    public CategoryEntity(@NonNull String id, String userId, @NonNull String name,
                          @NonNull String type, @NonNull String iconKey, @NonNull String colorHex) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.iconKey = iconKey;
        this.colorHex = colorHex;
    }
}
