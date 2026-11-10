package com.businessops.dashboard.model;

import com.google.cloud.Timestamp;

/**
 * Lean, immutable projection record representing a single transaction document during dimensional streaming.
 *
 * <p>Contains strictly the 11 dimensional attributes required for accumulation in Milestone 3,
 * avoiding over-the-wire transfer and in-memory retention of full Transaction audit and metadata fields.
 *
 * @param id transaction document ID
 * @param type transaction type ("INCOME" or "EXPENSE")
 * @param amountMinor monetary amount in 64-bit integer minor units
 * @param currency ISO-4217 currency code
 * @param status transaction status ("COMPLETED" or "PENDING")
 * @param transactionDate transaction timestamp
 * @param categoryId category ID (null if uncategorized)
 * @param categoryName cached category name (null if uncategorized)
 * @param categoryColor cached category color hex (null if uncategorized)
 * @param clientId client ID (null if direct / unassigned)
 * @param clientName cached client name (null if direct / unassigned)
 */
public record TransactionStreamItem(
    String id,
    String type,
    Long amountMinor,
    String currency,
    String status,
    Timestamp transactionDate,
    String categoryId,
    String categoryName,
    String categoryColor,
    String clientId,
    String clientName
) {}

