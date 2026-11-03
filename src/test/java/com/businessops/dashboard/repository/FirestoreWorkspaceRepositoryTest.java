package com.businessops.dashboard.repository;

import com.businessops.dashboard.exception.DatabaseOperationException;
import com.businessops.dashboard.model.Workspace;
import com.businessops.dashboard.model.WorkspaceMember;
import com.businessops.dashboard.model.WorkspaceRole;
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

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FirestoreWorkspaceRepository}.
 *
 * <p>Verifies Firestore document mapping, atomic batch write coordination,
 * and translation of execution exceptions into {@link DatabaseOperationException}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FirestoreWorkspaceRepositoryTest {

    @Mock
    private Firestore firestore;

    @Mock
    private CollectionReference workspacesCollection;

    @Mock
    private DocumentReference workspaceDocRef;

    @Mock
    private CollectionReference membersCollection;

    @Mock
    private DocumentReference memberDocRef;

    @Mock
    private WriteBatch writeBatch;

    @Mock
    private ApiFuture<java.util.List<WriteResult>> batchFuture;

    private FirestoreWorkspaceRepository repository;

    @BeforeEach
    void setUp() {
        repository = new FirestoreWorkspaceRepository(firestore);
    }

    @Test
    @DisplayName("createWorkspace: executes atomic batch write for workspace and owner member")
    void createWorkspace_executesAtomicBatchWrite() throws Exception {
        when(firestore.collection("workspaces")).thenReturn(workspacesCollection);
        when(workspacesCollection.document()).thenReturn(workspaceDocRef);
        when(workspacesCollection.document(anyString())).thenReturn(workspaceDocRef);
        when(workspaceDocRef.getId()).thenReturn("ws-generated-123");
        when(workspaceDocRef.collection("members")).thenReturn(membersCollection);
        when(membersCollection.document(anyString())).thenReturn(memberDocRef);
        when(firestore.batch()).thenReturn(writeBatch);
        when(writeBatch.set(eq(workspaceDocRef), anyMap())).thenReturn(writeBatch);
        when(writeBatch.set(eq(memberDocRef), anyMap())).thenReturn(writeBatch);
        when(writeBatch.commit()).thenReturn(batchFuture);
        when(batchFuture.get()).thenReturn(java.util.List.of());

        Workspace ws = new Workspace(null, "Test Workspace", "owner-1", "INR", null, null);
        WorkspaceMember member = new WorkspaceMember("owner-1", WorkspaceRole.OWNER, null);

        Workspace result = repository.createWorkspace(ws, member);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("ws-generated-123");
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(member.getJoinedAt()).isNotNull();

        verify(writeBatch).set(eq(workspaceDocRef), anyMap());
        verify(writeBatch).set(eq(memberDocRef), anyMap());
        verify(writeBatch).commit();
    }

    @Test
    @DisplayName("createWorkspace: translates ExecutionException into DatabaseOperationException")
    void createWorkspace_onFailure_throwsDatabaseOperationException() throws Exception {
        when(firestore.collection("workspaces")).thenReturn(workspacesCollection);
        when(workspacesCollection.document(anyString())).thenReturn(workspaceDocRef);
        when(workspacesCollection.document()).thenReturn(workspaceDocRef);
        when(workspaceDocRef.getId()).thenReturn("ws-fail-id");
        when(workspaceDocRef.collection("members")).thenReturn(membersCollection);
        when(membersCollection.document(anyString())).thenReturn(memberDocRef);
        when(firestore.batch()).thenReturn(writeBatch);
        when(writeBatch.commit()).thenReturn(batchFuture);
        when(batchFuture.get()).thenThrow(new ExecutionException("Firestore quota exceeded", new RuntimeException()));

        Workspace ws = new Workspace("ws-id", "Fail Workspace", "owner-1", "INR", null, null);
        WorkspaceMember member = new WorkspaceMember("owner-1", WorkspaceRole.OWNER, null);

        assertThatThrownBy(() -> repository.createWorkspace(ws, member))
                .isInstanceOf(DatabaseOperationException.class)
                .hasMessageContaining("Failed to persist workspace in database");
    }

    @Test
    @DisplayName("findById: returns empty Optional when document does not exist")
    void findById_whenNonExistent_returnsEmpty() throws Exception {
        DocumentSnapshot mockSnapshot = mock(DocumentSnapshot.class);
        when(mockSnapshot.exists()).thenReturn(false);

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        when(snapFuture.get()).thenReturn(mockSnapshot);

        when(firestore.collection("workspaces")).thenReturn(workspacesCollection);
        when(workspacesCollection.document("missing-ws")).thenReturn(workspaceDocRef);
        when(workspaceDocRef.get()).thenReturn(snapFuture);

        Optional<Workspace> result = repository.findById("missing-ws");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findById: correctly maps existing Firestore document fields")
    void findById_whenExists_mapsFields() throws Exception {
        DocumentSnapshot mockSnapshot = mock(DocumentSnapshot.class);
        when(mockSnapshot.exists()).thenReturn(true);
        when(mockSnapshot.getId()).thenReturn("ws-valid");
        when(mockSnapshot.getString("name")).thenReturn("Enterprise Corp");
        when(mockSnapshot.getString("ownerUid")).thenReturn("owner-123");
        when(mockSnapshot.getString("defaultCurrency")).thenReturn("INR");
        when(mockSnapshot.getTimestamp("createdAt")).thenReturn(Timestamp.now());
        when(mockSnapshot.getTimestamp("updatedAt")).thenReturn(Timestamp.now());

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        when(snapFuture.get()).thenReturn(mockSnapshot);

        when(firestore.collection("workspaces")).thenReturn(workspacesCollection);
        when(workspacesCollection.document("ws-valid")).thenReturn(workspaceDocRef);
        when(workspaceDocRef.get()).thenReturn(snapFuture);

        Optional<Workspace> result = repository.findById("ws-valid");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Enterprise Corp");
        assertThat(result.get().getOwnerUid()).isEqualTo("owner-123");
        assertThat(result.get().getDefaultCurrency()).isEqualTo("INR");
    }

    @Test
    @DisplayName("Throws DatabaseOperationException when Firestore bean is null")
    void unconfiguredFirestore_throwsDatabaseOperationException() {
        FirestoreWorkspaceRepository unconfiguredRepo = new FirestoreWorkspaceRepository(null);

        Workspace ws = new Workspace();
        WorkspaceMember member = new WorkspaceMember();

        assertThatThrownBy(() -> unconfiguredRepo.createWorkspace(ws, member))
                .isInstanceOf(DatabaseOperationException.class)
                .hasMessageContaining("Firestore client is unconfigured");
    }
}
