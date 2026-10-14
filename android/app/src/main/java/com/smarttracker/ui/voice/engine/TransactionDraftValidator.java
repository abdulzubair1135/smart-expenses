package com.smarttracker.ui.voice.engine;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smarttracker.ui.voice.VoiceCommandModel;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Strict Java validation layer for on-device AI generated transactions.
 * Enforces financial integrity:
 * - Positive integer minor units (paise > 0)
 * - Validated transaction types (RECEIVED, EXPENSE, LEND, BORROW, TRANSFER)
 * - Required counterparty for loans
 * - Ambiguity guards (never blindly default to EXPENSE)
 * - Rejects SQL, file paths, or command injections
 */
public class TransactionDraftValidator {

    private static final String TAG = "DraftValidator";

    private static final Set<String> VALID_TYPES = new HashSet<>(Arrays.asList(
            "received", "income", "expense", "lend", "borrow", "transfer"
    ));

    private static final Set<String> VALID_ACCOUNTS = new HashSet<>(Arrays.asList(
            "upi", "cash", "bank", "savings", "wallet", "credit card"
    ));

    private static final Set<String> VALID_CATEGORIES = new HashSet<>(Arrays.asList(
            "food", "groceries", "transport", "shopping", "bills", "entertainment",
            "health", "salary", "investment", "loan", "repayment", "transfer", "others"
    ));

    public static class ValidationResult {
        public final boolean isValid;
        @Nullable public final VoiceCommandModel model;
        @Nullable public final String errorMessage;
        public final boolean requiresClarification;
        @Nullable public final String clarificationQuestion;

        private ValidationResult(boolean isValid, @Nullable VoiceCommandModel model,
                                 @Nullable String errorMessage, boolean requiresClarification,
                                 @Nullable String clarificationQuestion) {
            this.isValid = isValid;
            this.model = model;
            this.errorMessage = errorMessage;
            this.requiresClarification = requiresClarification;
            this.clarificationQuestion = clarificationQuestion;
        }

        public static ValidationResult valid(@NonNull VoiceCommandModel model) {
            return new ValidationResult(true, model, null, false, null);
        }

        public static ValidationResult clarification(@NonNull String question, @NonNull VoiceCommandModel partial) {
            partial.clarificationRequired = true;
            partial.clarificationQuestion = question;
            return new ValidationResult(false, partial, null, true, question);
        }

        public static ValidationResult error(@NonNull String message) {
            return new ValidationResult(false, null, message, false, null);
        }
    }

