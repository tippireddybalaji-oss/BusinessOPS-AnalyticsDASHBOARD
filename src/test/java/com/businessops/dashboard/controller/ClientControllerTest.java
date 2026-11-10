package com.businessops.dashboard.controller;

import com.businessops.dashboard.config.SecurityConfig;
import com.businessops.dashboard.dto.ClientCreateRequest;
import com.businessops.dashboard.dto.ClientResponse;
import com.businessops.dashboard.dto.ClientUpdateRequest;
import com.businessops.dashboard.dto.PagedResponse;
import com.businessops.dashboard.exception.GlobalExceptionHandler;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.exception.WorkspaceAccessDeniedException;
import com.businessops.dashboard.security.FirebaseAuthFilter;
import com.businessops.dashboard.security.SecurityUtils;
import com.businessops.dashboard.service.ClientService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link ClientController}.
 *
 * <p>Verifies REST contracts, pagination envelopes, Jakarta validation,
 * and role-based authorization responses (200, 201, 204, 400, 401, 403, 404).
 */
@WebMvcTest(controllers = ClientController.class)
@Import({SecurityConfig.class, FirebaseAuthFilter.class, GlobalExceptionHandler.class})
class ClientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ClientService clientService;

    @MockitoBean
    private SecurityUtils securityUtils;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    private static final String VALID_TOKEN = "valid.firebase.token";
    private static final String TEST_UID = "user-test-789";
    private static final String WS_ID = "ws-abc-123";

    @BeforeEach
    void setupAuth() throws Exception {
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn(TEST_UID);
        when(mockToken.getEmail()).thenReturn("tester@businessops.io");

        when(firebaseAuth.verifyIdToken(VALID_TOKEN)).thenReturn(mockToken);
        when(securityUtils.getAuthenticatedUid()).thenReturn(TEST_UID);
    }

    // ── CREATE CLIENT (POST) ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/workspaces/{id}/clients with valid payload → 201 Created")
    void createClient_valid_returns201() throws Exception {
        ClientCreateRequest request = new ClientCreateRequest(
                "Reliance Retail", "Reliance Industries", "contact@reliance.com", "+912224908000", "ACTIVE", "Large client"
        );
        ClientResponse response = new ClientResponse(
                "cl-1", WS_ID, "Reliance Retail", "Reliance Industries",
                "contact@reliance.com", "+912224908000", "ACTIVE", "Large client",
                Instant.now(), Instant.now(), TEST_UID, TEST_UID
        );

        when(clientService.createClient(eq(TEST_UID), eq(WS_ID), any(ClientCreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/workspaces/" + WS_ID + "/clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isCreated())
               .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.id").value("cl-1"))
               .andExpect(jsonPath("$.name").value("Reliance Retail"))
               .andExpect(jsonPath("$.status").value("ACTIVE"))
               .andExpect(jsonPath("$.createdBy").value(TEST_UID));
    }

    @Test
    @DisplayName("POST /api/v1/workspaces/{id}/clients with blank name → 400 Bad Request")
    void createClient_blankName_returns400() throws Exception {
        ClientCreateRequest request = new ClientCreateRequest(
                "", "Co", "valid@email.com", "123", "ACTIVE", null
        );

        mockMvc.perform(post("/api/v1/workspaces/" + WS_ID + "/clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.status").value(400))
               .andExpect(jsonPath("$.error").value("Validation Failed"))
               .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    @DisplayName("POST /api/v1/workspaces/{id}/clients with invalid email → 400 Bad Request")
    void createClient_invalidEmail_returns400() throws Exception {
        ClientCreateRequest request = new ClientCreateRequest(
                "Valid Name", "Co", "not-an-email", "123", "ACTIVE", null
        );

        mockMvc.perform(post("/api/v1/workspaces/" + WS_ID + "/clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.status").value(400))
               .andExpect(jsonPath("$.validationErrors.email").exists());
    }

    @Test
    @DisplayName("POST /api/v1/workspaces/{id}/clients without token → 401 Unauthorized")
    void createClient_withoutToken_returns401() throws Exception {
        ClientCreateRequest request = new ClientCreateRequest("Client", null, null, null, null, null);

        mockMvc.perform(post("/api/v1/workspaces/" + WS_ID + "/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/workspaces/{id}/clients when user has MEMBER role → 403 Forbidden")
    void createClient_memberRole_returns403() throws Exception {
        ClientCreateRequest request = new ClientCreateRequest("Client", null, null, null, null, null);
        when(clientService.createClient(eq(TEST_UID), eq(WS_ID), any()))
                .thenThrow(new WorkspaceAccessDeniedException("Insufficient permissions: Member role cannot perform write operations"));

        mockMvc.perform(post("/api/v1/workspaces/" + WS_ID + "/clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.status").value(403))
               .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    // ── LIST CLIENTS (GET) ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/workspaces/{id}/clients with pagination → 200 OK with PagedResponse")
    void listClients_returns200WithPagination() throws Exception {
        ClientResponse c1 = new ClientResponse("c1", WS_ID, "Client One", "Co 1", "c1@co.com", "111", "ACTIVE", null, Instant.now(), Instant.now(), TEST_UID, TEST_UID);
        PagedResponse<ClientResponse> pagedResponse = new PagedResponse<>(List.of(c1), 0, 20, 1, 1, false);

        when(clientService.listClients(eq(TEST_UID), eq(WS_ID), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/workspaces/" + WS_ID + "/clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.content[0].id").value("c1"))
               .andExpect(jsonPath("$.page").value(0))
               .andExpect(jsonPath("$.size").value(20))
               .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/workspaces/{id}/clients with search and status params → 200 OK")
    void listClients_withFilters_returnsFilteredResults() throws Exception {
        PagedResponse<ClientResponse> pagedResponse = new PagedResponse<>(List.of(), 0, 10, 0, 0, false);

        when(clientService.listClients(eq(TEST_UID), eq(WS_ID), eq("Acme"), eq("LEAD"), eq(0), eq(10)))
                .thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/workspaces/" + WS_ID + "/clients?search=Acme&status=LEAD&page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content").isArray())
               .andExpect(jsonPath("$.content").isEmpty());
    }

    // ── GET CLIENT DETAILS (GET /{clientId}) ─────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/workspaces/{id}/clients/{clientId} → 200 OK with client details")
    void getClientById_authorized_returns200() throws Exception {
        ClientResponse response = new ClientResponse(
                "cl-target", WS_ID, "Target Client", "Corp", "target@corp.com", "999", "ACTIVE", "Notes",
                Instant.now(), Instant.now(), TEST_UID, TEST_UID
        );
        when(clientService.getClientById(TEST_UID, WS_ID, "cl-target")).thenReturn(response);

        mockMvc.perform(get("/api/v1/workspaces/" + WS_ID + "/clients/cl-target")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value("cl-target"))
               .andExpect(jsonPath("$.name").value("Target Client"));
    }

    @Test
    @DisplayName("GET /api/v1/workspaces/{id}/clients/{clientId} when not found → 404 Not Found")
    void getClientById_notFound_returns404() throws Exception {
        when(clientService.getClientById(TEST_UID, WS_ID, "missing-cl"))
                .thenThrow(new ResourceNotFoundException("Client not found with id: missing-cl"));

        mockMvc.perform(get("/api/v1/workspaces/" + WS_ID + "/clients/missing-cl")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.status").value(404))
               .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("GET /api/v1/workspaces/{id}/clients/{clientId} unauthorized workspace → 403 Forbidden")
    void getClientById_unauthorized_returns403() throws Exception {
        when(clientService.getClientById(TEST_UID, WS_ID, "cl-1"))
                .thenThrow(new WorkspaceAccessDeniedException("You do not have permission to access workspace"));

        mockMvc.perform(get("/api/v1/workspaces/" + WS_ID + "/clients/cl-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.status").value(403));
    }

    // ── UPDATE CLIENT (PUT /{clientId}) ──────────────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/workspaces/{id}/clients/{clientId} with valid payload → 200 OK")
    void updateClient_valid_returns200() throws Exception {
        ClientUpdateRequest request = new ClientUpdateRequest(
                "Updated Name", "Updated Co", "updated@co.com", "555", "LEAD", "Updated notes"
        );
        ClientResponse response = new ClientResponse(
                "cl-1", WS_ID, "Updated Name", "Updated Co", "updated@co.com", "555", "LEAD", "Updated notes",
                Instant.now(), Instant.now(), "orig-creator", TEST_UID
        );

        when(clientService.updateClient(eq(TEST_UID), eq(WS_ID), eq("cl-1"), any(ClientUpdateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/workspaces/" + WS_ID + "/clients/cl-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.name").value("Updated Name"))
               .andExpect(jsonPath("$.status").value("LEAD"))
               .andExpect(jsonPath("$.updatedBy").value(TEST_UID));
    }

    @Test
    @DisplayName("PUT /api/v1/workspaces/{id}/clients/{clientId} when not found → 404 Not Found")
    void updateClient_notFound_returns404() throws Exception {
        ClientUpdateRequest request = new ClientUpdateRequest("Name", null, null, null, null, null);
        when(clientService.updateClient(eq(TEST_UID), eq(WS_ID), eq("missing-id"), any()))
                .thenThrow(new ResourceNotFoundException("Client not found with id: missing-id"));

        mockMvc.perform(put("/api/v1/workspaces/" + WS_ID + "/clients/missing-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.status").value(404));
    }

    // ── DELETE CLIENT (DELETE /{clientId}) ───────────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/workspaces/{id}/clients/{clientId} → 204 No Content")
    void deleteClient_valid_returns204() throws Exception {
        doNothing().when(clientService).deleteClient(TEST_UID, WS_ID, "cl-to-delete");

        mockMvc.perform(delete("/api/v1/workspaces/" + WS_ID + "/clients/cl-to-delete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
               .andExpect(status().isNoContent());

        verify(clientService).deleteClient(TEST_UID, WS_ID, "cl-to-delete");
    }

    @Test
    @DisplayName("DELETE /api/v1/workspaces/{id}/clients/{clientId} without token → 401 Unauthorized")
    void deleteClient_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/workspaces/" + WS_ID + "/clients/cl-1"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/v1/workspaces/{id}/clients/{clientId} when user is MEMBER role → 403 Forbidden")
    void deleteClient_memberRole_returns403() throws Exception {
        doThrow(new WorkspaceAccessDeniedException("Insufficient permissions: Member role cannot perform write operations"))
                .when(clientService).deleteClient(TEST_UID, WS_ID, "cl-1");

        mockMvc.perform(delete("/api/v1/workspaces/" + WS_ID + "/clients/cl-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.status").value(403));
    }
}

