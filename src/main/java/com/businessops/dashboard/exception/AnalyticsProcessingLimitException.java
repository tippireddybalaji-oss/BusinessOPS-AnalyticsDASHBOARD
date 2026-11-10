package com.businessops.dashboard.exception;

/**
 * Exception thrown when an analytics query exceeds the 10,000-document processing limit.
 *
 * <p>Translates to HTTP 422 Unprocessable Content with error code
 * {@code ANALYTICS_PROCESSING_LIMIT_EXCEEDED} in the REST presentation layer.
 */
public class AnalyticsProcessingLimitException extends RuntimeException {

    private static final String DEFAULT_MESSAGE =
            "The requested date range exceeds the dimensional breakdown limit of 10,000 transactions. " +
            "Please narrow your date range to an interval with fewer transactions (e.g., a single month or quarter).";

    private final LimitSafeguardType safeguardType;
    private final long processedCount;
    private final Long totalEligibleCount;
    private final int maxAllowedCount;

    public AnalyticsProcessingLimitException(LimitSafeguardType safeguardType,
                                             long processedCount,
                                             Long totalEligibleCount) {
        super(DEFAULT_MESSAGE);
        this.safeguardType = safeguardType;
        this.processedCount = processedCount;
        this.totalEligibleCount = totalEligibleCount;
        this.maxAllowedCount = 10000;
    }

    public AnalyticsProcessingLimitException(LimitSafeguardType safeguardType,
                                             long processedCount,
                                             Long totalEligibleCount,
                                             String message) {
        super(message);
        this.safeguardType = safeguardType;
        this.processedCount = processedCount;
        this.totalEligibleCount = totalEligibleCount;
        this.maxAllowedCount = 10000;
    }

    public LimitSafeguardType getSafeguardType() {
        return safeguardType;
    }

    public long getProcessedCount() {
        return processedCount;
    }

    public Long getTotalEligibleCount() {
        return totalEligibleCount;
    }

    public int getMaxAllowedCount() {
        return maxAllowedCount;
    }
}

