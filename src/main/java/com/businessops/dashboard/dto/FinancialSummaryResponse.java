package com.businessops.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Executive financial summary response adhering to Section 9.2 of Phase 5 specification.
 */
public record FinancialSummaryResponse(
    String workspaceId,
    String currency,
    int fractionDigits,
    Instant startInclusive,
    Instant endExclusive,
    String reportingTimezone,
    // Realized Metrics
    Long totalRevenueMinor,
    String formattedRevenue,
    Long totalExpenseMinor,
    String formattedExpense,
    Long netProfitMinor,
    String formattedNetProfit,
    BigDecimal profitMarginPercentage,
    boolean isMarginComputable,
    long totalCompletedTransactions,
    long incomeTransactionCount,
    long expenseTransactionCount,
    // Pipeline Metrics (0L / formatted "0.00" when includePending = false)
    Long pendingIncomeMinor,
    String formattedPendingIncome,
    Long pendingExpenseMinor,
    String formattedPendingExpense,
    Long projectedNetProfitMinor, // equals netProfitMinor when includePending = false
    String formattedProjectedNetProfit,
    long pendingTransactionCount, // 0L when includePending = false
    // Discovered Currencies & Metadata
    List<String> availableCurrencies,
    CompletenessMetadata completeness
) {}

