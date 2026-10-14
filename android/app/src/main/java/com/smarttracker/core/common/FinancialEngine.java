package com.smarttracker.core.common;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Single source of truth for financial rules:
 * - All internal values stored as minor units (paise: e.g. 25000 = ₹250.00).
 * - Zero floating point arithmetic in balance computations.
 */
public final class FinancialEngine {

    private FinancialEngine() {}

    public static long parseCurrencyToMinor(String amountText) {
        if (amountText == null || amountText.trim().isEmpty()) {
            return 0;
        }
        String clean = amountText.replaceAll("[^0-9.]", "").trim();
        if (clean.isEmpty()) {
            return 0;
        }
        double amount = Double.parseDouble(clean);
        return Math.round(amount * 100.0);
    }

    public static String formatMinorToINR(long minorUnits) {
        double rupees = minorUnits / 100.0;
        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        return formatter.format(rupees).replace("INR", "₹").trim();
    }

    public static String formatMinorToSimpleINR(long minorUnits) {
        double rupees = minorUnits / 100.0;
        if (minorUnits % 100 == 0) {
            return String.format(Locale.getDefault(), "₹ %,d", minorUnits / 100);
        } else {
            return String.format(Locale.getDefault(), "₹ %,.2f", rupees);
        }
    }
}
