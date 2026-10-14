package com.smarttracker.ui.voice;

public class VoiceCommandModel {

    public String intent; // CREATE_TRANSACTION, QUERY_BALANCE, QUERY_EXPENSES, QUERY_INCOME, QUERY_CATEGORY_TOTAL, CONFIRM_DRAFT, CANCEL_DRAFT, UNKNOWN
    public String transactionType; // expense, received, lend, borrow, transfer
    public long amountMinor; // in integer paise (e.g. 25000 = ₹250.00)
    public String currencyCode = "INR";
    public String description;
    public String categoryName;
    public String accountName;
    public String transactionDate; // YYYY-MM-DD
    public String recipient;
    public String sourceAccount;
    public String destinationAccount;
    public boolean clarificationRequired;
    public String clarificationQuestion;
    public String spokenResponse;

    public double confidence = 0.95;
    public String aiProvider = "deterministic-fallback";
    public String correlationId;

    // Idempotency tracking key
    public String idempotencyKey;

    public VoiceCommandModel() {
        this.idempotencyKey = java.util.UUID.randomUUID().toString();
        this.correlationId = "corr_" + System.currentTimeMillis();
    }
}
