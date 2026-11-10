package com.businessops.dashboard.dto;

import java.time.Instant;
import java.util.List;

/**
 * Category analytics response adhering to Section 9.4 of Phase 5 specification.
 */
public record CategoryAnalyticsResponse(
    String workspaceId,
    String currency,
    int fractionDigits,
    Instant startInclusive,
    Instant endExclusive,
    String reportingTimezone,
    // Realized Header Metrics (Always reports combined totals for currency and period)
    Long totalExpenseMinor,
    String formattedTotalExpense,
    Long totalIncomeMinor,
    String formattedTotalIncome,
    // Pipeline Header Metrics (0L / formatted "0.00" when includePending = false)
    Long pendingExpenseMinor,
    String formattedPendingExpense,
    Long pendingIncomeMinor,
    String formattedPendingIncome,
    List<CategoryPerformanceItem> categories,
    List<String> availableCurrencies,
    CompletenessMetadata completeness
) {}