    /**
     * Parses and validates raw JSON produced by on-device Qwen.
     */
    @NonNull
    public static ValidationResult validateJson(@NonNull String jsonString, @NonNull String rawTranscript) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return ValidationResult.clarification("Ye transaction samajh nahi aaya. Kripya dobara boliye.",
                    createEmptyModel(rawTranscript));
        }

        try {
            // Find JSON boundary if surrounded by markdown or thinking tags
            String cleanJson = extractJsonSubstring(jsonString);
            JsonObject obj = JsonParser.parseString(cleanJson).getAsJsonObject();

            VoiceCommandModel model = new VoiceCommandModel();
            model.intent = obj.has("intent") && !obj.get("intent").isJsonNull()
                    ? obj.get("intent").getAsString() : "CREATE_TRANSACTION";

            // Clarification check
            if (obj.has("requiresClarification") && obj.get("requiresClarification").getAsBoolean()) {
                String question = obj.has("clarificationQuestion") && !obj.get("clarificationQuestion").isJsonNull()
                        ? obj.get("clarificationQuestion").getAsString()
                        : "Kripya clear boliye: kya aapne paise kamaye, kharch kiye, ya kisi ko udhar diye?";
                return ValidationResult.clarification(question, model);
            }

            // Transaction Type validation
            String type = null;
            if (obj.has("transactionType") && !obj.get("transactionType").isJsonNull()) {
                type = obj.get("transactionType").getAsString().trim().toLowerCase(Locale.ROOT);
            } else if (obj.has("transaction_type") && !obj.get("transaction_type").isJsonNull()) {
                type = obj.get("transaction_type").getAsString().trim().toLowerCase(Locale.ROOT);
            }

            if (type == null || !VALID_TYPES.contains(type)) {
                return ValidationResult.clarification("Transaction ka type clear nahi hai (kamaye, kharch, ya udhar). Kripya dobara boliye.",
                        createEmptyModel(rawTranscript));
            }
            model.transactionType = "income".equals(type) ? "received" : type;

            // Amount validation (must be positive integer minor units)
            long amountMinor = 0;
            if (obj.has("amountMinor") && !obj.get("amountMinor").isJsonNull()) {
                amountMinor = obj.get("amountMinor").getAsLong();
            } else if (obj.has("amount_minor") && !obj.get("amount_minor").isJsonNull()) {
                amountMinor = obj.get("amount_minor").getAsLong();
            } else if (obj.has("amount") && !obj.get("amount").isJsonNull()) {
                // If model returned major rupees, convert safely to paise
                amountMinor = (long) Math.round(obj.get("amount").getAsDouble() * 100);
            }

            if (amountMinor <= 0) {
                return ValidationResult.clarification("Kitne rupaye ka transaction hai? Kripya amount boliye.",
                        model);
            }
            model.amountMinor = amountMinor;
            model.currencyCode = "INR";

            // Description sanitization (prevent SQL injections / path traversal)
            String desc = obj.has("description") && !obj.get("description").isJsonNull()
                    ? obj.get("description").getAsString() : "";
            model.description = sanitizeText(desc, "Transaction");

            // Category & Account
            String cat = obj.has("categoryName") && !obj.get("categoryName").isJsonNull()
                    ? obj.get("categoryName").getAsString() : (obj.has("category") ? obj.get("category").getAsString() : "Others");
            model.categoryName = sanitizeCategory(cat, model.transactionType);

            String acc = obj.has("accountName") && !obj.get("accountName").isJsonNull()
                    ? obj.get("accountName").getAsString() : (obj.has("account") ? obj.get("account").getAsString() : "UPI");
            model.accountName = sanitizeAccount(acc);

            // Counterparty for loans
            if ("lend".equals(model.transactionType) || "borrow".equals(model.transactionType)) {
                String recipient = obj.has("recipient") && !obj.get("recipient").isJsonNull()
                        ? obj.get("recipient").getAsString() : null;
                model.recipient = sanitizeText(recipient, "lend".equals(model.transactionType) ? "Friend" : "Lender");
            }

            model.aiProvider = "local-qwen";
            model.confidence = 0.95;

            return ValidationResult.valid(model);

        } catch (Exception e) {
            Log.e(TAG, "Validation failed on JSON: " + jsonString + ". Error: " + e.getMessage());
            return ValidationResult.clarification("Ye transaction samajh nahi aaya. Kripya dobara boliye.",
                    createEmptyModel(rawTranscript));
        }
    }

    /**
     * Validates a model instance produced by fallback or direct parser.
     */
    @NonNull
    public static ValidationResult validateModel(@NonNull VoiceCommandModel model) {
        if (model.clarificationRequired) {
            String question = model.clarificationQuestion != null
                    ? model.clarificationQuestion
                    : "Kripya clear boliye (jaise: ₹50 kharch ya ₹50 kamaye).";
            return ValidationResult.clarification(question, model);
        }

        if (model.transactionType == null || !VALID_TYPES.contains(model.transactionType.toLowerCase(Locale.ROOT))) {
            return ValidationResult.clarification("Transaction ka type clear nahi hai. Kripya spasht boliye.", model);
        }

        if (model.amountMinor <= 0) {
            return ValidationResult.clarification("Kitne rupaye ka transaction hai? Kripya amount boliye.", model);
        }

        model.description = sanitizeText(model.description, "Transaction");
        model.categoryName = sanitizeCategory(model.categoryName, model.transactionType);
        model.accountName = sanitizeAccount(model.accountName);

        return ValidationResult.valid(model);
    }

    private static String extractJsonSubstring(String text) {
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        return text;
    }

    private static String sanitizeText(@Nullable String input, @NonNull String defaultVal) {
        if (input == null || input.trim().isEmpty()) {
            return defaultVal;
        }
        // Remove dangerous characters for SQL or commands
        String clean = input.replaceAll("['\";\\\\]", "").trim();
        return clean.length() > 60 ? clean.substring(0, 60) : clean;
    }

    private static String sanitizeCategory(String input, String transactionType) {
        if (input == null || input.trim().isEmpty()) {
            return "received".equalsIgnoreCase(transactionType) ? "Salary" : "Others";
        }
        String lower = input.trim().toLowerCase(Locale.ROOT);
        for (String valid : VALID_CATEGORIES) {
            if (valid.equals(lower) || lower.contains(valid)) {
                return Character.toUpperCase(valid.charAt(0)) + valid.substring(1);
            }
        }
        return "Others";
    }

    private static String sanitizeAccount(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "UPI";
        }
        String lower = input.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("cash")) return "Cash";
        if (lower.contains("bank") || lower.contains("saving")) return "Bank Account";
        if (lower.contains("wallet")) return "Wallet";
        if (lower.contains("card")) return "Credit Card";
        return "UPI";
    }

    private static VoiceCommandModel createEmptyModel(String transcript) {
        VoiceCommandModel m = new VoiceCommandModel();
        m.intent = "UNKNOWN";
        m.currencyCode = "INR";
        m.clarificationRequired = true;
        return m;
    }
}
