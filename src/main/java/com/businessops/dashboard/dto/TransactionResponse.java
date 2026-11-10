package com.businessops.dashboard.dto;

import com.businessops.dashboard.model.Transaction;
import com.businessops.dashboard.util.CurrencyUtils;

import java.time.Instant;

/**
 * Standardized response contract for transaction data.
 *
 * <p>Provides both raw integer minor units ({@code amountMinor}) for financial precision
 * and {@code formattedAmount} based on currency-specific fraction digits.
 */
public record TransactionResponse(
        String id,
        String workspaceId,
        String type,
        Long amountMinor,
        String formattedAmount,
        String currency,
        int fractionDigits,
        String categoryId,
        String categoryName,
        String categoryColor,
        String clientId,
        String clientName,
        String description,
        Instant transactionDate,
        String paymentMethod,
        String status,
        String createdByUid,
        String updatedByUid,
        Instant createdAt,
        Instant updatedAt
) {
    public static TransactionResponse from(Transaction tx) {
        if (tx == null) {
            return null;
        }

        String currency = (tx.getCurrency() != null && !tx.getCurrency().isBlank())
                ? tx.getCurrency()
                : "INR";
        int fractionDigits = CurrencyUtils.getFractionDigits(currency);
        String formattedAmount = tx.getAmountMinor() != null
                ? CurrencyUtils.formatAmount(tx.getAmountMinor(), currency)
                : null;

        Instant txnInstant = tx.getTransactionDate() != null
                ? tx.getTransactionDate().toDate().toInstant()
                : Instant.now();
        Instant createdInstant = tx.getCreatedAt() != null
                ? tx.getCreatedAt().toDate().toInstant()
                : Instant.now();
        Instant updatedInstant = tx.getUpdatedAt() != null
                ? tx.getUpdatedAt().toDate().toInstant()
                : Instant.now();

        return new TransactionResponse(
                tx.getId(),
                tx.getWorkspaceId(),
                tx.getType(),
                tx.getAmountMinor(),
                formattedAmount,
                currency,
                fractionDigits,
                tx.getCategoryId(),
                tx.getCategoryName(),
                tx.getCategoryColor(),
                tx.getClientId(),
                tx.getClientName(),
                tx.getDescription(),
                txnInstant,
                tx.getPaymentMethod(),
                tx.getStatus(),
                tx.getCreatedByUid(),
                tx.getUpdatedByUid(),
                createdInstant,
                updatedInstant
        );
    }
}

