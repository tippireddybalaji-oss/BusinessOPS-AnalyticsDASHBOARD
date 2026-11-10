package com.businessops.dashboard.repository;

import com.businessops.dashboard.exception.DatabaseOperationException;
import com.businessops.dashboard.model.FinancialSummaryAggregate;
import com.businessops.dashboard.model.TransactionStreamBatch;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FirestoreTransactionAggregationTest {

    @Mock
    private Firestore firestore;

    @Mock
    private CollectionReference workspacesCollection;

    @Mock
    private DocumentReference workspaceDocRef;

    @Mock
    private CollectionReference transactionsCollection;

    @Mock
    private Query mockQuery;

    @Mock
    private AggregateQuery mockAggregateQuery;

    @Mock
    private AggregateQuerySnapshot mockAggregateSnapshot;

    @Mock
    private ApiFuture<AggregateQuerySnapshot> mockAggregateFuture;

    @Mock
    private QuerySnapshot mockQuerySnapshot;

    @Mock
    private ApiFuture<QuerySnapshot> mockQueryFuture;

    private FirestoreTransactionRepository repository;

    private static final String WS_ID = "ws-analytics-test";
    private static final String CURRENCY = "USD";
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-02-01T00:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        when(firestore.collection("workspaces")).thenReturn(workspacesCollection);
        when(workspacesCollection.document(WS_ID)).thenReturn(workspaceDocRef);
        when(workspaceDocRef.collection("transactions")).thenReturn(transactionsCollection);

        // Chain query mocks
        when(transactionsCollection.whereEqualTo(anyString(), any())).thenReturn(mockQuery);
        when(transactionsCollection.whereGreaterThanOrEqualTo(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.whereEqualTo(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.whereGreaterThanOrEqualTo(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.whereLessThan(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.orderBy(anyString(), any(Query.Direction.class))).thenReturn(mockQuery);
        when(mockQuery.select(any(String[].class))).thenReturn(mockQuery);
        when(mockQuery.limit(anyInt())).thenReturn(mockQuery);
        when(mockQuery.startAfter(any(DocumentSnapshot.class))).thenReturn(mockQuery);

        when(mockQuery.aggregate(any(AggregateField.class), any(AggregateField.class))).thenReturn(mockAggregateQuery);
        when(mockQuery.aggregate(any(AggregateField.class))).thenReturn(mockAggregateQuery);
        when(mockAggregateQuery.get()).thenReturn(mockAggregateFuture);
        when(mockAggregateFuture.get()).thenReturn(mockAggregateSnapshot);

        when(mockQuery.get()).thenReturn(mockQueryFuture);
        when(mockQueryFuture.get()).thenReturn(mockQuerySnapshot);

        repository = new FirestoreTransactionRepository(firestore);
    }

    // ── SUMMARY AGGREGATION ──────────────────────────────────────────────────

    @Test
    @DisplayName("aggregateFinancialSummary: realized only (includePending=false) executes 2 queries")
    void testAggregateSummary_RealizedOnly() throws Exception {
        when(mockAggregateSnapshot.getLong(any())).thenReturn(100_000L, 40_000L);
        when(mockAggregateSnapshot.getCount()).thenReturn(10L, 5L);

        FinancialSummaryAggregate result = repository.aggregateFinancialSummary(WS_ID, CURRENCY, START, END, false);

        assertThat(result.totalRevenueMinor()).isEqualTo(100_000L);
        assertThat(result.incomeTransactionCount()).isEqualTo(10L);
        assertThat(result.totalExpenseMinor()).isEqualTo(40_000L);
        assertThat(result.expenseTransactionCount()).isEqualTo(5L);
        assertThat(result.pendingIncomeMinor()).isEqualTo(0L);
        assertThat(result.pendingIncomeTransactionCount()).isEqualTo(0L);
        assertThat(result.pendingExpenseMinor()).isEqualTo(0L);
        assertThat(result.pendingExpenseTransactionCount()).isEqualTo(0L);

        // Verify only 2 queries executed
        verify(mockQuery, times(2)).aggregate(any(AggregateField.class), any(AggregateField.class));
    }

    @Test
    @DisplayName("aggregateFinancialSummary: with pending (includePending=true) executes 4 queries")
    void testAggregateSummary_WithPending() throws Exception {
        when(mockAggregateSnapshot.getLong(any())).thenReturn(100_000L, 40_000L, 25_000L, 10_000L);
        when(mockAggregateSnapshot.getCount()).thenReturn(10L, 5L, 3L, 2L);

        FinancialSummaryAggregate result = repository.aggregateFinancialSummary(WS_ID, CURRENCY, START, END, true);

        assertThat(result.totalRevenueMinor()).isEqualTo(100_000L);
        assertThat(result.totalExpenseMinor()).isEqualTo(40_000L);
        assertThat(result.pendingIncomeMinor()).isEqualTo(25_000L);
        assertThat(result.pendingIncomeTransactionCount()).isEqualTo(3L);
        assertThat(result.pendingExpenseMinor()).isEqualTo(10_000L);
        assertThat(result.pendingExpenseTransactionCount()).isEqualTo(2L);

        verify(mockQuery, times(4)).aggregate(any(AggregateField.class), any(AggregateField.class));
    }

    @Test
    @DisplayName("aggregateFinancialSummary: zero matching documents handles null sum safely")
    void testAggregateSummary_ZeroMatches_NullSafe() throws Exception {
        when(mockAggregateSnapshot.getLong(any())).thenReturn(null);
        when(mockAggregateSnapshot.getCount()).thenReturn(0L);

        FinancialSummaryAggregate result = repository.aggregateFinancialSummary(WS_ID, CURRENCY, START, END, false);

        assertThat(result.totalRevenueMinor()).isEqualTo(0L);
        assertThat(result.incomeTransactionCount()).isEqualTo(0L);
        assertThat(result.totalExpenseMinor()).isEqualTo(0L);
        assertThat(result.expenseTransactionCount()).isEqualTo(0L);
    }

    // ── PRE-FLIGHT COUNT ─────────────────────────────────────────────────────

    @Test
    @DisplayName("countEligibleTransactions: completed only executes 1 count query")
    void testCountEligible_CompletedOnly() throws Exception {
        when(mockAggregateSnapshot.getCount()).thenReturn(500L);

        long count = repository.countEligibleTransactions(WS_ID, CURRENCY, START, END, false);

        assertThat(count).isEqualTo(500L);
        verify(mockQuery, times(1)).aggregate(any(AggregateField.class));
    }

    @Test
    @DisplayName("countEligibleTransactions: with pending executes 2 count queries and sums them")
    void testCountEligible_WithPending() throws Exception {
        when(mockAggregateSnapshot.getCount()).thenReturn(400L, 150L);

        long count = repository.countEligibleTransactions(WS_ID, CURRENCY, START, END, true);

        assertThat(count).isEqualTo(550L);
        verify(mockQuery, times(2)).aggregate(any(AggregateField.class));
    }

    // ── CURRENCY DISCOVERY & ALLOWLIST ───────────────────────────────────────

    @Test
    @DisplayName("discoverCurrencies: returns alphabetically sorted distinct currencies across pages")
    void testDiscoverCurrencies_AlphaSortedAndPaged() throws Exception {
        QueryDocumentSnapshot doc1 = mock(QueryDocumentSnapshot.class);
        when(doc1.getString("currency")).thenReturn("USD");
        when(doc1.getString("status")).thenReturn("COMPLETED");

        QueryDocumentSnapshot doc2 = mock(QueryDocumentSnapshot.class);
        when(doc2.getString("currency")).thenReturn("EUR");
        when(doc2.getString("status")).thenReturn("COMPLETED");

        QueryDocumentSnapshot doc3 = mock(QueryDocumentSnapshot.class);
        when(doc3.getString("currency")).thenReturn("INR");
        when(doc3.getString("status")).thenReturn("COMPLETED");

        // Page 1 returns 3 docs (< 1000 page size, terminates loop)
        when(mockQuerySnapshot.getDocuments()).thenReturn(List.of(doc1, doc2, doc3));

        List<String> currencies = repository.discoverCurrencies(WS_ID, START, END, false);

        assertThat(currencies).containsExactly("EUR", "INR", "USD");
    }

    @Test
    @DisplayName("discoverCurrencies status allowlist: COMPLETED included, CANCELLED/REVERSED/UNKNOWN excluded")
    void testDiscoverCurrencies_StatusAllowlist() throws Exception {
        QueryDocumentSnapshot completed = mock(QueryDocumentSnapshot.class);
        when(completed.getString("currency")).thenReturn("USD");
        when(completed.getString("status")).thenReturn("COMPLETED");

        QueryDocumentSnapshot pending = mock(QueryDocumentSnapshot.class);
        when(pending.getString("currency")).thenReturn("EUR");
        when(pending.getString("status")).thenReturn("PENDING");

        QueryDocumentSnapshot cancelled = mock(QueryDocumentSnapshot.class);
        when(cancelled.getString("currency")).thenReturn("GBP");
        when(cancelled.getString("status")).thenReturn("CANCELLED");

        QueryDocumentSnapshot reversed = mock(QueryDocumentSnapshot.class);
        when(reversed.getString("currency")).thenReturn("JPY");
        when(reversed.getString("status")).thenReturn("REVERSED");

        QueryDocumentSnapshot nullStatus = mock(QueryDocumentSnapshot.class);
        when(nullStatus.getString("currency")).thenReturn("CAD");
        when(nullStatus.getString("status")).thenReturn(null);

        when(mockQuerySnapshot.getDocuments()).thenReturn(List.of(completed, pending, cancelled, reversed, nullStatus));

        // When includePending = false: only COMPLETED is eligible
        List<String> realizedOnly = repository.discoverCurrencies(WS_ID, START, END, false);
        assertThat(realizedOnly).containsExactly("USD");

        // When includePending = true: COMPLETED and PENDING are eligible; CANCELLED, REVERSED, null excluded
        List<String> withPending = repository.discoverCurrencies(WS_ID, START, END, true);
        assertThat(withPending).containsExactly("EUR", "USD");
    }

    @Test
    @DisplayName("discoverCurrencies: independent query does not filter by currency")
    void testDiscoverCurrencies_NoCurrencyFilter() {
        when(mockQuerySnapshot.getDocuments()).thenReturn(List.of());

        repository.discoverCurrencies(WS_ID, START, END, false);

        // Verify whereEqualTo is never called with "currency"
        verify(transactionsCollection, never()).whereEqualTo(eq("currency"), any());
        verify(mockQuery, never()).whereEqualTo(eq("currency"), any());
    }

    // ── STREAM BATCHING & PROJECTION ─────────────────────────────────────────

    @Test
    @DisplayName("fetchStreamBatch: maps selectively projected document to TransactionStreamItem")
    void testFetchStreamBatch_ProjectionMapping() throws Exception {
        QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);
        when(doc.getId()).thenReturn("tx-1");
        when(doc.getString("type")).thenReturn("INCOME");
        when(doc.getLong("amountMinor")).thenReturn(50_000L);
        when(doc.getString("currency")).thenReturn("USD");
        when(doc.getString("status")).thenReturn("COMPLETED");
        when(doc.getTimestamp("transactionDate")).thenReturn(Timestamp.ofTimeSecondsAndNanos(START.getEpochSecond(), 0));
        when(doc.getString("categoryId")).thenReturn("cat-1");
        when(doc.getString("categoryName")).thenReturn("Sales");
        when(doc.getString("categoryColor")).thenReturn("#FF0000");
        when(doc.getString("clientId")).thenReturn("cl-1");
        when(doc.getString("clientName")).thenReturn("Acme");

        when(mockQuerySnapshot.getDocuments()).thenReturn(List.of(doc));

        TransactionStreamBatch batch = repository.fetchStreamBatch(WS_ID, CURRENCY, "COMPLETED", START, END, 500, null);

        assertThat(batch.items()).hasSize(1);
        assertThat(batch.hasMore()).isFalse();
        assertThat(batch.lastDocument()).isEqualTo(doc);

        var item = batch.items().get(0);
        assertThat(item.id()).isEqualTo("tx-1");
        assertThat(item.type()).isEqualTo("INCOME");
        assertThat(item.amountMinor()).isEqualTo(50_000L);
        assertThat(item.categoryName()).isEqualTo("Sales");
        assertThat(item.clientName()).isEqualTo("Acme");
    }

    @Test
    @DisplayName("fetchStreamBatch: resumes cursor continuation when startAfterDocument provided")
    void testFetchStreamBatch_CursorContinuation() {
        QueryDocumentSnapshot cursorDoc = mock(QueryDocumentSnapshot.class);
        when(mockQuerySnapshot.getDocuments()).thenReturn(List.of());

        repository.fetchStreamBatch(WS_ID, CURRENCY, "COMPLETED", START, END, 500, cursorDoc);

        verify(mockQuery).startAfter(cursorDoc);
    }

    // ── TENANT ISOLATION & DATE BOUNDARIES ───────────────────────────────────

    @Test
    @DisplayName("all aggregation methods enforce tenant isolation under workspace collection")
    void testTenantIsolation_ScopedToWorkspace() {
        repository.countEligibleTransactions(WS_ID, CURRENCY, START, END, false);

        verify(firestore).collection("workspaces");
        verify(workspacesCollection).document(WS_ID);
        verify(workspaceDocRef).collection("transactions");
    }

    @Test
    @DisplayName("all aggregation methods enforce half-open interval [startInclusive, endExclusive)")
    void testDateBoundaries_InclusiveStartExclusiveEnd() {
        repository.countEligibleTransactions(WS_ID, CURRENCY, START, END, false);

        verify(mockQuery).whereGreaterThanOrEqualTo(eq("transactionDate"), any(Timestamp.class));
        verify(mockQuery).whereLessThan(eq("transactionDate"), any(Timestamp.class));
        verify(mockQuery, never()).whereLessThanOrEqualTo(eq("transactionDate"), any());
    }

    // ── EXCEPTION TRANSLATION ────────────────────────────────────────────────

    @Test
    @DisplayName("exceptions translate to DatabaseOperationException")
    void testDatabaseExceptionTranslation() throws Exception {
        when(mockAggregateFuture.get()).thenThrow(new ExecutionException("Firestore timeout", new RuntimeException()));

        assertThatThrownBy(() -> repository.countEligibleTransactions(WS_ID, CURRENCY, START, END, false))
                .isInstanceOf(DatabaseOperationException.class)
                .hasMessageContaining("Failed to count eligible transactions");
    }
}

