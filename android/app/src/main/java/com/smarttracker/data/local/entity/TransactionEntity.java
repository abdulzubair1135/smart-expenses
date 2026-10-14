package com.smarttracker.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
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

    // Period & Expense Group Classifications
    public String expenseGroup; // GENERAL, TRAVEL, SPECIAL
    public String tripId; // Nullable, linked to trips table
    public boolean isImportant; // Flag for special / emergency transactions

    public TransactionEntity(@NonNull String id, @NonNull String userId, @NonNull String accountId,
                             @NonNull String type, long amountMinor, @NonNull String currencyCode,
                             String categoryId, String description, @NonNull String transactionDate,
                             @NonNull String status, String idempotencyKey, String relatedPerson,
                             String destinationAccountId, boolean isSynced, long createdAt,
                             String expenseGroup, String tripId, boolean isImportant) {
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
        this.expenseGroup = (expenseGroup != null && !expenseGroup.trim().isEmpty()) ? expenseGroup : "GENERAL";
        this.tripId = tripId;
        this.isImportant = isImportant;
    }

    @Ignore
    public TransactionEntity(@NonNull String id, @NonNull String userId, @NonNull String accountId,
                             @NonNull String type, long amountMinor, @NonNull String currencyCode,
                             String categoryId, String description, @NonNull String transactionDate,
                             @NonNull String status, String idempotencyKey, String relatedPerson,
                             String destinationAccountId, boolean isSynced, long createdAt) {
        this(id, userId, accountId, type, amountMinor, currencyCode, categoryId, description,
                transactionDate, status, idempotencyKey, relatedPerson, destinationAccountId, isSynced,
                createdAt, "GENERAL", null, false);
    }
}
