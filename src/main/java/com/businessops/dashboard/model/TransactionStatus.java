package com.businessops.dashboard.model;

/**
 * Settlement lifecycle status of a financial transaction.
 */
public enum TransactionStatus {
    COMPLETED,
    PENDING,
    CANCELLED;

    public static final TransactionStatus DEFAULT = COMPLETED;

    /**
     * Parses a string into a {@link TransactionStatus}, defaulting to {@link #DEFAULT} if null or blank.
     *
     * @param status raw input string
     * @return matching enum value or DEFAULT
     * @throws IllegalArgumentException if the status is invalid
     */
    public static TransactionStatus fromString(String status) {
        if (status == null || status.isBlank()) {
            return DEFAULT;
        }
        try {
            return TransactionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid transaction status: '" + status + "'. Allowed values: COMPLETED, PENDING, CANCELLED"
            );
        }
    }
}

