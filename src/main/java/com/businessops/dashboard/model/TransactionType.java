package com.businessops.dashboard.model;

/**
 * Type classification of a financial transaction.
 */
public enum TransactionType {
    INCOME,
    EXPENSE;

    /**
     * Parses a string into a {@link TransactionType}.
     *
     * @param type raw input string
     * @return matching enum value
     * @throws IllegalArgumentException if the type is null, blank, or not one of INCOME, EXPENSE
     */
    public static TransactionType fromString(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Transaction type cannot be blank");
        }
        try {
            return TransactionType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid transaction type: '" + type + "'. Allowed values: INCOME, EXPENSE"
            );
        }
    }
}

