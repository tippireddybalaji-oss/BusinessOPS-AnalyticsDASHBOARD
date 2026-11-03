package com.businessops.dashboard.repository;

import com.businessops.dashboard.exception.DatabaseOperationException;
import com.businessops.dashboard.model.Client;
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
 * Unit tests for {@link FirestoreClientRepository}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FirestoreClientRepositoryTest {

    @Mock
    private Firestore firestore;

    @Mock
    private CollectionReference workspacesCollection;

    @Mock
    private DocumentReference workspaceDocRef;

    @Mock
    private CollectionReference clientsCollection;

    @Mock
    private DocumentReference clientDocRef;

    @Mock
    private ApiFuture<WriteResult> writeFuture;

    private FirestoreClientRepository repository;

    private static final String WS_ID = "ws-test-1";

    @BeforeEach
    void setUp() {
        when(firestore.collection("workspaces")).thenReturn(workspacesCollection);
        when(workspacesCollection.document(WS_ID)).thenReturn(workspaceDocRef);
        when(workspaceDocRef.collection("clients")).thenReturn(clientsCollection);
        when(clientsCollection.document()).thenReturn(clientDocRef);
        when(clientsCollection.document(anyString())).thenReturn(clientDocRef);
        when(clientDocRef.getId()).thenReturn("cl-generated-123");

        repository = new FirestoreClientRepository(firestore);
    }

    @Test
    @DisplayName("createClient: persists document with generated ID and timestamps")
    void createClient_persistsSuccessfully() throws Exception {
        when(clientDocRef.set(anyMap())).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        Client client = new Client(null, WS_ID, "Tata Consultancy", "TCS", "tcs@tata.com", "12345", "ACTIVE", "Notes", null, null, "user-1", "user-1");

        Client result = repository.createClient(WS_ID, client);

        assertThat(result.getId()).isEqualTo("cl-generated-123");
        assertThat(result.getWorkspaceId()).isEqualTo(WS_ID);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();

        verify(clientDocRef).set(anyMap());
    }

    @Test
    @DisplayName("findById: returns client entity when document exists")
    void findById_whenExists_returnsClient() throws Exception {
        DocumentSnapshot mockSnap = mock(DocumentSnapshot.class);
        when(mockSnap.exists()).thenReturn(true);
        when(mockSnap.getId()).thenReturn("cl-100");
        when(mockSnap.getString("name")).thenReturn("Infosys");
        when(mockSnap.getString("company")).thenReturn("Infosys Ltd");
        when(mockSnap.getString("status")).thenReturn("ACTIVE");

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        when(snapFuture.get()).thenReturn(mockSnap);
        when(clientDocRef.get()).thenReturn(snapFuture);

        Optional<Client> result = repository.findById(WS_ID, "cl-100");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Infosys");
        assertThat(result.get().getCompany()).isEqualTo("Infosys Ltd");
    }

    @Test
    @DisplayName("findById: returns empty Optional when document does not exist")
    void findById_whenMissing_returnsEmpty() throws Exception {
        DocumentSnapshot mockSnap = mock(DocumentSnapshot.class);
        when(mockSnap.exists()).thenReturn(false);

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        when(snapFuture.get()).thenReturn(mockSnap);
        when(clientDocRef.get()).thenReturn(snapFuture);

        Optional<Client> result = repository.findById(WS_ID, "missing-id");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findClients: filters by search term across name and company")
    void findClients_withSearchFilter_filtersCorrectly() throws Exception {
        QueryDocumentSnapshot doc1 = mock(QueryDocumentSnapshot.class);
        when(doc1.exists()).thenReturn(true);
        when(doc1.getId()).thenReturn("d1");
        when(doc1.getString("name")).thenReturn("Acme Logistics");
        when(doc1.getString("company")).thenReturn("Acme Corp");
        when(doc1.getTimestamp("createdAt")).thenReturn(Timestamp.ofTimeSecondsAndNanos(100, 0));

        QueryDocumentSnapshot doc2 = mock(QueryDocumentSnapshot.class);
        when(doc2.exists()).thenReturn(true);
        when(doc2.getId()).thenReturn("d2");
        when(doc2.getString("name")).thenReturn("Zeta Retail");
        when(doc2.getString("company")).thenReturn("Zeta Global");
        when(doc2.getTimestamp("createdAt")).thenReturn(Timestamp.ofTimeSecondsAndNanos(200, 0));

        QuerySnapshot mockSnapshot = mock(QuerySnapshot.class);
        when(mockSnapshot.getDocuments()).thenReturn(List.of(doc1, doc2));

        @SuppressWarnings("unchecked")
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        when(queryFuture.get()).thenReturn(mockSnapshot);
        when(clientsCollection.get()).thenReturn(queryFuture);

        List<Client> results = repository.findClients(WS_ID, "zeta", null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Zeta Retail");
    }

    @Test
    @DisplayName("updateClient: updates client fields and sets new updatedAt")
    void updateClient_updatesFields() throws Exception {
        when(clientDocRef.update(anyMap())).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        Client client = new Client("cl-1", WS_ID, "Updated Name", "Updated Co", "email@test.com", "999", "LEAD", "Notes", Timestamp.now(), null, "creator", "editor");

        Client updated = repository.updateClient(WS_ID, client);

        assertThat(updated.getUpdatedAt()).isNotNull();
        verify(clientDocRef).update(anyMap());
    }

    @Test
    @DisplayName("deleteClient: deletes document in Firestore")
    void deleteClient_deletesDocument() throws Exception {
        when(clientDocRef.delete()).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        boolean deleted = repository.deleteClient(WS_ID, "cl-1");

        assertThat(deleted).isTrue();
        verify(clientDocRef).delete();
    }

    @Test
    @DisplayName("Translates ExecutionException to DatabaseOperationException")
    void failure_translatesToDatabaseOperationException() throws Exception {
        when(clientDocRef.set(anyMap())).thenReturn(writeFuture);
        when(writeFuture.get()).thenThrow(new ExecutionException("Permission denied", new RuntimeException()));

        Client client = new Client(null, WS_ID, "Fail Client", null, null, null, "ACTIVE", null, null, null, "u", "u");

        assertThatThrownBy(() -> repository.createClient(WS_ID, client))
                .isInstanceOf(DatabaseOperationException.class)
                .hasMessageContaining("Failed to persist client");
    }

    @Test
    @DisplayName("Throws DatabaseOperationException when Firestore bean is null")
    void unconfiguredFirestore_throwsException() {
        FirestoreClientRepository unconfigured = new FirestoreClientRepository(null);

        assertThatThrownBy(() -> unconfigured.findById(WS_ID, "cl-1"))
                .isInstanceOf(DatabaseOperationException.class)
                .hasMessageContaining("Firestore client is unconfigured");
    }
}
