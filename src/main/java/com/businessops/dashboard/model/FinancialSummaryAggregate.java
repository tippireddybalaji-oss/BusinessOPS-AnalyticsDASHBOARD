package com.businessops.dashboard.model;

/**
 * Encapsulates raw scalar sums and counts retrieved from server-side native Firestore aggregation queries.
 *
 * @param totalRevenueMinor sum of realized income in minor units
 * @param incomeTransactionCount count of realized income transactions
 * @param totalExpenseMinor sum of realized expenses in minor units
 * @param expenseTransactionCount count of realized expense transactions
 * @param pendingIncomeMinor sum of pending pipeline income in minor units
 * @param pendingIncomeTransactionCount count of pending income transactions
 * @param pendingExpenseMinor sum of pending pipeline expenses in minor units
 * @param pendingExpenseTransactionCount count of pending expense transactions
 */
public record FinancialSummaryAggregate(
    long totalRevenueMinor,
    long incomeTransactionCount,
    long totalExpenseMinor,
    long expenseTransactionCount,
    long pendingIncomeMinor,
    long pendingIncomeTransactionCount,
    long pendingExpenseMinor,
    long pendingExpenseTransactionCount
) {
    public static FinancialSummaryAggregate empty() {
        return new FinancialSummaryAggregate(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }
}

