package com.smarttracker.core.common;

import org.junit.Test;
import static org.junit.Assert.*;

public class FinancialEngineTest {

    @Test
    public void testParseCurrencyToMinor() {
        assertEquals(25000L, FinancialEngine.parseCurrencyToMinor("250"));
        assertEquals(25050L, FinancialEngine.parseCurrencyToMinor("250.50"));
        assertEquals(1050000L, FinancialEngine.parseCurrencyToMinor("10500"));
        assertEquals(0L, FinancialEngine.parseCurrencyToMinor(""));
        assertEquals(0L, FinancialEngine.parseCurrencyToMinor(null));
    }

    @Test
    public void testFormatMinorToSimpleINR() {
        assertEquals("₹ 250", FinancialEngine.formatMinorToSimpleINR(25000L));
        assertEquals("₹ 10,500", FinancialEngine.formatMinorToSimpleINR(1050000L));
        assertEquals("₹ 250.50", FinancialEngine.formatMinorToSimpleINR(25050L));
    }
}
