package com.smarttracker.ui.voice;

import com.smarttracker.ui.voice.engine.ModelManager;
import com.smarttracker.ui.voice.engine.TransactionDraftValidator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for On-Device AI Validation, ModelManager, and Deterministic NLP Engine.
 */
public class OnDeviceAiTest {

    @Test
    public void testValidateValidReceivedJson() {
        String json = "{\n" +
                "  \"intent\": \"CREATE_TRANSACTION\",\n" +
                "  \"transactionType\": \"RECEIVED\",\n" +
                "  \"amountMinor\": 5000,\n" +
                "  \"category\": \"Salary\",\n" +
                "  \"account\": \"UPI\",\n" +
                "  \"description\": \"Freelance payout\",\n" +
                "  \"requiresClarification\": false\n" +
                "}";

        TransactionDraftValidator.ValidationResult result =
                TransactionDraftValidator.validateJson(json, "50 rupaye kamaye");

        assertTrue("Expected valid result", result.isValid);
        assertNotNull(result.model);
        assertEquals("received", result.model.transactionType);
        assertEquals(5000, result.model.amountMinor);
        assertEquals("Salary", result.model.categoryName);
        assertEquals("UPI", result.model.accountName);
    }

    @Test
    public void testValidateValidExpenseJson() {
        String json = "{\"intent\":\"CREATE_TRANSACTION\",\"transactionType\":\"EXPENSE\",\"amountMinor\":25000,\"category\":\"Food\",\"account\":\"Cash\"}";
        TransactionDraftValidator.ValidationResult result =
                TransactionDraftValidator.validateJson(json, "250 lunch kharch");

        assertTrue(result.isValid);
        assertNotNull(result.model);
        assertEquals("expense", result.model.transactionType);
        assertEquals(25000, result.model.amountMinor);
        assertEquals("Food", result.model.categoryName);
        assertEquals("Cash", result.model.accountName);
    }

    @Test
    public void testValidateLendingJson() {
        String json = "{\"intent\":\"CREATE_TRANSACTION\",\"transactionType\":\"LEND\",\"amountMinor\":20000,\"recipient\":\"Ali\"}";
        TransactionDraftValidator.ValidationResult result =
                TransactionDraftValidator.validateJson(json, "Ali ko 200 udhar diye");

        assertTrue(result.isValid);
        assertNotNull(result.model);
        assertEquals("lend", result.model.transactionType);
        assertEquals(20000, result.model.amountMinor);
        assertEquals("Ali", result.model.recipient);
    }

    @Test
    public void testValidateBorrowingJson() {
        String json = "{\"intent\":\"CREATE_TRANSACTION\",\"transactionType\":\"BORROW\",\"amountMinor\":20000,\"recipient\":\"Ali\"}";
        TransactionDraftValidator.ValidationResult result =
                TransactionDraftValidator.validateJson(json, "Ali se 200 udhar liye");

        assertTrue(result.isValid);
        assertNotNull(result.model);
        assertEquals("borrow", result.model.transactionType);
        assertEquals(20000, result.model.amountMinor);
        assertEquals("Ali", result.model.recipient);
    }

    @Test
    public void testValidateAmbiguousJsonRequiresClarification() {
        String json = "{\"requiresClarification\":true,\"clarificationQuestion\":\"Did you spend or earn?\"}";
        TransactionDraftValidator.ValidationResult result =
                TransactionDraftValidator.validateJson(json, "Maine paise diye");

        assertFalse(result.isValid);
        assertTrue(result.requiresClarification);
        assertNotNull(result.clarificationQuestion);
        assertEquals("Did you spend or earn?", result.clarificationQuestion);
    }

    @Test
    public void testValidateInvalidJsonThrowsNoExceptionAndRequiresClarification() {
        String malformedJson = "{ corrupted json payload without closing brackets";
        TransactionDraftValidator.ValidationResult result =
                TransactionDraftValidator.validateJson(malformedJson, "corrupt");

        assertFalse(result.isValid);
        assertTrue(result.requiresClarification);
    }

    @Test
    public void testSanitizeInjectionInDescription() {
        String json = "{\"intent\":\"CREATE_TRANSACTION\",\"transactionType\":\"EXPENSE\",\"amountMinor\":1000,\"description\":\"Dinner'; DROP TABLE transactions; --\"}";
        TransactionDraftValidator.ValidationResult result =
                TransactionDraftValidator.validateJson(json, "dinner");

        assertTrue(result.isValid);
        assertNotNull(result.model);
        assertFalse("Description must not contain SQL quote injection", result.model.description.contains("'"));
        assertFalse("Description must not contain semicolons", result.model.description.contains(";"));
    }

    @Test
    public void testOfflinePatternHindiEarnings() {
        VoiceCommandModel model = LocalPatternLanguageParser.parse("Maine 50 rupaye kamaye");
        assertEquals("received", model.transactionType);
        assertEquals(5000, model.amountMinor);
        assertFalse(model.clarificationRequired);
    }

    @Test
    public void testOfflinePatternHindiSalary() {
        VoiceCommandModel model = LocalPatternLanguageParser.parse("Mujhe 500 rupaye salary mili");
        assertEquals("received", model.transactionType);
        assertEquals(50000, model.amountMinor);
        assertEquals("Salary", model.categoryName);
        assertFalse(model.clarificationRequired);
    }

    @Test
    public void testOfflinePatternHindiExpense() {
        VoiceCommandModel model = LocalPatternLanguageParser.parse("Maine 250 rupaye kharch kiye");
        assertEquals("expense", model.transactionType);
        assertEquals(25000, model.amountMinor);
        assertFalse(model.clarificationRequired);
    }

    @Test
    public void testOfflinePatternLendVsBorrow() {
        VoiceCommandModel lend = LocalPatternLanguageParser.parse("Maine Ali ko 200 rupaye udhaar diye");
        assertEquals("lend", lend.transactionType);
        assertEquals(20000, lend.amountMinor);
        assertEquals("Ali", lend.recipient);

        VoiceCommandModel borrow = LocalPatternLanguageParser.parse("Maine Ali se 200 rupaye udhaar liye");
        assertEquals("borrow", borrow.transactionType);
        assertEquals(20000, borrow.amountMinor);
        assertEquals("Ali", borrow.recipient);
    }

    @Test
    public void testOfflinePatternAmbiguousInput() {
        VoiceCommandModel ambiguousGiven = LocalPatternLanguageParser.parse("Maine paise diye");
        assertTrue(ambiguousGiven.clarificationRequired);

        VoiceCommandModel ambiguousReceived = LocalPatternLanguageParser.parse("Mujhe paise mile");
        assertTrue(ambiguousReceived.clarificationRequired);
    }

    @Test
    public void testOfflinePatternNegation() {
        VoiceCommandModel negation = LocalPatternLanguageParser.parse("Maine 500 kamaye nahi, kharch kiye");
        assertEquals("expense", negation.transactionType);
        assertEquals(50000, negation.amountMinor);
    }

    @Test
    public void testModelManagerConstants() {
        assertEquals("qwen-0.6b-instruct-q4_k_m.gguf", ModelManager.MODEL_FILE_NAME);
        assertTrue(ModelManager.EXPECTED_MODEL_SIZE_BYTES > 300L * 1024L * 1024L);
        assertEquals(1024L * 1024L * 1024L, ModelManager.MIN_REQUIRED_FREE_STORAGE_BYTES);
    }
}
