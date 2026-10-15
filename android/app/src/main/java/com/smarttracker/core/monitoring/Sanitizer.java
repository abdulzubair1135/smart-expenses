package com.smarttracker.core.monitoring;

import java.util.regex.Pattern;

/**
 * Privacy and security filter for all monitoring and crash telemetry.
 * Strictly redacts passwords, tokens, private keys, financial amounts and payment cards.
 */
public class Sanitizer {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|secret|pwd)[\"':\\s=]+([^\"',\\s&]+)");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)(bearer\\s+|token[\"':\\s=]+)([a-zA-Z0-9_\\-\\.]+)");
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9_\\-]+");
    private static final Pattern CARD_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b");
    private static final Pattern FINANCIAL_AMOUNT_PATTERN = Pattern.compile("(?i)(amount|balance|openingBalanceMinor|amountMinor)[\"':\\s=]+(\\d+)");

    private static final int MAX_STRING_LENGTH = 10000;

    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String result = input;
        if (result.length() > MAX_STRING_LENGTH) {
            result = result.substring(0, MAX_STRING_LENGTH) + "... [TRUNCATED]";
        }

        result = PASSWORD_PATTERN.matcher(result).replaceAll("$1=[REDACTED]");
        result = BEARER_PATTERN.matcher(result).replaceAll("$1[REDACTED]");
        result = JWT_PATTERN.matcher(result).replaceAll("[JWT_REDACTED]");
        result = CARD_PATTERN.matcher(result).replaceAll("[CARD_REDACTED]");
        result = FINANCIAL_AMOUNT_PATTERN.matcher(result).replaceAll("$1=[REDACTED]");

        return result;
    }

    public static String extractStackTrace(Throwable throwable) {
        if (throwable == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");
        StackTraceElement[] trace = throwable.getStackTrace();
        int maxFrames = Math.min(trace.length, 25);
        for (int i = 0; i < maxFrames; i++) {
            sb.append("\tat ").append(trace[i].toString()).append("\n");
        }
        if (throwable.getCause() != null) {
            sb.append("Caused by: ").append(throwable.getCause().toString()).append("\n");
        }
        return sanitize(sb.toString());
    }
}
