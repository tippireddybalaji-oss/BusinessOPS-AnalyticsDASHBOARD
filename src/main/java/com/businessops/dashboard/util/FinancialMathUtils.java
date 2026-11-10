package com.businessops.dashboard.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure mathematical utilities and precision invariants for Phase 5 Financial Analytics.
 *
 * <p>Architectural Responsibilities:
 * <ul>
 *   <li>64-bit integer arithmetic for monetary units via {@link Math#addExact(long, long)} and {@link Math#subtractExact(long, long)}.</li>
 *   <li>Throws {@link ArithmeticException} on integer overflow (translated into {@code DatabaseOperationException} by service layer).</li>
 *   <li>{@link BigDecimal} with scale 2 and {@link RoundingMode#HALF_UP} for all quotients, percentages, and averages.</li>
 *   <li>Zero floating-point operations ({@code float} and {@code double} are prohibited).</li>
 *   <li>Responsible strictly for mathematical currency conversion and precision formatting; application-level currency acceptance is isolated in {@link com.businessops.dashboard.validation.SupportedCurrencies} and the request-validation layer.</li>
 * </ul>
 */
public final class FinancialMathUtils {

    private FinancialMathUtils() {
        // Utility class
    }

    /**
     * Safely adds two 64-bit signed minor-unit amounts.
     *
     * @param a first operand
     * @param b second operand
     * @return sum in minor units
     * @throws ArithmeticException on 64-bit signed integer overflow
     */
    public static long safeAdd(long a, long b) {
        return Math.addExact(a, b);
    }

    /**
     * Safely subtracts two 64-bit signed minor-unit amounts.
     *
     * @param a minuend
     * @param b subtrahend
     * @return difference in minor units
     * @throws ArithmeticException on 64-bit signed integer underflow
     */
    public static long safeSubtract(long a, long b) {
        return Math.subtractExact(a, b);
    }

    /**
     * Computes the average transaction value in fractional minor units.
     *
     * @param totalMinor total monetary amount in minor units
     * @param count number of transactions
     * @return average value with scale 2 (RoundingMode.HALF_UP), or 0.00 if count &le; 0
     */
    public static BigDecimal calculateAverageMinor(long totalMinor, long count) {
        if (count <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(totalMinor)
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    /**
     * Computes a generic ratio as a percentage (e.g., share of total).
     *
     * @param numerator portion amount
     * @param denominator total amount
     * @return percentage ratio with scale 2 (RoundingMode.HALF_UP), or 0.00 if denominator &le; 0
     */
    public static BigDecimal calculatePercentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    /**
     * Determines whether profit margin is mathematically computable according to Section 2.2.
     *
     * @param revenueMinor realized revenue in minor units (null treated as 0L)
     * @param expenseMinor realized expense in minor units (null treated as 0L)
     * @return {@code false} if revenue is 0 and expense &gt; 0; {@code true} in all other cases
     */
    public static boolean isMarginComputable(Long revenueMinor, Long expenseMinor) {
        long rev = revenueMinor != null ? revenueMinor : 0L;
        long exp = expenseMinor != null ? expenseMinor : 0L;
        if (rev > 0) {
            return true;
        }
        // When revenue is 0: computable only if expenses are also 0 (margin = 0.00%)
        return exp <= 0;
    }

    /**
     * Calculates the profit margin percentage according to Section 2.2:
     * <pre>
     * margin = ((revenue - expense) * 100) / revenue
     * </pre>
     *
     * <p>Behavioral Truth Table:
     * <ul>
     *   <li>Revenue &gt; 0: computes {@code ((revenue - expense) * 100) / revenue} at scale 2 HALF_UP</li>
     *   <li>Revenue = 0, Expense = 0: returns {@code 0.00}</li>
     *   <li>Revenue = 0, Expense &gt; 0: returns {@code null} (undefined / incalculable)</li>
     * </ul>
     *
     * @param revenueMinor realized revenue in minor units (null treated as 0L)
     * @param expenseMinor realized expense in minor units (null treated as 0L)
     * @return profit margin percentage with scale 2, or {@code null} when incalculable
     * @throws ArithmeticException on 64-bit signed integer subtraction overflow
     */
    public static BigDecimal calculateProfitMargin(Long revenueMinor, Long expenseMinor) {
        long rev = revenueMinor != null ? revenueMinor : 0L;
        long exp = expenseMinor != null ? expenseMinor : 0L;

        if (rev > 0) {
            long netProfitMinor = safeSubtract(rev, exp);
            return BigDecimal.valueOf(netProfitMinor)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(rev), 2, RoundingMode.HALF_UP);
        }

        if (exp == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // Zero revenue with positive expenses: undefined profit margin
        return null;
    }

    /**
     * Calculates projected net profit taking pending pipeline items into account:
     * <pre>
     * projectedNetProfitMinor = netProfitMinor + pendingIncomeMinor - pendingExpenseMinor
     * </pre>
     *
     * @param netProfitMinor realized net profit
     * @param pendingIncomeMinor pending pipeline income
     * @param pendingExpenseMinor pending pipeline expense
     * @return projected net profit in minor units
     * @throws ArithmeticException on 64-bit signed integer arithmetic overflow
     */
    public static long calculateProjectedNetProfit(long netProfitMinor, long pendingIncomeMinor, long pendingExpenseMinor) {
        return safeSubtract(safeAdd(netProfitMinor, pendingIncomeMinor), pendingExpenseMinor);
    }

    /**
     * Formats an average amount (expressed in fractional minor units) into a major-unit plain decimal string.
     *
     * <p>Queries {@link CurrencyUtils#getFractionDigits(String)} to determine native decimal scale,
     * dividing by {@code 10^fractionDigits} directly at scale {@code fractionDigits} with {@link RoundingMode#HALF_UP}.
     *
     * @param averageMinor fractional minor unit amount
     * @param currencyCode standard ISO-4217 currency code
     * @return formatted plain decimal string (e.g. "12.35" for USD, "1235" for JPY), or null if averageMinor is null
     */
    public static String formatAverageAmount(BigDecimal averageMinor, String currencyCode) {
        if (averageMinor == null) {
            return null;
        }
        int fractionDigits = CurrencyUtils.getFractionDigits(currencyCode);
        BigDecimal divisor = BigDecimal.TEN.pow(fractionDigits);
        return averageMinor.divide(divisor, fractionDigits, RoundingMode.HALF_UP).toPlainString();
    }
}

