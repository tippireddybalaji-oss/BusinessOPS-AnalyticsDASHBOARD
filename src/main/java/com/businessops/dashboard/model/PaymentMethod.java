package com.businessops.dashboard.model;

/**
 * Payment channel utilized for a financial transaction.
 */
public enum PaymentMethod {
    CASH,
    BANK_TRANSFER,
    UPI,
    CARD,
    CHEQUE,
    OTHER;

    public static final PaymentMethod DEFAULT = BANK_TRANSFER;

    /**
     * Parses a string into a {@link PaymentMethod}, defaulting to {@link #DEFAULT} if null or blank.
     *
     * @param method raw input string
     * @return matching enum value or DEFAULT
     * @throws IllegalArgumentException if the method is invalid
     */
    public static PaymentMethod fromString(String method) {
        if (method == null || method.isBlank()) {
            return DEFAULT;
        }
        try {
            return PaymentMethod.valueOf(method.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid payment method: '" + method + "'. Allowed values: CASH, BANK_TRANSFER, UPI, CARD, CHEQUE, OTHER"
            );
        }
    }
}

