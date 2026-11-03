package com.businessops.dashboard.controller;

import com.businessops.dashboard.config.SecurityConfig;
import com.businessops.dashboard.dto.WorkspaceCreateRequest;
import com.businessops.dashboard.dto.WorkspaceResponse;
import com.businessops.dashboard.exception.GlobalExceptionHandler;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.exception.WorkspaceAccessDeniedException;
import com.businessops.dashboard.model.WorkspaceRole;
import com.businessops.dashboard.security.FirebaseAuthFilter;
import com.businessops.dashboard.security.SecurityUtils;
import com.businessops.dashboard.service.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link WorkspaceController}.
 *
 * <p>Tests endpoint contracts, Jakarta Bean Validation, server-side authorization responses (200, 201, 400, 401, 403, 404),
 * and verifies that spoofing attempts (e.g. client injecting ownerUid/role) are rejected.
 */
@WebMvcTest(controllers = WorkspaceController.class)
@Import({SecurityConfig.class, FirebaseAuthFilter.class, GlobalExceptionHandler.class})
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WorkspaceService workspaceService;

    @MockitoBean
    private SecurityUtils securityUtils;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    private static final String VALID_TOKEN = "valid.firebase.token";
    private static final String TEST_UID = "user-test-123";

    @BeforeEach
    void setupAuth() throws Exception {
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn(TEST_UID);
        when(mockToken.getEmail()).thenReturn("test@businessops.io");

        when(firebaseAuth.verifyIdToken(VALID_TOKEN)).thenReturn(mockToken);
        when(securityUtils.getAuthenticatedUid()).thenReturn(TEST_UID);
    }

    // ── POST /api/v1/workspaces ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/workspaces with valid payload → 201 Created")
    void createWorkspace_validPayload_returns201() throws Exception {
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("FinTech Corp", "INR");
        WorkspaceResponse response = new WorkspaceResponse(
                "ws-fintech-1", "FinTech Corp", TEST_UID, "INR",
                Instant.now(), Instant.now(), WorkspaceRole.OWNER
        );

        when(workspaceService.createWorkspace(eq(TEST_UID), any(WorkspaceCreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isCreated())
               .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.id").value("ws-fintech-1"))
               .andExpect(jsonPath("$.name").value("FinTech Corp"))
               .andExpect(jsonPath("$.ownerUid").value(TEST_UID))
               .andExpect(jsonPath("$.defaultCurrency").value("INR"))
               .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    @DisplayName("POST /api/v1/workspaces with blank name → 400 Bad Request with field validation errors")
    void createWorkspace_blankName_returns400() throws Exception {
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("", "INR");

        mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.status").value(400))
               .andExpect(jsonPath("$.error").value("Validation Failed"))
               .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    @DisplayName("POST /api/v1/workspaces with single-character name → 400 Bad Request")
    void createWorkspace_shortName_returns400() throws Exception {
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("A", "INR");

        mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.status").value(400))
               .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    @DisplayName("POST /api/v1/workspaces with unsupported currency → 400 Bad Request")
    void createWorkspace_invalidCurrency_returns400() throws Exception {
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("Valid Name", "DOGECOIN");
        when(workspaceService.createWorkspace(eq(TEST_UID), any()))
                .thenThrow(new IllegalArgumentException("Unsupported currency: 'DOGECOIN'"));

        mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.status").value(400))
               .andExpect(jsonPath("$.error").value("Invalid Argument"));
    }

    @Test
    @DisplayName("POST /api/v1/workspaces without token → 401 Unauthorized")
    void createWorkspace_withoutToken_returns401() throws Exception {
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("No Auth Corp", "INR");

        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/workspaces ignores client spoofed ownerUid and role in JSON body")
    void createWorkspace_ignoresClientSuppliedOwnerAndRole() throws Exception {
        // Attacker attempts to pass ownerUid="hacker" and role="ADMIN"
        String maliciousPayload = """
            {
                "name": "Target Business",
                "defaultCurrency": "INR",
                "ownerUid": "hacker-victim-uid",
                "role": "ADMIN"
            }
            """;

        WorkspaceResponse response = new WorkspaceResponse(
                "ws-secure-1", "Target Business", TEST_UID, "INR",
                Instant.now(), Instant.now(), WorkspaceRole.OWNER
        );

        when(workspaceService.createWorkspace(eq(TEST_UID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousPayload))
               .andExpect(status().isCreated())
               // Asserts that server set the verified UID as ownerUid, not the attacker's
               .andExpect(jsonPath("$.ownerUid").value(TEST_UID))
               .andExpect(jsonPath("$.role").value("OWNER"));
    }

    // ── GET /api/v1/workspaces ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/workspaces with valid token → 200 OK with list of workspaces")
    void listWorkspaces_returns200WithList() throws Exception {
        WorkspaceResponse ws = new WorkspaceResponse(
                "ws-1", "Alpha Logistics", TEST_UID, "INR",
                Instant.now(), Instant.now(), WorkspaceRole.OWNER
        );
        when(workspaceService.listWorkspacesForUser(TEST_UID)).thenReturn(List.of(ws));

        mockMvc.perform(get("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$[0].id").value("ws-1"))
               .andExpect(jsonPath("$[0].name").value("Alpha Logistics"))
               .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    @DisplayName("GET /api/v1/workspaces when user belongs to no workspaces → 200 OK with empty array []")
    void listWorkspaces_empty_returnsEmptyArray() throws Exception {
        when(workspaceService.listWorkspacesForUser(TEST_UID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("GET /api/v1/workspaces without token → 401 Unauthorized")
    void listWorkspaces_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces"))
               .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/workspaces/{id} ──────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/workspaces/{id} with authorized member → 200 OK with workspace details")
    void getWorkspaceById_authorized_returns200() throws Exception {
        WorkspaceResponse ws = new WorkspaceResponse(
                "ws-target", "Target Operations", TEST_UID, "INR",
                Instant.now(), Instant.now(), WorkspaceRole.OWNER
        );
        when(workspaceService.getWorkspaceById(TEST_UID, "ws-target")).thenReturn(ws);

        mockMvc.perform(get("/api/v1/workspaces/ws-target")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value("ws-target"))
               .andExpect(jsonPath("$.name").value("Target Operations"));
    }

    @Test
    @DisplayName("GET /api/v1/workspaces/{id} for non-existent workspace → 404 Not Found")
    void getWorkspaceById_nonExistent_returns404() throws Exception {
        when(workspaceService.getWorkspaceById(TEST_UID, "missing-id"))
                .thenThrow(new ResourceNotFoundException("Workspace not found with id: missing-id"));

        mockMvc.perform(get("/api/v1/workspaces/missing-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.status").value(404))
               .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("GET /api/v1/workspaces/{id} when user is not a member (cross-workspace access) → 403 Forbidden")
    void getWorkspaceById_unauthorized_returns403() throws Exception {
        when(workspaceService.getWorkspaceById(TEST_UID, "other-user-workspace"))
                .thenThrow(new WorkspaceAccessDeniedException("You do not have permission to access workspace: other-user-workspace"));

        mockMvc.perform(get("/api/v1/workspaces/other-user-workspace")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.status").value(403))
               .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("GET /api/v1/workspaces/{id} without token → 401 Unauthorized")
    void getWorkspaceById_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/any-id"))
               .andExpect(status().isUnauthorized());
    }
}
