package com.businessops.dashboard.util;

import com.businessops.dashboard.validation.SupportedCurrencies;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Monetary precision, parsing, and formatting utilities adhering strictly to ISO-4217 minor-unit conventions.
 *
 * <p>Key Invariants:
 * <ul>
 *   <li>All internal monetary values are stored and calculated as 64-bit signed integers ({@code Long amountMinor}).</li>
 *   <li>Binary floating-point types ({@code float}, {@code double}) are <strong>never</strong> used for financial calculations.</li>
 *   <li>Decimal conversions use {@link BigDecimal} with explicit scale and {@link RoundingMode#UNNECESSARY}.</li>
 *   <li>Zero-decimal currencies (such as {@code JPY}) have a fraction digit count of 0 (factor = 1).</li>
 *   <li>Standard currencies ({@code INR}, {@code USD}, {@code EUR}, etc.) have 2 fraction digits (factor = 100).</li>
 * </ul>
 */
public final class CurrencyUtils {

    /**
     * ISO-4217 fraction digits for all supported currencies.
     */
    private static final Map<String, Integer> FRACTION_DIGITS = Map.of(
            "INR", 2,
            "USD", 2,
            "EUR", 2,
            "GBP", 2,
            "AUD", 2,
            "CAD", 2,
            "SGD", 2,
            "AED", 2,
            "JPY", 0
    );

    private CurrencyUtils() {
        // Utility class
    }

    /**
     * Resolves the ISO-4217 fraction digits (number of decimal places) for a supported currency.
     *
     * @param currencyCode standard ISO-4217 currency code (null/blank defaults to INR)
     * @return number of fraction digits (e.g. 2 for INR/USD, 0 for JPY)
     * @throws IllegalArgumentException if the currency code is unsupported
     */
    public static int getFractionDigits(String currencyCode) {
        String normalized = SupportedCurrencies.normalizeOrDefault(currencyCode);
        Integer digits = FRACTION_DIGITS.get(normalized);
        if (digits == null) {
            throw new IllegalArgumentException("Unsupported currency: '" + currencyCode + "'");
        }
        return digits;
    }

    /**
     * Formats an integer minor-unit amount into its standardized decimal string representation.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code formatAmount(2500000L, "INR")} &rarr; {@code "25000.00"}</li>
     *   <li>{@code formatAmount(250050L, "INR")} &rarr; {@code "2500.50"}</li>
     *   <li>{@code formatAmount(1L, "INR")} &rarr; {@code "0.01"}</li>
     *   <li>{@code formatAmount(5000L, "JPY")} &rarr; {@code "5000"}</li>
     * </ul>
     *
     * @param amountMinor amount in integer minor units (e.g. paise for INR, cents for USD, yen for JPY)
     * @param currencyCode target currency code (null/blank defaults to INR)
     * @return formatted plain decimal string matching the currency's exact fraction digits
     * @throws IllegalArgumentException if currency is unsupported
     */
    public static String formatAmount(long amountMinor, String currencyCode) {
        int fractionDigits = getFractionDigits(currencyCode);
        BigDecimal minor = BigDecimal.valueOf(amountMinor);
        BigDecimal divisor = BigDecimal.TEN.pow(fractionDigits);
        BigDecimal decimal = minor.divide(divisor, fractionDigits, RoundingMode.UNNECESSARY);
        return decimal.toPlainString();
    }

    /**
     * Parses a decimal string amount into integer minor units for financial storage.
     *
     * <p>Enforces:
     * <ul>
     *   <li>Non-null, non-blank decimal input.</li>
     *   <li>Valid supported currency.</li>
     *   <li>Positive non-zero amount (&gt; 0).</li>
     *   <li>Scale cannot exceed the currency's ISO-4217 fraction digits (e.g. rejects "10.555" for INR, "10.5" for JPY).</li>
     *   <li>Numeric overflow detection (rejects values exceeding {@link Long#MAX_VALUE}).</li>
     * </ul>
     *
     * @param decimalStr decimal string representation (e.g. "2500.50")
     * @param currencyCode target currency code
     * @return parsed amount in integer minor units
     * @throws IllegalArgumentException on null/blank input, invalid format, non-positive value, excess precision, or overflow
     */
    public static long parseToMinor(String decimalStr, String currencyCode) {
        if (decimalStr == null || decimalStr.isBlank()) {
            throw new IllegalArgumentException("Monetary amount cannot be blank");
        }

        String normalizedCurrency = SupportedCurrencies.normalizeOrDefault(currencyCode);
        int fractionDigits = getFractionDigits(normalizedCurrency);

        BigDecimal decimal;
        try {
            decimal = new BigDecimal(decimalStr.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid decimal monetary amount: '" + decimalStr + "'", e);
        }

        // Validate positive non-zero amount
        if (decimal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be strictly positive (> 0), got: " + decimalStr);
        }

        // Validate scale / excess precision
        // Note: BigDecimal("10") has scale 0; BigDecimal("10.5") has scale 1; BigDecimal("10.55") has scale 2.
        if (decimal.scale() > fractionDigits) {
            throw new IllegalArgumentException(
                    "Excess decimal precision: currency " + normalizedCurrency
                            + " allows at most " + fractionDigits
                            + " decimal places, but got: '" + decimalStr + "'"
            );
        }

        // Scale to minor units
        BigDecimal minorUnits = decimal.movePointRight(fractionDigits);

        // Enforce integer minor units & detect 64-bit overflow
        try {
            return minorUnits.longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Numeric overflow: amount '" + decimalStr + "' exceeds 64-bit integer limit", e);
        }
    }

    /**
     * Validates that an amountMinor value is strictly positive (&gt; 0).
     *
     * @param amountMinor value to check
     * @throws IllegalArgumentException if amount is null or &le; 0
     */
    public static void validatePositiveAmount(Long amountMinor) {
        if (amountMinor == null) {
            throw new IllegalArgumentException("Transaction amountMinor cannot be null");
        }
        if (amountMinor <= 0L) {
            throw new IllegalArgumentException("Transaction amountMinor must be greater than zero, got: " + amountMinor);
        }
    }
}

