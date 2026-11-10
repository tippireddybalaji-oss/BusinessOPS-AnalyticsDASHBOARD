package com.businessops.dashboard.dto;

import java.time.Instant;
import java.util.List;

/**
 * Financial trends response adhering to Section 9.3 of Phase 5 specification.
 */
public record FinancialTrendsResponse(
    String workspaceId,
    String currency,
    int fractionDigits,
    String interval, // Strictly "MONTHLY" or "YEARLY"
    Instant startInclusive,
    Instant endExclusive,
    String reportingTimezone,
    List<TrendPeriodItem> periods,
    List<String> availableCurrencies,
    CompletenessMetadata completeness
) {}

