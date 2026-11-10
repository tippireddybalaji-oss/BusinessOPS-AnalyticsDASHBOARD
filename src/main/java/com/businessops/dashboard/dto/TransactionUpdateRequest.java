package com.businessops.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request payload for updating an existing transaction.
 */
public record TransactionUpdateRequest(
        @NotBlank(message = "Transaction type cannot be blank")
        String type,

        @NotNull(message = "Transaction amount cannot be null")
        @Positive(message = "Transaction amount must be greater than zero")
        Long amountMinor,

        String currency,

        @NotBlank(message = "Category ID cannot be blank")
        String categoryId,

        String clientId,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

        Instant transactionDate,

        String paymentMethod,

        String status
) {
}

