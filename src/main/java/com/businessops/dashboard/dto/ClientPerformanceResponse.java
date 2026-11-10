package com.businessops.dashboard.dto;

import java.time.Instant;
import java.util.List;

/**
 * Client performance response adhering to Section 9.5 of Phase 5 specification.
 */
public record ClientPerformanceResponse(
    String workspaceId,
    String currency,
    int fractionDigits,
    Instant startInclusive,
    Instant endExclusive,
    String reportingTimezone,
    // Realized Header Metrics
    Long totalRealizedRevenueMinor,
    String formattedTotalRevenue,
    Long unassignedClientRevenueMinor, // Transactions where clientId is null/blank
    String formattedUnassignedRevenue,
    // Pipeline Header Metrics (0L / formatted "0.00" when includePending = false)
    Long totalPendingRevenueMinor,
    String formattedTotalPendingRevenue,
    List<ClientPerformanceItem> clients,
    List<String> availableCurrencies,
    CompletenessMetadata completeness
) {}

