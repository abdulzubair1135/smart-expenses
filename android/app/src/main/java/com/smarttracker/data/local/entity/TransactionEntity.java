package com.smarttracker.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions")
public class TransactionEntity {

    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String userId;

    @NonNull
    public String accountId;

    @NonNull
    public String type; // expense, received, lend, borrow, transfer

    public long amountMinor; // e.g. 25000 = ₹250.00

    @NonNull
    public String currencyCode; // INR

    public String categoryId;

    public String description;

    @NonNull
    public String transactionDate; // ISO-8601 string

    @NonNull
    public String status; // completed, pending, cancelled

    public String idempotencyKey;

    public String relatedPerson; // For Lend/Borrow

    public String destinationAccountId; // For Transfer

    public boolean isSynced; // Local-First sync flag

    public long createdAt;

    public TransactionEntity(@NonNull String id, @NonNull String userId, @NonNull String accountId,
                             @NonNull String type, long amountMinor, @NonNull String currencyCode,
                             String categoryId, String description, @NonNull String transactionDate,
                             @NonNull String status, String idempotencyKey, String relatedPerson,
                             String destinationAccountId, boolean isSynced, long createdAt) {
        this.id = id;
        this.userId = userId;
        this.accountId = accountId;
        this.type = type;
        this.amountMinor = amountMinor;
        this.currencyCode = currencyCode;
        this.categoryId = categoryId;
        this.description = description;
        this.transactionDate = transactionDate;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.relatedPerson = relatedPerson;
        this.destinationAccountId = destinationAccountId;
        this.isSynced = isSynced;
        this.createdAt = createdAt;
    }
}
