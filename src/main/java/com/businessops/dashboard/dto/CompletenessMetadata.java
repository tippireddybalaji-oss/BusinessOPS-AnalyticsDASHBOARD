package com.businessops.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata indicating query processing completeness and boundary limits.
 *
 * @param isComplete whether the aggregation stream completed without truncation or limit abort
 * @param status processing state ("COMPLETE" or "PROCESSING_LIMIT_EXCEEDED")
 * @param processedCount number of documents aggregated during execution
 * @param totalEligibleCount total matching count (null if aborted by runtime streaming cap)
 * @param message diagnostic explanation when execution is incomplete or skew is detected
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompletenessMetadata(
    boolean isComplete,
    String status,
    Long processedCount,
    Long totalEligibleCount,
    String message
) {
    public static CompletenessMetadata complete(Long processedCount, Long totalEligibleCount) {
        return new CompletenessMetadata(true, "COMPLETE", processedCount, totalEligibleCount, null);
    }

    public static CompletenessMetadata limitExceeded(
        Long processedCount,
        Long totalEligibleCount,
        String message
    ) {
        return new CompletenessMetadata(false, "PROCESSING_LIMIT_EXCEEDED", processedCount, totalEligibleCount, message);
    }
}

