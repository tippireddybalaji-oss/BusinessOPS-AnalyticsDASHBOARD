package com.businessops.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Time-series trend item representing a single aggregated period bucket (Section 9.3).
 */
public record TrendPeriodItem(
    String period, // e.g. "2026-01" (MONTHLY) or "2026" (YEARLY)
    Instant startInclusive,
    Instant endExclusive,
    // Realized Metrics
    Long revenueMinor,
    String formattedRevenue,
    Long expenseMinor,
    String formattedExpense,
    Long netProfitMinor,
    String formattedNetProfit,
    BigDecimal profitMarginPercentage,
    long transactionCount,
    // Pipeline Metrics (0L / formatted "0.00" when includePending = false)
    Long pendingIncomeMinor,
    String formattedPendingIncome,
    Long pendingExpenseMinor,
    String formattedPendingExpense,
    Long projectedNetProfitMinor, // equals netProfitMinor when includePending = false
    String formattedProjectedNetProfit,
    long pendingTransactionCount // 0L when includePending = false
) {}

