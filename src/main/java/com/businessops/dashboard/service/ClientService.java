package com.businessops.dashboard.service;

import com.businessops.dashboard.dto.ClientCreateRequest;
import com.businessops.dashboard.dto.ClientResponse;
import com.businessops.dashboard.dto.ClientUpdateRequest;
import com.businessops.dashboard.dto.PagedResponse;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.model.Client;
import com.businessops.dashboard.model.ClientStatus;
import com.businessops.dashboard.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service orchestrating client management, validation, and multi-tenant authorization.
 *
 * <p><strong>Authorization Matrix:</strong>
 * <ul>
 *   <li>Read operations (list, get): {@code OWNER}, {@code ADMIN}, {@code MEMBER}.</li>
 *   <li>Write operations (create, update, delete): {@code OWNER}, {@code ADMIN}.</li>
 *   <li>Non-members: rejected with HTTP 403 Forbidden.</li>
 * </ul>
 */
@Service
public class ClientService {

    private static final Logger log = LoggerFactory.getLogger(ClientService.class);

    private final WorkspaceService workspaceService;
    private final ClientRepository clientRepository;

    public ClientService(WorkspaceService workspaceService, ClientRepository clientRepository) {
        this.workspaceService = workspaceService;
        this.clientRepository = clientRepository;
    }

    /**
     * Creates a new client in the specified workspace.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param request client creation parameters
     * @return created client response
     */
    public ClientResponse createClient(String authenticatedUid, String workspaceId, ClientCreateRequest request) {
        // Enforce write access (OWNER or ADMIN required; MEMBER rejected with 403)
        workspaceService.verifyWorkspaceWriteAccess(authenticatedUid, workspaceId);

        String normalizedStatus = ClientStatus.fromString(request.status()).name();

        Client client = new Client();
        client.setWorkspaceId(workspaceId);
        client.setName(request.name().trim());
        client.setCompany(request.company() != null ? request.company().trim() : null);
        client.setEmail(request.email() != null ? request.email().trim().toLowerCase() : null);
        client.setPhone(request.phone() != null ? request.phone().trim() : null);
        client.setStatus(normalizedStatus);
        client.setNotes(request.notes() != null ? request.notes().trim() : null);
        client.setCreatedBy(authenticatedUid);
        client.setUpdatedBy(authenticatedUid);

        Client created = clientRepository.createClient(workspaceId, client);
        log.info("Client created: id='{}' in workspace='{}' by user='{}'", created.getId(), workspaceId, authenticatedUid);

        return ClientResponse.from(created);
    }

    /**
     * Retrieves client details by ID.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param clientId client document ID
     * @return client response
     */
    public ClientResponse getClientById(String authenticatedUid, String workspaceId, String clientId) {
        // Enforce read access (OWNER, ADMIN, or MEMBER allowed)
        workspaceService.verifyWorkspaceAccess(authenticatedUid, workspaceId);

        Client client = clientRepository.findById(workspaceId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found with id: " + clientId + " in workspace: " + workspaceId
                ));

        return ClientResponse.from(client);
    }

    /**
     * Lists clients in the workspace with pagination, search, and status filtering.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param search optional search query matching name or company
     * @param status optional status filter (ACTIVE, INACTIVE, LEAD)
     * @param page zero-based page index
     * @param size page size (1–100)
     * @return paginated client response
     */
    public PagedResponse<ClientResponse> listClients(String authenticatedUid, String workspaceId,
                                                    String search, String status, int page, int size) {
        // Enforce read access (OWNER, ADMIN, or MEMBER allowed)
        workspaceService.verifyWorkspaceAccess(authenticatedUid, workspaceId);

        if (status != null && !status.isBlank()) {
            ClientStatus.fromString(status); // Validate status value; throws IllegalArgumentException if invalid
        }

        List<Client> clients = clientRepository.findClients(workspaceId, search, status);
        List<ClientResponse> responses = clients.stream()
                .map(ClientResponse::from)
                .toList();

        return PagedResponse.of(responses, page, size);
    }

    /**
     * Updates an existing client document.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param clientId target client ID
     * @param request updated client parameters
     * @return updated client response
     */
    public ClientResponse updateClient(String authenticatedUid, String workspaceId,
                                      String clientId, ClientUpdateRequest request) {
        // Enforce write access (OWNER or ADMIN required)
        workspaceService.verifyWorkspaceWriteAccess(authenticatedUid, workspaceId);

        String normalizedStatus = ClientStatus.fromString(request.status()).name();

        Client existing = clientRepository.findById(workspaceId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found with id: " + clientId + " in workspace: " + workspaceId
                ));

        existing.setName(request.name().trim());
        existing.setCompany(request.company() != null ? request.company().trim() : null);
        existing.setEmail(request.email() != null ? request.email().trim().toLowerCase() : null);
        existing.setPhone(request.phone() != null ? request.phone().trim() : null);
        existing.setStatus(normalizedStatus);
        existing.setNotes(request.notes() != null ? request.notes().trim() : null);
        existing.setUpdatedBy(authenticatedUid);
        // Note: createdAt and createdBy are strictly preserved

        Client updated = clientRepository.updateClient(workspaceId, existing);
        log.info("Client updated: id='{}' in workspace='{}' by user='{}'", clientId, workspaceId, authenticatedUid);

        return ClientResponse.from(updated);
    }

    /**
     * Deletes a client document from the workspace.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param clientId target client ID
     */
    public void deleteClient(String authenticatedUid, String workspaceId, String clientId) {
        // Enforce write access (OWNER or ADMIN required)
        workspaceService.verifyWorkspaceWriteAccess(authenticatedUid, workspaceId);

        clientRepository.findById(workspaceId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found with id: " + clientId + " in workspace: " + workspaceId
                ));

        clientRepository.deleteClient(workspaceId, clientId);
        log.info("Client deleted: id='{}' from workspace='{}' by user='{}'", clientId, workspaceId, authenticatedUid);
    }
}

