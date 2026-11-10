package com.businessops.dashboard.service;

import com.businessops.dashboard.dto.ClientCreateRequest;
import com.businessops.dashboard.dto.ClientResponse;
import com.businessops.dashboard.dto.ClientUpdateRequest;
import com.businessops.dashboard.dto.PagedResponse;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.exception.WorkspaceAccessDeniedException;
import com.businessops.dashboard.model.Client;
import com.businessops.dashboard.model.WorkspaceRole;
import com.businessops.dashboard.repository.ClientRepository;
import com.google.cloud.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ClientService}.
 *
 * <p>Verifies client lifecycle, role-based authorization (OWNER/ADMIN write vs MEMBER read-only),
 * field preservation during updates, and cross-workspace access control.
 */
@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private ClientRepository clientRepository;

    private ClientService clientService;

    private static final String AUTH_UID = "user-auth-123";
    private static final String WS_ID = "ws-target-456";

    @BeforeEach
    void setUp() {
        clientService = new ClientService(workspaceService, clientRepository);
    }

    // ── CREATE CLIENT ────────────────────────────────────────────────────────

    @Test
    @DisplayName("createClient: OWNER or ADMIN successfully creates client with defaulted ACTIVE status")
    void createClient_withOwnerRole_createsSuccessfully() {
        ClientCreateRequest request = new ClientCreateRequest(
                "Acme Global", "Acme Inc", "acme@example.com", "+919876543210", null, "Key enterprise client"
        );

        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.OWNER);

        Client savedClient = new Client(
                "cl-1", WS_ID, "Acme Global", "Acme Inc", "acme@example.com",
                "+919876543210", "ACTIVE", "Key enterprise client",
                Timestamp.now(), Timestamp.now(), AUTH_UID, AUTH_UID
        );
        when(clientRepository.createClient(eq(WS_ID), any(Client.class))).thenReturn(savedClient);

        ClientResponse response = clientService.createClient(AUTH_UID, WS_ID, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("cl-1");
        assertThat(response.workspaceId()).isEqualTo(WS_ID);
        assertThat(response.name()).isEqualTo("Acme Global");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.createdBy()).isEqualTo(AUTH_UID);
        assertThat(response.updatedBy()).isEqualTo(AUTH_UID);

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).createClient(eq(WS_ID), captor.capture());
        Client captured = captor.getValue();
        assertThat(captured.getCreatedBy()).isEqualTo(AUTH_UID);
        assertThat(captured.getUpdatedBy()).isEqualTo(AUTH_UID);
        assertThat(captured.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("createClient: MEMBER role is rejected with 403 Forbidden")
    void createClient_whenMemberRole_throwsAccessDenied() {
        ClientCreateRequest request = new ClientCreateRequest(
                "Unauthorized Client", "Company", "info@test.com", "123456", "ACTIVE", null
        );

        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID))
                .thenThrow(new WorkspaceAccessDeniedException("Insufficient permissions: Member role cannot perform write operations"));

        assertThatThrownBy(() -> clientService.createClient(AUTH_UID, WS_ID, request))
                .isInstanceOf(WorkspaceAccessDeniedException.class)
                .hasMessageContaining("Insufficient permissions");

        verify(clientRepository, never()).createClient(any(), any());
    }

    @Test
    @DisplayName("createClient: invalid status value throws IllegalArgumentException")
    void createClient_withInvalidStatus_throwsIllegalArgumentException() {
        ClientCreateRequest request = new ClientCreateRequest(
                "Bad Status Client", "Co", "email@test.com", "123", "SUSPENDED", null
        );

        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.OWNER);

        assertThatThrownBy(() -> clientService.createClient(AUTH_UID, WS_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid client status: 'SUSPENDED'");

        verify(clientRepository, never()).createClient(any(), any());
    }

    // ── GET CLIENT DETAILS ───────────────────────────────────────────────────

    @Test
    @DisplayName("getClientById: MEMBER role successfully reads client")
    void getClientById_whenMember_returnsClient() {
        when(workspaceService.verifyWorkspaceAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.MEMBER);

        Client client = new Client(
                "cl-100", WS_ID, "Client 100", "Corp", "c100@test.com",
                "99999", "ACTIVE", "Notes", Timestamp.now(), Timestamp.now(), "creator-uid", "creator-uid"
        );
        when(clientRepository.findById(WS_ID, "cl-100")).thenReturn(Optional.of(client));

        ClientResponse response = clientService.getClientById(AUTH_UID, WS_ID, "cl-100");

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("cl-100");
        assertThat(response.name()).isEqualTo("Client 100");
    }

    @Test
    @DisplayName("getClientById: non-existent client throws ResourceNotFoundException (404)")
    void getClientById_whenNotFound_throwsNotFound() {
        when(workspaceService.verifyWorkspaceAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.OWNER);
        when(clientRepository.findById(WS_ID, "missing-cl")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getClientById(AUTH_UID, WS_ID, "missing-cl"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Client not found with id: missing-cl in workspace: " + WS_ID);
    }

    // ── LIST CLIENTS & PAGINATION ────────────────────────────────────────────

    @Test
    @DisplayName("listClients: retrieves paginated clients and applies pagination metadata")
    void listClients_withPagination_returnsPagedResponse() {
        when(workspaceService.verifyWorkspaceAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.MEMBER);

        Client c1 = new Client("c1", WS_ID, "Alpha", "A Corp", "a@a.com", "1", "ACTIVE", null, Timestamp.now(), Timestamp.now(), AUTH_UID, AUTH_UID);
        Client c2 = new Client("c2", WS_ID, "Beta", "B Corp", "b@b.com", "2", "ACTIVE", null, Timestamp.now(), Timestamp.now(), AUTH_UID, AUTH_UID);
        Client c3 = new Client("c3", WS_ID, "Gamma", "G Corp", "g@g.com", "3", "ACTIVE", null, Timestamp.now(), Timestamp.now(), AUTH_UID, AUTH_UID);

        when(clientRepository.findClients(WS_ID, "Corp", "ACTIVE")).thenReturn(List.of(c1, c2, c3));

        // Request page 0, size 2
        PagedResponse<ClientResponse> page0 = clientService.listClients(AUTH_UID, WS_ID, "Corp", "ACTIVE", 0, 2);

        assertThat(page0.content()).hasSize(2);
        assertThat(page0.totalElements()).isEqualTo(3);
        assertThat(page0.totalPages()).isEqualTo(2);
        assertThat(page0.hasMore()).isTrue();
        assertThat(page0.content().get(0).name()).isEqualTo("Alpha");
        assertThat(page0.content().get(1).name()).isEqualTo("Beta");

        // Request page 1, size 2
        PagedResponse<ClientResponse> page1 = clientService.listClients(AUTH_UID, WS_ID, "Corp", "ACTIVE", 1, 2);

        assertThat(page1.content()).hasSize(1);
        assertThat(page1.content().get(0).name()).isEqualTo("Gamma");
        assertThat(page1.hasMore()).isFalse();
    }

    // ── UPDATE CLIENT ────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateClient: preserves original createdAt and createdBy while updating fields and updatedBy")
    void updateClient_preservesCreatedMetadata() {
        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.ADMIN);

        Timestamp originalCreated = Timestamp.ofTimeSecondsAndNanos(1000000, 0);
        Client existing = new Client(
                "cl-update", WS_ID, "Old Name", "Old Company", "old@test.com",
                "1111", "ACTIVE", "Old notes", originalCreated, originalCreated, "original-creator-uid", "original-creator-uid"
        );
        when(clientRepository.findById(WS_ID, "cl-update")).thenReturn(Optional.of(existing));

        ClientUpdateRequest updateRequest = new ClientUpdateRequest(
                "New Name", "New Company", "new@test.com", "2222", "LEAD", "Updated notes"
        );

        when(clientRepository.updateClient(eq(WS_ID), any(Client.class))).thenAnswer(invocation -> invocation.getArgument(1));

        ClientResponse updated = clientService.updateClient(AUTH_UID, WS_ID, "cl-update", updateRequest);

        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.company()).isEqualTo("New Company");
        assertThat(updated.status()).isEqualTo("LEAD");
        assertThat(updated.createdBy()).isEqualTo("original-creator-uid"); // Preserved!
        assertThat(updated.updatedBy()).isEqualTo(AUTH_UID); // Updated!
    }

    // ── DELETE CLIENT ────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteClient: OWNER or ADMIN successfully deletes client")
    void deleteClient_whenAuthorized_deletesSuccessfully() {
        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.OWNER);

        Client client = new Client("cl-del", WS_ID, "To Delete", null, null, null, "ACTIVE", null, Timestamp.now(), Timestamp.now(), AUTH_UID, AUTH_UID);
        when(clientRepository.findById(WS_ID, "cl-del")).thenReturn(Optional.of(client));
        when(clientRepository.deleteClient(WS_ID, "cl-del")).thenReturn(true);

        clientService.deleteClient(AUTH_UID, WS_ID, "cl-del");

        verify(clientRepository).deleteClient(WS_ID, "cl-del");
    }

    @Test
    @DisplayName("deleteClient: non-existent client throws ResourceNotFoundException (404)")
    void deleteClient_whenNotFound_throwsNotFound() {
        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.OWNER);
        when(clientRepository.findById(WS_ID, "missing-cl")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.deleteClient(AUTH_UID, WS_ID, "missing-cl"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Client not found with id: missing-cl");

        verify(clientRepository, never()).deleteClient(any(), any());
    }
}

