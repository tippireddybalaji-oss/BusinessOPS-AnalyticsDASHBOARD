package com.businessops.dashboard.dto;

import java.math.BigDecimal;

/**
 * Category performance item adhering to Section 9.4 of Phase 5 specification.
 */
public record CategoryPerformanceItem(
    String categoryId,
    String categoryName,
    String categoryColor,
    String type, // "INCOME" or "EXPENSE"
    // Realized Metrics
    Long totalAmountMinor,
    String formattedTotalAmount,
    long transactionCount,
    BigDecimal percentageOfTotal, // Scale 2, RoundingMode.HALF_UP
    // Pipeline Metrics (0L / formatted "0.00" when includePending = false)
    Long pendingAmountMinor,
    String formattedPendingAmount,
    long pendingTransactionCount // 0L when includePending = false
) {}

