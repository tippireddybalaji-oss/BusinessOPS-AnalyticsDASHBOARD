package com.businessops.dashboard.controller;

import com.businessops.dashboard.dto.ClientCreateRequest;
import com.businessops.dashboard.dto.ClientResponse;
import com.businessops.dashboard.dto.ClientUpdateRequest;
import com.businessops.dashboard.dto.PagedResponse;
import com.businessops.dashboard.security.SecurityUtils;
import com.businessops.dashboard.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Client Management within a specific workspace.
 *
 * <p>Base path: {@code /api/v1/workspaces/{workspaceId}/clients}
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/clients")
public class ClientController {

    private final ClientService clientService;
    private final SecurityUtils securityUtils;

    public ClientController(ClientService clientService, SecurityUtils securityUtils) {
        this.clientService = clientService;
        this.securityUtils = securityUtils;
    }

    /**
     * Creates a new client in the specified workspace.
     *
     * @param workspaceId target workspace ID
     * @param request validated client payload
     * @return 201 Created with client response
     */
    @PostMapping
    public ResponseEntity<ClientResponse> createClient(
            @PathVariable("workspaceId") String workspaceId,
            @Valid @RequestBody ClientCreateRequest request) {
        String uid = securityUtils.getAuthenticatedUid();
        ClientResponse response = clientService.createClient(uid, workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a paginated list of clients with optional search and status filtering.
     *
     * @param workspaceId target workspace ID
     * @param search optional name/company substring filter
     * @param status optional status filter (ACTIVE, INACTIVE, LEAD)
     * @param page zero-based page number (default: 0)
     * @param size page size (default: 20, max: 100)
     * @return 200 OK with paginated client response
     */
    @GetMapping
    public ResponseEntity<PagedResponse<ClientResponse>> listClients(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        String uid = securityUtils.getAuthenticatedUid();
        PagedResponse<ClientResponse> response = clientService.listClients(uid, workspaceId, search, status, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves client details by ID.
     *
     * @param workspaceId target workspace ID
     * @param clientId target client document ID
     * @return 200 OK with client details
     */
    @GetMapping("/{clientId}")
    public ResponseEntity<ClientResponse> getClient(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("clientId") String clientId) {
        String uid = securityUtils.getAuthenticatedUid();
        ClientResponse response = clientService.getClientById(uid, workspaceId, clientId);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing client.
     *
     * @param workspaceId target workspace ID
     * @param clientId target client document ID
     * @param request validated updated client payload
     * @return 200 OK with updated client details
     */
    @PutMapping("/{clientId}")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("clientId") String clientId,
            @Valid @RequestBody ClientUpdateRequest request) {
        String uid = securityUtils.getAuthenticatedUid();
        ClientResponse response = clientService.updateClient(uid, workspaceId, clientId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a client from the workspace.
     *
     * @param workspaceId target workspace ID
     * @param clientId target client document ID
     * @return 204 No Content
     */
    @DeleteMapping("/{clientId}")
    public ResponseEntity<Void> deleteClient(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("clientId") String clientId) {
        String uid = securityUtils.getAuthenticatedUid();
        clientService.deleteClient(uid, workspaceId, clientId);
        return ResponseEntity.noContent().build();
    }
}
