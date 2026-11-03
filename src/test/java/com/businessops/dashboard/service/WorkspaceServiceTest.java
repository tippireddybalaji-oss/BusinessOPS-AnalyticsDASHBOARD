package com.businessops.dashboard.service;

import com.businessops.dashboard.dto.WorkspaceCreateRequest;
import com.businessops.dashboard.dto.WorkspaceResponse;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.exception.WorkspaceAccessDeniedException;
import com.businessops.dashboard.model.Workspace;
import com.businessops.dashboard.model.WorkspaceMember;
import com.businessops.dashboard.model.WorkspaceRole;
import com.businessops.dashboard.repository.WorkspaceRepository;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WorkspaceService}.
 *
 * <p>Verifies business rules, automatic owner assignment, currency normalization,
 * and server-side authorization enforcement.
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        workspaceService = new WorkspaceService(workspaceRepository);
    }

    // ── Workspace Creation ───────────────────────────────────────────────────

    @Test
    @DisplayName("createWorkspace: assigns authenticated UID as owner, defaults to INR, creates OWNER member")
    void createWorkspace_withDefaultCurrency_createsOwnerAndWorkspace() {
        String authUid = "user-123";
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("Acme Ops", null);

        Workspace savedWs = new Workspace("ws-abc", "Acme Ops", authUid, "INR", Timestamp.now(), Timestamp.now());
        when(workspaceRepository.createWorkspace(any(Workspace.class), any(WorkspaceMember.class)))
                .thenReturn(savedWs);

        WorkspaceResponse response = workspaceService.createWorkspace(authUid, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("ws-abc");
        assertThat(response.name()).isEqualTo("Acme Ops");
        assertThat(response.ownerUid()).isEqualTo(authUid);
        assertThat(response.defaultCurrency()).isEqualTo("INR");
        assertThat(response.role()).isEqualTo(WorkspaceRole.OWNER);

        ArgumentCaptor<Workspace> wsCaptor = ArgumentCaptor.forClass(Workspace.class);
        ArgumentCaptor<WorkspaceMember> memberCaptor = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(workspaceRepository).createWorkspace(wsCaptor.capture(), memberCaptor.capture());

        Workspace capturedWs = wsCaptor.getValue();
        assertThat(capturedWs.getName()).isEqualTo("Acme Ops");
        assertThat(capturedWs.getOwnerUid()).isEqualTo(authUid);
        assertThat(capturedWs.getDefaultCurrency()).isEqualTo("INR");

        WorkspaceMember capturedMember = memberCaptor.getValue();
        assertThat(capturedMember.getUid()).isEqualTo(authUid);
        assertThat(capturedMember.getRole()).isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    @DisplayName("createWorkspace: normalizes valid custom currency to uppercase")
    void createWorkspace_withCustomCurrency_normalizesToUppercase() {
        String authUid = "user-456";
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("Global Corp", "usd");

        Workspace savedWs = new Workspace("ws-xyz", "Global Corp", authUid, "USD", Timestamp.now(), Timestamp.now());
        when(workspaceRepository.createWorkspace(any(), any())).thenReturn(savedWs);

        WorkspaceResponse response = workspaceService.createWorkspace(authUid, request);

        assertThat(response.defaultCurrency()).isEqualTo("USD");

        ArgumentCaptor<Workspace> wsCaptor = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceRepository).createWorkspace(wsCaptor.capture(), any());
        assertThat(wsCaptor.getValue().getDefaultCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("createWorkspace: rejects unsupported currency with IllegalArgumentException")
    void createWorkspace_withUnsupportedCurrency_throwsException() {
        String authUid = "user-123";
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("Crypto Co", "BITCOIN");

        assertThatThrownBy(() -> workspaceService.createWorkspace(authUid, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported currency: 'BITCOIN'");

        verify(workspaceRepository, never()).createWorkspace(any(), any());
    }

    // ── Workspace Retrieval & Authorization ──────────────────────────────────

    @Test
    @DisplayName("getWorkspaceById: returns workspace when user is the OWNER")
    void getWorkspaceById_whenUserIsOwner_returnsWorkspace() {
        String authUid = "owner-uid";
        String wsId = "ws-100";
        Workspace ws = new Workspace(wsId, "Owner Workspace", authUid, "INR", Timestamp.now(), Timestamp.now());

        when(workspaceRepository.findById(wsId)).thenReturn(Optional.of(ws));
        when(workspaceRepository.findMember(wsId, authUid)).thenReturn(Optional.empty()); // ownerUid fallback

        WorkspaceResponse response = workspaceService.getWorkspaceById(authUid, wsId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(wsId);
        assertThat(response.role()).isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    @DisplayName("getWorkspaceById: returns workspace when user is a MEMBER")
    void getWorkspaceById_whenUserIsMember_returnsWorkspaceWithMemberRole() {
        String authUid = "member-uid";
        String wsId = "ws-200";
        Workspace ws = new Workspace(wsId, "Team Workspace", "different-owner", "INR", Timestamp.now(), Timestamp.now());
        WorkspaceMember member = new WorkspaceMember(authUid, WorkspaceRole.MEMBER, Timestamp.now());

        when(workspaceRepository.findById(wsId)).thenReturn(Optional.of(ws));
        when(workspaceRepository.findMember(wsId, authUid)).thenReturn(Optional.of(member));

        WorkspaceResponse response = workspaceService.getWorkspaceById(authUid, wsId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(wsId);
        assertThat(response.role()).isEqualTo(WorkspaceRole.MEMBER);
    }

    @Test
    @DisplayName("getWorkspaceById: throws ResourceNotFoundException (404) when workspace does not exist")
    void getWorkspaceById_whenNonExistent_throwsNotFound() {
        when(workspaceRepository.findById("missing-ws")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.getWorkspaceById("any-user", "missing-ws"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Workspace not found with id: missing-ws");
    }

    @Test
    @DisplayName("getWorkspaceById: throws WorkspaceAccessDeniedException (403) when user is not a member or owner")
    void getWorkspaceById_whenUnauthorized_throwsAccessDenied() {
        String authUid = "unauthorized-user";
        String wsId = "ws-restricted";
        Workspace ws = new Workspace(wsId, "Private Workspace", "different-owner", "INR", Timestamp.now(), Timestamp.now());

        when(workspaceRepository.findById(wsId)).thenReturn(Optional.of(ws));
        when(workspaceRepository.findMember(wsId, authUid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.getWorkspaceById(authUid, wsId))
                .isInstanceOf(WorkspaceAccessDeniedException.class)
                .hasMessageContaining("You do not have permission to access workspace: ws-restricted");
    }

    // ── Workspace Listing ────────────────────────────────────────────────────

    @Test
    @DisplayName("listWorkspacesForUser: returns all workspaces accessible to user")
    void listWorkspacesForUser_returnsAccessibleWorkspaces() {
        String authUid = "user-1";
        Workspace ws1 = new Workspace("ws-1", "My Workspace", authUid, "INR", Timestamp.now(), Timestamp.now());
        Workspace ws2 = new Workspace("ws-2", "Shared Workspace", "other-owner", "USD", Timestamp.now(), Timestamp.now());

        when(workspaceRepository.findWorkspacesByMemberUid(authUid)).thenReturn(List.of(ws1, ws2));
        when(workspaceRepository.findMember("ws-2", authUid))
                .thenReturn(Optional.of(new WorkspaceMember(authUid, WorkspaceRole.ADMIN, Timestamp.now())));

        List<WorkspaceResponse> results = workspaceService.listWorkspacesForUser(authUid);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo("ws-1");
        assertThat(results.get(0).role()).isEqualTo(WorkspaceRole.OWNER);
        assertThat(results.get(1).id()).isEqualTo("ws-2");
        assertThat(results.get(1).role()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    @DisplayName("listWorkspacesForUser: returns empty list when user belongs to no workspaces")
    void listWorkspacesForUser_whenNoneExist_returnsEmptyList() {
        String authUid = "isolated-user";
        when(workspaceRepository.findWorkspacesByMemberUid(authUid)).thenReturn(List.of());

        List<WorkspaceResponse> results = workspaceService.listWorkspacesForUser(authUid);

        assertThat(results).isNotNull().isEmpty();
    }
}
