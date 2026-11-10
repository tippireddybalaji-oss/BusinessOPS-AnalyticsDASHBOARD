package com.businessops.dashboard.repository;

import com.businessops.dashboard.exception.DatabaseOperationException;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.model.ClientStatus;
import com.businessops.dashboard.model.Transaction;
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FirestoreTransactionRepository}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FirestoreTransactionRepositoryTest {

    @Mock
    private Firestore firestore;

    @Mock
    private CollectionReference workspacesCollection;

    @Mock
    private DocumentReference workspaceDocRef;

    @Mock
    private CollectionReference transactionsCollection;

    @Mock
    private DocumentReference transactionDocRef;

    @Mock
    private CollectionReference categoriesCollection;

    @Mock
    private DocumentReference categoryDocRef;

    @Mock
    private CollectionReference clientsCollection;

    @Mock
    private DocumentReference clientDocRef;

    @Mock
    private com.google.cloud.firestore.Transaction mockTxContext;

    @Mock
    private ApiFuture<WriteResult> writeFuture;

    private FirestoreTransactionRepository repository;

    private static final String WS_ID = "ws-test-1";
    private static final String TX_ID = "tx-test-1";
    private static final String CAT_ID = "cat-sales";
    private static final String CLIENT_ID = "cl-acme";

    @BeforeEach
    void setUp() throws Exception {
        when(firestore.collection("workspaces")).thenReturn(workspacesCollection);
        when(workspacesCollection.document(WS_ID)).thenReturn(workspaceDocRef);

        when(workspaceDocRef.collection("transactions")).thenReturn(transactionsCollection);
        when(transactionsCollection.document()).thenReturn(transactionDocRef);
        when(transactionsCollection.document(anyString())).thenReturn(transactionDocRef);
        when(transactionDocRef.getId()).thenReturn(TX_ID);

        when(workspaceDocRef.collection("categories")).thenReturn(categoriesCollection);
        when(categoriesCollection.document(anyString())).thenReturn(categoryDocRef);

        when(workspaceDocRef.collection("clients")).thenReturn(clientsCollection);
        when(clientsCollection.document(anyString())).thenReturn(clientDocRef);

        // Handle firestore.runTransaction executing the callback
        when(firestore.runTransaction(any())).thenAnswer(invocation -> {
            com.google.cloud.firestore.Transaction.Function<?> function = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            ApiFuture<Object> future = mock(ApiFuture.class);
            try {
                Object result = function.updateCallback(mockTxContext);
                when(future.get()).thenReturn(result);
            } catch (Exception e) {
                when(future.get()).thenThrow(new ExecutionException(e));
            }
            return future;
        });

        when(transactionDocRef.set(anyMap())).thenReturn(writeFuture);
        when(transactionDocRef.delete()).thenReturn(writeFuture);

        repository = new FirestoreTransactionRepository(firestore);
    }

    @Test
    @DisplayName("createTransaction: succeeds with valid category and active client, caching display fields")
    void createTransaction_withCategoryAndClient_succeeds() throws Exception {
        DocumentSnapshot catSnap = mock(DocumentSnapshot.class);
        when(catSnap.exists()).thenReturn(true);
        when(catSnap.getBoolean("isArchived")).thenReturn(false);
        when(catSnap.getString("type")).thenReturn("INCOME");
        when(catSnap.getString("name")).thenReturn("Sales Revenue");
        when(catSnap.getString("color")).thenReturn("#10B981");

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> catFuture = mock(ApiFuture.class);
        when(catFuture.get()).thenReturn(catSnap);
        when(mockTxContext.get(categoryDocRef)).thenReturn(catFuture);

        DocumentSnapshot clientSnap = mock(DocumentSnapshot.class);
        when(clientSnap.exists()).thenReturn(true);
        when(clientSnap.getString("status")).thenReturn("ACTIVE");
        when(clientSnap.getString("name")).thenReturn("Acme Corp");

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> clientFuture = mock(ApiFuture.class);
        when(clientFuture.get()).thenReturn(clientSnap);
        when(mockTxContext.get(clientDocRef)).thenReturn(clientFuture);

        Transaction input = new Transaction();
        input.setType("INCOME");
        input.setAmountMinor(500000L);
        input.setCurrency("INR");
        input.setCategoryId(CAT_ID);
        input.setClientId(CLIENT_ID);
        input.setDescription("Q1 Consulting Retainer");

        Transaction created = repository.createTransaction(WS_ID, input);

        assertThat(created.getId()).isEqualTo(TX_ID);
        assertThat(created.getCategoryName()).isEqualTo("Sales Revenue");
        assertThat(created.getCategoryColor()).isEqualTo("#10B981");
        assertThat(created.getClientName()).isEqualTo("Acme Corp");

        // Verify zero writes to category document
        verify(mockTxContext, never()).set(eq(categoryDocRef), any());
        verify(mockTxContext, never()).update(eq(categoryDocRef), anyMap());

        // Verify transaction document was set
        verify(mockTxContext).set(eq(transactionDocRef), anyMap());
    }

    @Test
    @DisplayName("createTransaction: succeeds when clientId is omitted")
    void createTransaction_withoutClient_succeeds() throws Exception {
        DocumentSnapshot catSnap = mock(DocumentSnapshot.class);
        when(catSnap.exists()).thenReturn(true);
        when(catSnap.getBoolean("isArchived")).thenReturn(false);
        when(catSnap.getString("type")).thenReturn("EXPENSE");
        when(catSnap.getString("name")).thenReturn("Office Rent");
        when(catSnap.getString("color")).thenReturn("#EF4444");

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> catFuture = mock(ApiFuture.class);
        when(catFuture.get()).thenReturn(catSnap);
        when(mockTxContext.get(categoryDocRef)).thenReturn(catFuture);

        Transaction input = new Transaction();
        input.setType("EXPENSE");
        input.setAmountMinor(100000L);
        input.setCurrency("USD");
        input.setCategoryId("cat-rent");
        input.setClientId(null);

        Transaction created = repository.createTransaction(WS_ID, input);

        assertThat(created.getClientId()).isNull();
        assertThat(created.getClientName()).isNull();
        assertThat(created.getCategoryName()).isEqualTo("Office Rent");
        verify(mockTxContext).set(eq(transactionDocRef), anyMap());
    }

    @Test
    @DisplayName("createTransaction: throws ResourceNotFoundException when category does not exist")
    void createTransaction_categoryNotFound_throwsResourceNotFound() throws Exception {
        DocumentSnapshot catSnap = mock(DocumentSnapshot.class);
        when(catSnap.exists()).thenReturn(false);

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> catFuture = mock(ApiFuture.class);
        when(catFuture.get()).thenReturn(catSnap);
        when(mockTxContext.get(categoryDocRef)).thenReturn(catFuture);

        Transaction input = new Transaction();
        input.setType("INCOME");
        input.setAmountMinor(1000L);
        input.setCategoryId("nonexistent-cat");

        assertThatThrownBy(() -> repository.createTransaction(WS_ID, input))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found with id: nonexistent-cat");
    }

    @Test
    @DisplayName("createTransaction: throws IllegalArgumentException when category is archived")
    void createTransaction_archivedCategory_throwsIllegalArgument() throws Exception {
        DocumentSnapshot catSnap = mock(DocumentSnapshot.class);
        when(catSnap.exists()).thenReturn(true);
        when(catSnap.getBoolean("isArchived")).thenReturn(true);

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> catFuture = mock(ApiFuture.class);
        when(catFuture.get()).thenReturn(catSnap);
        when(mockTxContext.get(categoryDocRef)).thenReturn(catFuture);

        Transaction input = new Transaction();
        input.setType("INCOME");
        input.setAmountMinor(1000L);
        input.setCategoryId(CAT_ID);

        assertThatThrownBy(() -> repository.createTransaction(WS_ID, input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot create transaction with archived category");
    }

    @Test
    @DisplayName("createTransaction: throws IllegalArgumentException on category type mismatch")
    void createTransaction_categoryTypeMismatch_throwsIllegalArgument() throws Exception {
        DocumentSnapshot catSnap = mock(DocumentSnapshot.class);
        when(catSnap.exists()).thenReturn(true);
        when(catSnap.getBoolean("isArchived")).thenReturn(false);
        when(catSnap.getString("type")).thenReturn("EXPENSE");

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> catFuture = mock(ApiFuture.class);
        when(catFuture.get()).thenReturn(catSnap);
        when(mockTxContext.get(categoryDocRef)).thenReturn(catFuture);

        Transaction input = new Transaction();
        input.setType("INCOME");
        input.setAmountMinor(1000L);
        input.setCategoryId(CAT_ID);

        assertThatThrownBy(() -> repository.createTransaction(WS_ID, input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match transaction type");
    }

    @Test
    @DisplayName("createTransaction: throws IllegalArgumentException when client is not active")
    void createTransaction_inactiveClient_throwsIllegalArgument() throws Exception {
        DocumentSnapshot catSnap = mock(DocumentSnapshot.class);
        when(catSnap.exists()).thenReturn(true);
        when(catSnap.getBoolean("isArchived")).thenReturn(false);
        when(catSnap.getString("type")).thenReturn("INCOME");

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> catFuture = mock(ApiFuture.class);
        when(catFuture.get()).thenReturn(catSnap);
        when(mockTxContext.get(categoryDocRef)).thenReturn(catFuture);

        DocumentSnapshot clientSnap = mock(DocumentSnapshot.class);
        when(clientSnap.exists()).thenReturn(true);
        when(clientSnap.getString("status")).thenReturn("INACTIVE");

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> clientFuture = mock(ApiFuture.class);
        when(clientFuture.get()).thenReturn(clientSnap);
        when(mockTxContext.get(clientDocRef)).thenReturn(clientFuture);

        Transaction input = new Transaction();
        input.setType("INCOME");
        input.setAmountMinor(1000L);
        input.setCategoryId(CAT_ID);
        input.setClientId(CLIENT_ID);

        assertThatThrownBy(() -> repository.createTransaction(WS_ID, input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not active");
    }

    @Test
    @DisplayName("findById: returns transaction when document exists")
    void findById_whenExists_returnsTransaction() throws Exception {
        DocumentSnapshot snap = mock(DocumentSnapshot.class);
        when(snap.exists()).thenReturn(true);
        when(snap.getId()).thenReturn(TX_ID);
        when(snap.getString("type")).thenReturn("INCOME");
        when(snap.getLong("amountMinor")).thenReturn(250000L);
        when(snap.getString("currency")).thenReturn("INR");
        when(snap.getString("categoryId")).thenReturn(CAT_ID);
        when(snap.getString("categoryName")).thenReturn("Sales Revenue");
        when(snap.getTimestamp("transactionDate")).thenReturn(Timestamp.now());

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        when(snapFuture.get()).thenReturn(snap);
        when(transactionDocRef.get()).thenReturn(snapFuture);

        Optional<Transaction> result = repository.findById(WS_ID, TX_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(TX_ID);
        assertThat(result.get().getAmountMinor()).isEqualTo(250000L);
    }

    @Test
    @DisplayName("findById: returns empty Optional when document does not exist")
    void findById_whenNotExists_returnsEmpty() throws Exception {
        DocumentSnapshot snap = mock(DocumentSnapshot.class);
        when(snap.exists()).thenReturn(false);

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        when(snapFuture.get()).thenReturn(snap);
        when(transactionDocRef.get()).thenReturn(snapFuture);

        Optional<Transaction> result = repository.findById(WS_ID, "missing-tx");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("updateTransaction: preserves createdAt and createdByUid")
    void updateTransaction_preservesOriginalAuditMetadata() throws Exception {
        Timestamp originalCreated = Timestamp.now();
        String originalAuthor = "author-1";

        DocumentSnapshot txSnap = mock(DocumentSnapshot.class);
        when(txSnap.exists()).thenReturn(true);
        when(txSnap.getTimestamp("createdAt")).thenReturn(originalCreated);
        when(txSnap.getString("createdByUid")).thenReturn(originalAuthor);

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> txFuture = mock(ApiFuture.class);
        when(txFuture.get()).thenReturn(txSnap);
        when(mockTxContext.get(transactionDocRef)).thenReturn(txFuture);

        DocumentSnapshot catSnap = mock(DocumentSnapshot.class);
        when(catSnap.exists()).thenReturn(true);
        when(catSnap.getBoolean("isArchived")).thenReturn(false);
        when(catSnap.getString("type")).thenReturn("EXPENSE");
        when(catSnap.getString("name")).thenReturn("Software Subscriptions");
        when(catSnap.getString("color")).thenReturn("#F59E0B");

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> catFuture = mock(ApiFuture.class);
        when(catFuture.get()).thenReturn(catSnap);
        when(mockTxContext.get(categoryDocRef)).thenReturn(catFuture);

        Transaction updateInput = new Transaction();
        updateInput.setId(TX_ID);
        updateInput.setType("EXPENSE");
        updateInput.setAmountMinor(45000L);
        updateInput.setCurrency("USD");
        updateInput.setCategoryId("cat-software");
        updateInput.setUpdatedByUid("editor-2");

        Transaction updated = repository.updateTransaction(WS_ID, updateInput);

        assertThat(updated.getCreatedAt()).isEqualTo(originalCreated);
        assertThat(updated.getCreatedByUid()).isEqualTo(originalAuthor);
        assertThat(updated.getCategoryName()).isEqualTo("Software Subscriptions");
        verify(mockTxContext).set(eq(transactionDocRef), anyMap());
    }

    @Test
    @DisplayName("deleteTransaction: deletes document and returns true if exists")
    void deleteTransaction_whenExists_returnsTrue() throws Exception {
        DocumentSnapshot snap = mock(DocumentSnapshot.class);
        when(snap.exists()).thenReturn(true);

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        when(snapFuture.get()).thenReturn(snap);
        when(transactionDocRef.get()).thenReturn(snapFuture);

        boolean deleted = repository.deleteTransaction(WS_ID, TX_ID);

        assertThat(deleted).isTrue();
        verify(transactionDocRef).delete();
    }

    @Test
    @DisplayName("deleteTransaction: returns false if document does not exist")
    void deleteTransaction_whenNotExists_returnsFalse() throws Exception {
        DocumentSnapshot snap = mock(DocumentSnapshot.class);
        when(snap.exists()).thenReturn(false);

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        when(snapFuture.get()).thenReturn(snap);
        when(transactionDocRef.get()).thenReturn(snapFuture);

        boolean deleted = repository.deleteTransaction(WS_ID, "unknown-id");
        assertThat(deleted).isFalse();
    }

    @Test
    @DisplayName("findTransactions: builds structured query with equality filters and limit")
    void findTransactions_withFilters_returnsMatchingTransactions() throws Exception {
        Query mockQuery = mock(Query.class);
        when(transactionsCollection.whereEqualTo(eq("type"), any())).thenReturn(mockQuery);
        when(mockQuery.whereEqualTo(eq("categoryId"), any())).thenReturn(mockQuery);
        when(mockQuery.whereEqualTo(eq("clientId"), any())).thenReturn(mockQuery);
        when(mockQuery.whereGreaterThanOrEqualTo(eq("transactionDate"), any())).thenReturn(mockQuery);
        when(mockQuery.whereLessThanOrEqualTo(eq("transactionDate"), any())).thenReturn(mockQuery);
        when(mockQuery.orderBy(eq("transactionDate"), any(Query.Direction.class))).thenReturn(mockQuery);
        when(mockQuery.limit(anyInt())).thenReturn(mockQuery);

        QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);
        when(doc.getId()).thenReturn(TX_ID);
        when(doc.getString("type")).thenReturn("INCOME");
        when(doc.getLong("amountMinor")).thenReturn(100000L);
        when(doc.getString("currency")).thenReturn("INR");
        when(doc.getTimestamp("transactionDate")).thenReturn(Timestamp.now());

        QuerySnapshot snap = mock(QuerySnapshot.class);
        when(snap.getDocuments()).thenReturn(List.of(doc));

        @SuppressWarnings("unchecked")
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
        when(future.get()).thenReturn(snap);
        when(mockQuery.get()).thenReturn(future);

        List<Transaction> result = repository.findTransactions(
                WS_ID, "INCOME", CAT_ID, CLIENT_ID, Timestamp.now(), Timestamp.now(), 501
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(TX_ID);
    }
}

