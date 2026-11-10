package com.businessops.dashboard.repository;

import com.businessops.dashboard.exception.DatabaseOperationException;
import com.businessops.dashboard.model.Category;
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
 * Unit tests for {@link FirestoreCategoryRepository}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FirestoreCategoryRepositoryTest {

    @Mock
    private Firestore firestore;

    @Mock
    private CollectionReference workspacesCollection;

    @Mock
    private DocumentReference workspaceDocRef;

    @Mock
    private CollectionReference categoriesCollection;

    @Mock
    private DocumentReference categoryDocRef;

    @Mock
    private ApiFuture<WriteResult> writeFuture;

    private FirestoreCategoryRepository repository;

    private static final String WS_ID = "ws-test-1";

    @BeforeEach
    void setUp() {
        when(firestore.collection("workspaces")).thenReturn(workspacesCollection);
        when(workspacesCollection.document(WS_ID)).thenReturn(workspaceDocRef);
        when(workspaceDocRef.collection("categories")).thenReturn(categoriesCollection);
        when(categoriesCollection.document()).thenReturn(categoryDocRef);
        when(categoriesCollection.document(anyString())).thenReturn(categoryDocRef);
        when(categoryDocRef.getId()).thenReturn("cat-generated-123");

        repository = new FirestoreCategoryRepository(firestore);
    }

    @Test
    @DisplayName("createCategory: persists document and returns category with generated ID")
    void createCategory_persistsSuccessfully() throws Exception {
        when(categoryDocRef.set(anyMap())).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        Category category = new Category(
                null, WS_ID, "Custom SaaS", "EXPENSE", false,
                "#4F46E5", "tag", false, null, null, null, "user-1", "user-1"
        );

        Category result = repository.createCategory(WS_ID, category);

        assertThat(result.getId()).isEqualTo("cat-generated-123");
        assertThat(result.getWorkspaceId()).isEqualTo(WS_ID);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();

        verify(categoryDocRef).set(anyMap());
    }

    @Test
    @DisplayName("findById: returns category entity when document exists")
    void findById_whenExists_returnsCategory() throws Exception {
        DocumentSnapshot mockSnap = mock(DocumentSnapshot.class);
        when(mockSnap.exists()).thenReturn(true);
        when(mockSnap.getId()).thenReturn("def_inc_sales");
        when(mockSnap.getString("name")).thenReturn("Client Invoicing / Sales");
        when(mockSnap.getString("type")).thenReturn("INCOME");
        when(mockSnap.getBoolean("isDefault")).thenReturn(true);
        when(mockSnap.getBoolean("isArchived")).thenReturn(false);

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        when(snapFuture.get()).thenReturn(mockSnap);
        when(categoryDocRef.get()).thenReturn(snapFuture);

        Optional<Category> result = repository.findById(WS_ID, "def_inc_sales");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Client Invoicing / Sales");
        assertThat(result.get().isDefault()).isTrue();
    }

    @Test
    @DisplayName("findById: returns empty when document does not exist")
    void findById_whenNotExists_returnsEmpty() throws Exception {
        DocumentSnapshot mockSnap = mock(DocumentSnapshot.class);
        when(mockSnap.exists()).thenReturn(false);

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        when(snapFuture.get()).thenReturn(mockSnap);
        when(categoryDocRef.get()).thenReturn(snapFuture);

        Optional<Category> result = repository.findById(WS_ID, "missing-cat");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByNameAndType: returns matching active category ignoring case")
    void findByNameAndType_matchesCaseInsensitively() throws Exception {
        Query mockQuery = mock(Query.class);
        when(categoriesCollection.whereEqualTo("type", "EXPENSE")).thenReturn(mockQuery);

        QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);
        when(doc.exists()).thenReturn(true);
        when(doc.getId()).thenReturn("cat-1");
        when(doc.getString("name")).thenReturn("Rent & Utilities");
        when(doc.getString("type")).thenReturn("EXPENSE");
        when(doc.getBoolean("isArchived")).thenReturn(false);

        QuerySnapshot querySnap = mock(QuerySnapshot.class);
        when(querySnap.getDocuments()).thenReturn(List.of(doc));

        @SuppressWarnings("unchecked")
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        when(queryFuture.get()).thenReturn(querySnap);
        when(mockQuery.get()).thenReturn(queryFuture);

        Optional<Category> result = repository.findByNameAndType(WS_ID, "rent & utilities", "EXPENSE");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Rent & Utilities");
    }

    @Test
    @DisplayName("listCategories: excludes archived categories when includeArchived=false")
    void listCategories_excludesArchived() throws Exception {
        QueryDocumentSnapshot activeDoc = mock(QueryDocumentSnapshot.class);
        when(activeDoc.exists()).thenReturn(true);
        when(activeDoc.getId()).thenReturn("cat-active");
        when(activeDoc.getString("name")).thenReturn("Active Cat");
        when(activeDoc.getBoolean("isArchived")).thenReturn(false);
        when(activeDoc.getBoolean("isDefault")).thenReturn(false);

        QueryDocumentSnapshot archivedDoc = mock(QueryDocumentSnapshot.class);
        when(archivedDoc.exists()).thenReturn(true);
        when(archivedDoc.getId()).thenReturn("cat-archived");
        when(archivedDoc.getString("name")).thenReturn("Archived Cat");
        when(archivedDoc.getBoolean("isArchived")).thenReturn(true);

        QuerySnapshot querySnap = mock(QuerySnapshot.class);
        when(querySnap.getDocuments()).thenReturn(List.of(activeDoc, archivedDoc));

        @SuppressWarnings("unchecked")
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        when(queryFuture.get()).thenReturn(querySnap);
        when(categoriesCollection.get()).thenReturn(queryFuture);

        List<Category> categories = repository.listCategories(WS_ID, null, false);

        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getId()).isEqualTo("cat-active");
    }

    @Test
    @DisplayName("archiveCategory: default category throws IllegalArgumentException")
    void archiveCategory_whenDefault_throwsIllegalArgument() throws Exception {
        DocumentSnapshot snap = mock(DocumentSnapshot.class);
        when(snap.exists()).thenReturn(true);
        when(snap.getBoolean("isDefault")).thenReturn(true);

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        when(snapFuture.get()).thenReturn(snap);

        Transaction mockTx = mock(Transaction.class);
        when(mockTx.get(categoryDocRef)).thenReturn(snapFuture);

        when(firestore.runTransaction(any())).thenAnswer(invocation -> {
            Transaction.Function<?> function = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            ApiFuture<Object> future = mock(ApiFuture.class);
            when(future.get()).thenThrow(new ExecutionException(new IllegalArgumentException("Default categories cannot be archived: def_1")));
            return future;
        });

        assertThatThrownBy(() -> repository.archiveCategory(WS_ID, "def_1", "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Default categories cannot be archived");
    }

    @Test
    @DisplayName("seedDefaultCategories: writes categories using WriteBatch")
    void seedDefaultCategories_executesBatch() throws Exception {
        WriteBatch mockBatch = mock(WriteBatch.class);
        when(firestore.batch()).thenReturn(mockBatch);
        when(mockBatch.commit()).thenReturn(mock(ApiFuture.class));

        List<Category> defaults = List.of(
                new Category("def-1", WS_ID, "Sales", "INCOME", true, "#10B981", "tag", false, null, null, null, "user", "user")
        );

        repository.seedDefaultCategories(WS_ID, defaults);

        verify(mockBatch).set(any(DocumentReference.class), anyMap(), any(SetOptions.class));
        verify(mockBatch).commit();
    }

    @Test
    @DisplayName("hasAnyCategories: checks emptiness of collection")
    void hasAnyCategories_returnsCorrectBoolean() throws Exception {
        Query mockLimitQuery = mock(Query.class);
        when(categoriesCollection.limit(1)).thenReturn(mockLimitQuery);

        QuerySnapshot emptySnap = mock(QuerySnapshot.class);
        when(emptySnap.isEmpty()).thenReturn(true);

        @SuppressWarnings("unchecked")
        ApiFuture<QuerySnapshot> snapFuture = mock(ApiFuture.class);
        when(snapFuture.get()).thenReturn(emptySnap);
        when(mockLimitQuery.get()).thenReturn(snapFuture);

        boolean exists = repository.hasAnyCategories(WS_ID);
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("failure translates to DatabaseOperationException")
    void failure_translatesToDatabaseOperationException() throws Exception {
        when(categoryDocRef.set(anyMap())).thenReturn(writeFuture);
        when(writeFuture.get()).thenThrow(new ExecutionException("Permission denied", new RuntimeException()));

        Category cat = new Category(null, WS_ID, "Test", "INCOME", false, "#123456", "tag", false, null, null, null, "u1", "u1");

        assertThatThrownBy(() -> repository.createCategory(WS_ID, cat))
                .isInstanceOf(DatabaseOperationException.class)
                .hasMessageContaining("Failed to persist category");
    }

    @Test
    @DisplayName("Throws DatabaseOperationException when Firestore bean is null")
    void unconfiguredFirestore_throwsException() {
        FirestoreCategoryRepository unconfigured = new FirestoreCategoryRepository(null);

        assertThatThrownBy(() -> unconfigured.findById(WS_ID, "cat-1"))
                .isInstanceOf(DatabaseOperationException.class)
                .hasMessageContaining("Firestore client is unconfigured");
    }
}
