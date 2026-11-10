package com.businessops.dashboard.repository;

import com.businessops.dashboard.model.FinancialSummaryAggregate;
import com.businessops.dashboard.model.Transaction;
import com.businessops.dashboard.model.TransactionStreamBatch;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing financial {@link Transaction} documents in Cloud Firestore.
 *
 * <p>All documents reside strictly at {@code workspaces/{workspaceId}/transactions/{transactionId}}.
 */
public interface TransactionRepository {

    /**
     * Persists a new transaction within a workspace.
     *
     * <p>Executes inside a Firestore transaction to validate category and optional client
     * references transactionally without mutating the category document.
     *
     * @param workspaceId target workspace ID
     * @param transaction transaction entity to persist
     * @return created transaction with generated ID, timestamps, and cached display values
     */
    Transaction createTransaction(String workspaceId, Transaction transaction);

    /**
     * Retrieves an individual transaction by workspace and transaction ID.
     *
     * @param workspaceId target workspace ID
     * @param transactionId target transaction ID
     * @return Optional containing the transaction if found, or empty Optional
     */
    Optional<Transaction> findById(String workspaceId, String transactionId);

    /**
     * Updates an existing transaction within a workspace.
     *
     * <p>Executes inside a Firestore transaction, preserving original creation metadata
     * and re-validating category and client references transactionally.
     *
     * @param workspaceId target workspace ID
     * @param transaction transaction entity with updated fields
     * @return updated transaction entity
     */
    Transaction updateTransaction(String workspaceId, Transaction transaction);

    /**
     * Deletes a transaction document by workspace and transaction ID.
     *
     * @param workspaceId target workspace ID
     * @param transactionId target transaction ID
     * @return true if deleted, false if not found
     */
    boolean deleteTransaction(String workspaceId, String transactionId);

    /**
     * Retrieves transactions matching structured equality and date range filters,
     * ordered by {@code transactionDate DESC}.
     *
     * @param workspaceId target workspace ID
     * @param type optional transaction type (INCOME or EXPENSE)
     * @param categoryId optional category ID
     * @param clientId optional client ID
     * @param startDate optional start date (inclusive)
     * @param endDate optional end date (inclusive)
     * @param limit optional candidate limit (e.g. 501 for sentinel probe)
     * @return list of matching transactions
     */
    List<Transaction> findTransactions(String workspaceId,
                                       String type,
                                       String categoryId,
                                       String clientId,
                                       Timestamp startDate,
                                       Timestamp endDate,
                                       Integer limit);

    // ── PHASE 5 ANALYTICS & AGGREGATION ──────────────────────────────────────────

    /**
     * Executes server-side native Firestore aggregations (sum and count) for executive summary KPIs.
     *
     * @param workspaceId target workspace ID
     * @param currency target reporting currency code (C_rep)
     * @param startInclusive inclusive start boundary
     * @param endExclusive exclusive end boundary
     * @param includePending whether to aggregate pending pipeline transactions
     * @return aggregated financial scalar totals and counts
     */
    FinancialSummaryAggregate aggregateFinancialSummary(
            String workspaceId,
            String currency,
            Instant startInclusive,
            Instant endExclusive,
            boolean includePending
    );

    /**
     * Executes server-side count queries to determine total matching transactions across the request.
     *
     * @param workspaceId target workspace ID
     * @param currency target reporting currency code (C_rep)
     * @param startInclusive inclusive start boundary
     * @param endExclusive exclusive end boundary
     * @param includePending whether to count pending transactions
     * @return count of eligible matching transactions
     */
    long countEligibleTransactions(
            String workspaceId,
            String currency,
            Instant startInclusive,
            Instant endExclusive,
            boolean includePending
    );

    /**
     * Discovers all active currencies within the workspace and date boundaries via cursor-paginated scan.
     * Never returns a partial list. Incurs O(N) document read costs across the reporting window.
     *
     * @param workspaceId target workspace ID
     * @param startInclusive inclusive start boundary
     * @param endExclusive exclusive end boundary
     * @param includePending whether to include pending transactions
     * @return alphabetically sorted list of distinct ISO-4217 currency codes
     */
    List<String> discoverCurrencies(
            String workspaceId,
            Instant startInclusive,
            Instant endExclusive,
            boolean includePending
    );

    /**
     * Fetches a single cursor-paginated batch of selectively projected transaction items for a specific status.
     *
     * @param workspaceId target workspace ID
     * @param currency target reporting currency code (C_rep)
     * @param status status filter ("COMPLETED" or "PENDING")
     * @param startInclusive inclusive start boundary
     * @param endExclusive exclusive end boundary
     * @param batchSize maximum documents to fetch in this batch
     * @param startAfterDocument optional cursor document snapshot to resume from (null for first batch)
     * @return batch of mapped transaction stream items, last document snapshot, and hasMore flag
     */
    TransactionStreamBatch fetchStreamBatch(
            String workspaceId,
            String currency,
            String status,
            Instant startInclusive,
            Instant endExclusive,
            int batchSize,
            QueryDocumentSnapshot startAfterDocument
    );
}

