package com.businessops.dashboard.repository;

import com.businessops.dashboard.exception.DatabaseOperationException;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.model.ClientStatus;
import com.businessops.dashboard.model.FinancialSummaryAggregate;
import com.businessops.dashboard.model.Transaction;
import com.businessops.dashboard.model.TransactionStreamBatch;
import com.businessops.dashboard.model.TransactionStreamItem;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Cloud Firestore implementation of {@link TransactionRepository}.
 *
 * <p>All documents reside strictly at {@code workspaces/{workspaceId}/transactions/{transactionId}}.
 */
@Repository
public class FirestoreTransactionRepository implements TransactionRepository {

    private static final Logger log = LoggerFactory.getLogger(FirestoreTransactionRepository.class);

    private static final String WORKSPACES_COLLECTION = "workspaces";
    private static final String TRANSACTIONS_SUBCOLLECTION = "transactions";
    private static final String CATEGORIES_SUBCOLLECTION = "categories";
    private static final String CLIENTS_SUBCOLLECTION = "clients";

    private final Firestore firestore;

    public FirestoreTransactionRepository(@Autowired(required = false) Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public Transaction createTransaction(String workspaceId, Transaction transaction) {
        checkFirestoreAvailable();

        try {
            CollectionReference txCollection = getTransactionsCollection(workspaceId);
            DocumentReference txDocRef = (transaction.getId() != null && !transaction.getId().isBlank())
                    ? txCollection.document(transaction.getId())
                    : txCollection.document();

            String txId = txDocRef.getId();
            transaction.setId(txId);
            transaction.setWorkspaceId(workspaceId);

            DocumentReference catDocRef = getCategoriesCollection(workspaceId).document(transaction.getCategoryId());
            DocumentReference clientDocRef = (transaction.getClientId() != null && !transaction.getClientId().isBlank())
                    ? getClientsCollection(workspaceId).document(transaction.getClientId())
                    : null;

            return firestore.runTransaction(transactionContext -> {
                // 1. Transactional read and validation of Category
                DocumentSnapshot catSnap = transactionContext.get(catDocRef).get();
                if (!catSnap.exists()) {
                    throw new ResourceNotFoundException(
                            "Category not found with id: " + transaction.getCategoryId() + " in workspace: " + workspaceId
                    );
                }

                Boolean isArchived = catSnap.getBoolean("isArchived");
                if (Boolean.TRUE.equals(isArchived)) {
                    throw new IllegalArgumentException(
                            "Cannot create transaction with archived category: " + transaction.getCategoryId()
                    );
                }

                String catType = catSnap.getString("type");
                if (catType != null && !catType.equalsIgnoreCase(transaction.getType())) {
                    throw new IllegalArgumentException(
                            "Category type '" + catType + "' does not match transaction type '" + transaction.getType() + "'"
                    );
                }

                transaction.setCategoryName(catSnap.getString("name"));
                transaction.setCategoryColor(catSnap.getString("color"));

                // 2. Transactional read and validation of Client (if present)
                if (clientDocRef != null) {
                    DocumentSnapshot clientSnap = transactionContext.get(clientDocRef).get();
                    if (!clientSnap.exists()) {
                        throw new ResourceNotFoundException(
                                "Client not found with id: " + transaction.getClientId() + " in workspace: " + workspaceId
                        );
                    }

                    String status = clientSnap.getString("status");
                    if (status == null || !ClientStatus.ACTIVE.name().equalsIgnoreCase(status.trim())) {
                        throw new IllegalArgumentException(
                                "Client '" + transaction.getClientId() + "' is not active (status: " + status + ")"
                        );
                    }

                    transaction.setClientName(clientSnap.getString("name"));
                } else {
                    transaction.setClientId(null);
                    transaction.setClientName(null);
                }

                // 3. Persist transaction document. Notice: ZERO category writes.
                Timestamp now = Timestamp.now();
                if (transaction.getCreatedAt() == null) {
                    transaction.setCreatedAt(now);
                }
                transaction.setUpdatedAt(now);

                Map<String, Object> data = toDocumentMap(transaction);
                transactionContext.set(txDocRef, data);

                return transaction;
            }).get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while creating transaction", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ResourceNotFoundException rnfe) {
                throw rnfe;
            }
            if (cause instanceof IllegalArgumentException iae) {
                throw iae;
            }
            log.error("Failed to create transaction in Firestore for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to persist transaction: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Transaction> findById(String workspaceId, String transactionId) {
        checkFirestoreAvailable();

        try {
            DocumentSnapshot snap = getTransactionsCollection(workspaceId)
                    .document(transactionId)
                    .get()
                    .get();

            if (!snap.exists()) {
                return Optional.empty();
            }

            return Optional.ofNullable(mapDocumentToTransaction(snap, workspaceId));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while fetching transaction", e);
        } catch (ExecutionException e) {
            log.error("Failed to fetch transaction {} from workspace {}: {}", transactionId, workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to retrieve transaction: " + e.getMessage(), e);
        }
    }

    @Override
    public Transaction updateTransaction(String workspaceId, Transaction transaction) {
        checkFirestoreAvailable();

        try {
            DocumentReference txDocRef = getTransactionsCollection(workspaceId).document(transaction.getId());
            DocumentReference catDocRef = getCategoriesCollection(workspaceId).document(transaction.getCategoryId());
            DocumentReference clientDocRef = (transaction.getClientId() != null && !transaction.getClientId().isBlank())
                    ? getClientsCollection(workspaceId).document(transaction.getClientId())
                    : null;

            return firestore.runTransaction(transactionContext -> {
                // 1. Transactional read of existing Transaction to preserve creation metadata
                DocumentSnapshot txSnap = transactionContext.get(txDocRef).get();
                if (!txSnap.exists()) {
                    throw new ResourceNotFoundException(
                            "Transaction not found with id: " + transaction.getId() + " in workspace: " + workspaceId
                    );
                }

                transaction.setCreatedAt(txSnap.getTimestamp("createdAt"));
                transaction.setCreatedByUid(txSnap.getString("createdByUid"));

                // 2. Transactional read and validation of Category
                DocumentSnapshot catSnap = transactionContext.get(catDocRef).get();
                if (!catSnap.exists()) {
                    throw new ResourceNotFoundException(
                            "Category not found with id: " + transaction.getCategoryId() + " in workspace: " + workspaceId
                    );
                }

                Boolean isArchived = catSnap.getBoolean("isArchived");
                if (Boolean.TRUE.equals(isArchived)) {
                    throw new IllegalArgumentException(
                            "Cannot update transaction with archived category: " + transaction.getCategoryId()
                    );
                }

                String catType = catSnap.getString("type");
                if (catType != null && !catType.equalsIgnoreCase(transaction.getType())) {
                    throw new IllegalArgumentException(
                            "Category type '" + catType + "' does not match transaction type '" + transaction.getType() + "'"
                    );
                }

                transaction.setCategoryName(catSnap.getString("name"));
                transaction.setCategoryColor(catSnap.getString("color"));

                // 3. Transactional read and validation of Client (if present)
                if (clientDocRef != null) {
                    DocumentSnapshot clientSnap = transactionContext.get(clientDocRef).get();
                    if (!clientSnap.exists()) {
                        throw new ResourceNotFoundException(
                                "Client not found with id: " + transaction.getClientId() + " in workspace: " + workspaceId
                        );
                    }

                    String status = clientSnap.getString("status");
                    if (status == null || !ClientStatus.ACTIVE.name().equalsIgnoreCase(status.trim())) {
                        throw new IllegalArgumentException(
                                "Client '" + transaction.getClientId() + "' is not active (status: " + status + ")"
                        );
                    }

                    transaction.setClientName(clientSnap.getString("name"));
                } else {
                    transaction.setClientId(null);
                    transaction.setClientName(null);
                }

                // 4. Update transaction document with new timestamp and caller updatedByUid
                Timestamp now = Timestamp.now();
                transaction.setUpdatedAt(now);

                Map<String, Object> data = toDocumentMap(transaction);
                transactionContext.set(txDocRef, data);

                return transaction;
            }).get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while updating transaction", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ResourceNotFoundException rnfe) {
                throw rnfe;
            }
            if (cause instanceof IllegalArgumentException iae) {
                throw iae;
            }
            log.error("Failed to update transaction in Firestore for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to update transaction: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteTransaction(String workspaceId, String transactionId) {
        checkFirestoreAvailable();

        try {
            DocumentReference docRef = getTransactionsCollection(workspaceId).document(transactionId);
            DocumentSnapshot snap = docRef.get().get();
            if (!snap.exists()) {
                return false;
            }

            docRef.delete().get();
            log.info("Transaction deleted: id='{}' in workspace='{}'", transactionId, workspaceId);
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while deleting transaction", e);
        } catch (ExecutionException e) {
            log.error("Failed to delete transaction {} from workspace {}: {}", transactionId, workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to delete transaction: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Transaction> findTransactions(String workspaceId,
                                              String type,
                                              String categoryId,
                                              String clientId,
                                              Timestamp startDate,
                                              Timestamp endDate,
                                              Integer limit) {
        checkFirestoreAvailable();

        try {
            Query query = getTransactionsCollection(workspaceId);

            if (type != null && !type.isBlank()) {
                query = query.whereEqualTo("type", type.trim().toUpperCase());
            }
            if (categoryId != null && !categoryId.isBlank()) {
                query = query.whereEqualTo("categoryId", categoryId.trim());
            }
            if (clientId != null && !clientId.isBlank()) {
                query = query.whereEqualTo("clientId", clientId.trim());
            }
            if (startDate != null) {
                query = query.whereGreaterThanOrEqualTo("transactionDate", startDate);
            }
            if (endDate != null) {
                query = query.whereLessThanOrEqualTo("transactionDate", endDate);
            }

            query = query.orderBy("transactionDate", Query.Direction.DESCENDING);

            if (limit != null && limit > 0) {
                query = query.limit(limit);
            }

            QuerySnapshot querySnapshot = query.get().get();
            List<Transaction> transactions = new ArrayList<>();

            for (QueryDocumentSnapshot doc : querySnapshot.getDocuments()) {
                Transaction tx = mapDocumentToTransaction(doc, workspaceId);
                if (tx != null) {
                    transactions.add(tx);
                }
            }

            // Ensure deterministic descending sort by transactionDate
            transactions.sort((a, b) -> {
                if (a.getTransactionDate() == null || b.getTransactionDate() == null) return 0;
                return b.getTransactionDate().compareTo(a.getTransactionDate());
            });

            return transactions;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while listing transactions", e);
        } catch (ExecutionException e) {
            log.error("Failed to list transactions for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to query transactions: " + e.getMessage(), e);
        }
    }

    private void checkFirestoreAvailable() {
        if (this.firestore == null) {
            throw new DatabaseOperationException("Firestore instance is not initialized or unavailable", null);
        }
    }

    private CollectionReference getTransactionsCollection(String workspaceId) {
        return firestore.collection(WORKSPACES_COLLECTION)
                .document(workspaceId)
                .collection(TRANSACTIONS_SUBCOLLECTION);
    }

    private CollectionReference getCategoriesCollection(String workspaceId) {
        return firestore.collection(WORKSPACES_COLLECTION)
                .document(workspaceId)
                .collection(CATEGORIES_SUBCOLLECTION);
    }

    private CollectionReference getClientsCollection(String workspaceId) {
        return firestore.collection(WORKSPACES_COLLECTION)
                .document(workspaceId)
                .collection(CLIENTS_SUBCOLLECTION);
    }

    private Map<String, Object> toDocumentMap(Transaction tx) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", tx.getType());
        data.put("amountMinor", tx.getAmountMinor());
        data.put("currency", tx.getCurrency());
        data.put("categoryId", tx.getCategoryId());
        data.put("categoryName", tx.getCategoryName());
        data.put("categoryColor", tx.getCategoryColor());
        data.put("clientId", tx.getClientId());
        data.put("clientName", tx.getClientName());
        data.put("description", tx.getDescription());
        data.put("transactionDate", tx.getTransactionDate());
        data.put("paymentMethod", tx.getPaymentMethod());
        data.put("status", tx.getStatus());
        data.put("createdByUid", tx.getCreatedByUid());
        data.put("updatedByUid", tx.getUpdatedByUid());
        data.put("createdAt", tx.getCreatedAt());
        data.put("updatedAt", tx.getUpdatedAt());
        return data;
    }

    private Transaction mapDocumentToTransaction(DocumentSnapshot doc, String workspaceId) {
        Transaction tx = new Transaction();
        tx.setId(doc.getId());
        tx.setWorkspaceId(workspaceId);
        tx.setType(doc.getString("type"));
        tx.setAmountMinor(doc.getLong("amountMinor"));
        tx.setCurrency(doc.getString("currency"));
        tx.setCategoryId(doc.getString("categoryId"));
        tx.setCategoryName(doc.getString("categoryName"));
        tx.setCategoryColor(doc.getString("categoryColor"));
        tx.setClientId(doc.getString("clientId"));
        tx.setClientName(doc.getString("clientName"));
        tx.setDescription(doc.getString("description"));
        tx.setTransactionDate(doc.getTimestamp("transactionDate"));
        tx.setPaymentMethod(doc.getString("paymentMethod"));
        tx.setStatus(doc.getString("status"));
        tx.setCreatedByUid(doc.getString("createdByUid"));
        tx.setUpdatedByUid(doc.getString("updatedByUid"));
        tx.setCreatedAt(doc.getTimestamp("createdAt"));
        tx.setUpdatedAt(doc.getTimestamp("updatedAt"));
        return tx;
    }

    // ── PHASE 5 ANALYTICS & AGGREGATION ──────────────────────────────────────────

    @Override
    public FinancialSummaryAggregate aggregateFinancialSummary(
            String workspaceId,
            String currency,
            Instant startInclusive,
            Instant endExclusive,
            boolean includePending
    ) {
        checkFirestoreAvailable();
        Timestamp startTimestamp = toTimestamp(startInclusive);
        Timestamp endTimestamp = toTimestamp(endExclusive);

        try {
            // 1. Realized Income
            AggregateResult realizedIncome = executeAggregateQuery(
                    workspaceId, currency, "COMPLETED", "INCOME", startTimestamp, endTimestamp
            );

            // 2. Realized Expense
            AggregateResult realizedExpense = executeAggregateQuery(
                    workspaceId, currency, "COMPLETED", "EXPENSE", startTimestamp, endTimestamp
            );

            // 3. Pipeline Metrics (if includePending)
            AggregateResult pendingIncome = includePending
                    ? executeAggregateQuery(workspaceId, currency, "PENDING", "INCOME", startTimestamp, endTimestamp)
                    : new AggregateResult(0L, 0L);

            AggregateResult pendingExpense = includePending
                    ? executeAggregateQuery(workspaceId, currency, "PENDING", "EXPENSE", startTimestamp, endTimestamp)
                    : new AggregateResult(0L, 0L);

            return new FinancialSummaryAggregate(
                    realizedIncome.sum(),
                    realizedIncome.count(),
                    realizedExpense.sum(),
                    realizedExpense.count(),
                    pendingIncome.sum(),
                    pendingIncome.count(),
                    pendingExpense.sum(),
                    pendingExpense.count()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while aggregating financial summary", e);
        } catch (ExecutionException e) {
            log.error("Failed to aggregate financial summary for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to aggregate financial summary: " + e.getMessage(), e);
        }
    }

    @Override
    public long countEligibleTransactions(
            String workspaceId,
            String currency,
            Instant startInclusive,
            Instant endExclusive,
            boolean includePending
    ) {
        checkFirestoreAvailable();
        Timestamp startTimestamp = toTimestamp(startInclusive);
        Timestamp endTimestamp = toTimestamp(endExclusive);

        try {
            long completedCount = executeCountQuery(workspaceId, currency, "COMPLETED", startTimestamp, endTimestamp);
            if (!includePending) {
                return completedCount;
            }
            long pendingCount = executeCountQuery(workspaceId, currency, "PENDING", startTimestamp, endTimestamp);
            return Math.addExact(completedCount, pendingCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while counting eligible transactions", e);
        } catch (ExecutionException e) {
            log.error("Failed to count eligible transactions for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to count eligible transactions: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> discoverCurrencies(
            String workspaceId,
            Instant startInclusive,
            Instant endExclusive,
            boolean includePending
    ) {
        checkFirestoreAvailable();
        Timestamp startTimestamp = toTimestamp(startInclusive);
        Timestamp endTimestamp = toTimestamp(endExclusive);

        try {
            Query baseQuery = getTransactionsCollection(workspaceId)
                    .whereGreaterThanOrEqualTo("transactionDate", startTimestamp)
                    .whereLessThan("transactionDate", endTimestamp)
                    .orderBy("transactionDate", Query.Direction.ASCENDING)
                    .select("currency", "status");

            Set<String> discovered = new HashSet<>();
            DocumentSnapshot lastDoc = null;
            boolean hasMore = true;
            final int PAGE_SIZE = 1000;

            while (hasMore) {
                Query pagedQuery = baseQuery.limit(PAGE_SIZE);
                if (lastDoc != null) {
                    pagedQuery = pagedQuery.startAfter(lastDoc);
                }

                QuerySnapshot querySnapshot = pagedQuery.get().get();
                List<QueryDocumentSnapshot> docs = querySnapshot.getDocuments();
                if (docs.isEmpty()) {
                    break;
                }

                for (QueryDocumentSnapshot doc : docs) {
                    String rawStatus = doc.getString("status");
                    if (isStatusEligibleForDiscovery(rawStatus, includePending)) {
                        String curr = doc.getString("currency");
                        if (curr != null && !curr.isBlank()) {
                            discovered.add(curr.trim().toUpperCase());
                        }
                    }
                }

                if (docs.size() < PAGE_SIZE) {
                    hasMore = false;
                } else {
                    lastDoc = docs.get(docs.size() - 1);
                }
            }

            List<String> sortedCurrencies = new ArrayList<>(discovered);
            Collections.sort(sortedCurrencies);
            return sortedCurrencies;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while discovering currencies", e);
        } catch (ExecutionException e) {
            log.error("Failed to discover currencies for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to discover currencies: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches a single cursor-paginated batch of selectively projected transaction items for a specific status.
     *
     * <p>Deterministic Cursor Ordering:
     * For this query ordered by {@code transactionDate ASC}, Cloud Firestore appends {@code __name__ ASC}
     * (document ID in ascending order) as the secondary tie-breaker. Passing {@link QueryDocumentSnapshot}
     * to {@link Query#startAfter(DocumentSnapshot)} supplies both the timestamp and document path to Firestore,
     * preventing duplicate or skipped records across page boundaries for a stable dataset.
     *
     * <p>Consistency Guarantee:
     * Note that independently executed Firestore page queries reflect read-committed state at each page's read time;
     * they do not provide an immutable snapshot across concurrent inserts or updates.
     */
    @Override
    public TransactionStreamBatch fetchStreamBatch(
            String workspaceId,
            String currency,
            String status,
            Instant startInclusive,
            Instant endExclusive,
            int batchSize,
            QueryDocumentSnapshot startAfterDocument
    ) {
        checkFirestoreAvailable();
        Timestamp startTimestamp = toTimestamp(startInclusive);
        Timestamp endTimestamp = toTimestamp(endExclusive);

        try {
            Query q = getTransactionsCollection(workspaceId)
                    .whereEqualTo("currency", currency)
                    .whereEqualTo("status", status)
                    .whereGreaterThanOrEqualTo("transactionDate", startTimestamp)
                    .whereLessThan("transactionDate", endTimestamp)
                    .orderBy("transactionDate", Query.Direction.ASCENDING)
                    .select(
                            "type", "amountMinor", "currency", "status", "transactionDate",
                            "categoryId", "categoryName", "categoryColor", "clientId", "clientName"
                    )
                    .limit(batchSize);

            if (startAfterDocument != null) {
                q = q.startAfter(startAfterDocument);
            }

            QuerySnapshot snapshot = q.get().get();
            List<QueryDocumentSnapshot> docs = snapshot.getDocuments();

            List<TransactionStreamItem> items = new ArrayList<>(docs.size());
            for (QueryDocumentSnapshot doc : docs) {
                items.add(mapDocumentToStreamItem(doc));
            }

            QueryDocumentSnapshot lastDoc = docs.isEmpty() ? null : docs.get(docs.size() - 1);
            boolean hasMore = docs.size() == batchSize;

            return new TransactionStreamBatch(items, lastDoc, hasMore);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while fetching transaction stream batch", e);
        } catch (ExecutionException e) {
            log.error("Failed to fetch transaction stream batch for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to fetch stream batch: " + e.getMessage(), e);
        }
    }

    private AggregateResult executeAggregateQuery(
            String workspaceId,
            String currency,
            String status,
            String type,
            Timestamp startTimestamp,
            Timestamp endTimestamp
    ) throws ExecutionException, InterruptedException {
        Query q = getTransactionsCollection(workspaceId)
                .whereEqualTo("currency", currency)
                .whereEqualTo("status", status)
                .whereEqualTo("type", type)
                .whereGreaterThanOrEqualTo("transactionDate", startTimestamp)
                .whereLessThan("transactionDate", endTimestamp);

        AggregateQuery aggregateQuery = q.aggregate(
                AggregateField.sum("amountMinor"),
                AggregateField.count()
        );

        AggregateQuerySnapshot snapshot = aggregateQuery.get().get();
        Long sum = snapshot.getLong(AggregateField.sum("amountMinor"));
        long totalMinor = sum != null ? sum : 0L;
        long count = snapshot.getCount();

        return new AggregateResult(totalMinor, count);
    }

    private long executeCountQuery(
            String workspaceId,
            String currency,
            String status,
            Timestamp startTimestamp,
            Timestamp endTimestamp
    ) throws ExecutionException, InterruptedException {
        Query q = getTransactionsCollection(workspaceId)
                .whereEqualTo("currency", currency)
                .whereEqualTo("status", status)
                .whereGreaterThanOrEqualTo("transactionDate", startTimestamp)
                .whereLessThan("transactionDate", endTimestamp);

        return q.aggregate(AggregateField.count()).get().get().getCount();
    }

    /**
     * Strict eligibility allowlist for currency discovery:
     * - COMPLETED is always eligible.
     * - PENDING is eligible only when includePending == true.
     * - CANCELLED and every unknown/null status (e.g. REVERSED) are always excluded.
     */
    private boolean isStatusEligibleForDiscovery(String status, boolean includePending) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        if ("COMPLETED".equals(normalized)) {
            return true;
        }
        if (includePending && "PENDING".equals(normalized)) {
            return true;
        }
        return false; // CANCELLED, REVERSED, or any unknown status excluded
    }

    private TransactionStreamItem mapDocumentToStreamItem(DocumentSnapshot doc) {
        return new TransactionStreamItem(
                doc.getId(),
                doc.getString("type"),
                doc.getLong("amountMinor"),
                doc.getString("currency"),
                doc.getString("status"),
                doc.getTimestamp("transactionDate"),
                doc.getString("categoryId"),
                doc.getString("categoryName"),
                doc.getString("categoryColor"),
                doc.getString("clientId"),
                doc.getString("clientName")
        );
    }

    private Timestamp toTimestamp(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    private record AggregateResult(long sum, long count) {}
}

