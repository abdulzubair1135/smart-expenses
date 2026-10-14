package com.smarttracker.ui.voice;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocalPatternLanguageParserTest {

    @Test
    public void testEarnedMoneyIncomeClassification() {
        VoiceCommandModel result = LocalPatternLanguageParser.parse("Maine 50 rupaye kamaye.");
        assertNotNull(result);
        assertEquals("CREATE_TRANSACTION", result.intent);
        assertEquals("received", result.transactionType);
        assertEquals(5000, result.amountMinor);
        assertFalse(result.clarificationRequired);
    }

    @Test
    public void testSalaryClassification() {
        VoiceCommandModel result = LocalPatternLanguageParser.parse("Mujhe 500 rupaye salary mili.");
        assertNotNull(result);
        assertEquals("CREATE_TRANSACTION", result.intent);
        assertEquals("received", result.transactionType);
        assertEquals(50000, result.amountMinor);
        assertEquals("Salary", result.categoryName);
    }

    @Test
    public void testExpenseClassification() {
        VoiceCommandModel result = LocalPatternLanguageParser.parse("Maine 250 rupaye kharch kiye.");
        assertNotNull(result);
        assertEquals("CREATE_TRANSACTION", result.intent);
        assertEquals("expense", result.transactionType);
        assertEquals(25000, result.amountMinor);
    }

    @Test
    public void testLendClassification() {
        VoiceCommandModel result = LocalPatternLanguageParser.parse("Maine Ali ko 200 rupaye udhaar diye.");
        assertNotNull(result);
        assertEquals("CREATE_TRANSACTION", result.intent);
        assertEquals("lend", result.transactionType);
        assertEquals(20000, result.amountMinor);
        assertEquals("Ali", result.recipient);
    }

    @Test
    public void testBorrowClassification() {
        VoiceCommandModel result = LocalPatternLanguageParser.parse("Maine Ali se 200 rupaye udhaar liye.");
        assertNotNull(result);
        assertEquals("CREATE_TRANSACTION", result.intent);
        assertEquals("borrow", result.transactionType);
        assertEquals(20000, result.amountMinor);
        assertEquals("Ali", result.recipient);
    }

    @Test
    public void testLoanRepaymentReceivedClassification() {
        VoiceCommandModel result = LocalPatternLanguageParser.parse("Ali ne mere 500 rupaye wapas kiye.");
        assertNotNull(result);
        assertEquals("CREATE_TRANSACTION", result.intent);
        assertEquals("received", result.transactionType);
        assertEquals(50000, result.amountMinor);
        assertEquals("Ali", result.recipient);
        assertTrue(result.description.contains("Loan Repayment Received"));
    }

    @Test
    public void testTransferClassification() {
        VoiceCommandModel result = LocalPatternLanguageParser.parse("Bank se wallet mein 1000 transfer kiye.");
        assertNotNull(result);
        assertEquals("CREATE_TRANSACTION", result.intent);
        assertEquals("transfer", result.transactionType);
        assertEquals(100000, result.amountMinor);
    }

    @Test
    public void testAmbiguousGivenMoneyRequiresClarification() {
        VoiceCommandModel result = LocalPatternLanguageParser.parse("Maine paise diye.");
        assertNotNull(result);
        assertTrue(result.clarificationRequired);
        assertNotNull(result.clarificationQuestion);
        assertTrue(result.clarificationQuestion.contains("kis purpose ke liye"));
    }

    @Test
    public void testAmbiguousReceivedMoneyRequiresClarification() {
        VoiceCommandModel result = LocalPatternLanguageParser.parse("Mujhe paise mile.");
        assertNotNull(result);
        assertTrue(result.clarificationRequired);
        assertNotNull(result.clarificationQuestion);
        assertTrue(result.clarificationQuestion.contains("kis roop mein mile"));
    }

    @Test
    public void testNegationCorrectionClassifiedAsExpense() {
        VoiceCommandModel result = LocalPatternLanguageParser.parse("Maine 500 kamaye nahi, kharch kiye.");
        assertNotNull(result);
        assertEquals("CREATE_TRANSACTION", result.intent);
        assertEquals("expense", result.transactionType);
        assertEquals(50000, result.amountMinor);
    }

    @Test
    public void testCorrectionRequestDoesNotCreateIncome() {
        VoiceCommandModel result = LocalPatternLanguageParser.parse("Maine income record kiya tha, ab use correct karo.");
        assertNotNull(result);
        assertTrue(result.clarificationRequired);
        assertNotEquals("CREATE_TRANSACTION", result.intent);
    }

    @Test
    public void testMissingAmountRequiresClarification() {
        VoiceCommandModel result = LocalPatternLanguageParser.parse("Maine auto ka kiraya diya.");
        assertNotNull(result);
        assertTrue(result.clarificationRequired);
    }
}
