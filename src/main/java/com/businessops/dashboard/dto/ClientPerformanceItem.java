package com.businessops.dashboard.dto;

import java.math.BigDecimal;

/**
 * Client performance item adhering to Section 9.5 of Phase 5 specification.
 */
public record ClientPerformanceItem(
    String clientId,
    String clientName,
    // Realized Metrics
    Long totalRevenueMinor,
    String formattedTotalRevenue,
    long incomeTransactionCount,
    BigDecimal averageTransactionValueMinor, // Fractional minor units, Scale 2, RoundingMode.HALF_UP
    String formattedAverageValue,
    BigDecimal percentageOfTotalRevenue, // Scale 2, RoundingMode.HALF_UP
    // Pipeline Metrics (0L / formatted "0.00" when includePending = false)
    Long pendingRevenueMinor,
    String formattedPendingRevenue,
    long pendingIncomeTransactionCount // 0L when includePending = false
) {}

