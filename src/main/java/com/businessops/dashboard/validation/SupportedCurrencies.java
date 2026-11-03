package com.businessops.dashboard.validation;

import java.util.Set;

/**
 * Currency validation and normalization utility.
 *
 * <p>Enforces INR as the system default currency while supporting
 * standard ISO-4217 currencies for future multi-currency operations.
 */
public final class SupportedCurrencies {

    public static final String DEFAULT_CURRENCY = "INR";

    public static final Set<String> SUPPORTED = Set.of(
            "INR", "USD", "EUR", "GBP", "AUD", "CAD", "SGD", "AED", "JPY"
    );

    private SupportedCurrencies() {
        // Utility class
    }

    public static boolean isValid(String currency) {
        if (currency == null || currency.isBlank()) {
            return false;
        }
        return SUPPORTED.contains(currency.trim().toUpperCase());
    }

    /**
     * Normalizes the currency code to uppercase, or returns the default (INR)
     * if the input is null or blank.
     *
     * @param currency raw currency string
     * @return normalized currency code
     * @throws IllegalArgumentException if the currency is not supported
     */
    public static String normalizeOrDefault(String currency) {
        if (currency == null || currency.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        String normalized = currency.trim().toUpperCase();
        if (!SUPPORTED.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported currency: '" + currency + "'. Supported currencies are: " + SUPPORTED
            );
        }
        return normalized;
    }
}
