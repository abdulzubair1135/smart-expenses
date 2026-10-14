package com.smarttracker.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "accounts")
public class AccountEntity {

    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String userId;

    @NonNull
    public String name; // Cash, Bank, UPI, Savings

    @NonNull
    public String type; // cash, bank, upi, savings

    public long openingBalanceMinor;

    @NonNull
    public String currencyCode;

    public long createdAt;

    public AccountEntity(@NonNull String id, @NonNull String userId, @NonNull String name,
                         @NonNull String type, long openingBalanceMinor, @NonNull String currencyCode,
                         long createdAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.openingBalanceMinor = openingBalanceMinor;
        this.currencyCode = currencyCode;
        this.createdAt = createdAt;
    }
}
