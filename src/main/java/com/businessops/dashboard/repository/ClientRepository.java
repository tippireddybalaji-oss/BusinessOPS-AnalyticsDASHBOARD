package com.businessops.dashboard.repository;

import com.businessops.dashboard.model.Client;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing {@link Client} entities in Cloud Firestore.
 *
 * <p>All operations are scoped to a parent workspace: {@code workspaces/{workspaceId}/clients/{clientId}}.
 */
public interface ClientRepository {

    /**
     * Persists a new client document under the specified workspace.
     *
     * @param workspaceId target workspace ID
     * @param client client entity to persist
     * @return the created client with generated ID and timestamps
     */
    Client createClient(String workspaceId, Client client);

    /**
     * Retrieves a client by workspace and client ID.
     *
     * @param workspaceId target workspace ID
     * @param clientId target client ID
     * @return Optional containing the client if found, or empty Optional
     */
    Optional<Client> findById(String workspaceId, String clientId);

    /**
     * Retrieves clients for a workspace with optional search query and status filter.
     *
     * @param workspaceId target workspace ID
     * @param search optional search string matching client name or company
     * @param status optional status filter (e.g. ACTIVE, INACTIVE, LEAD)
     * @return list of matching clients
     */
    List<Client> findClients(String workspaceId, String search, String status);

    /**
     * Updates an existing client document.
     *
     * @param workspaceId target workspace ID
     * @param client client entity with updated fields
     * @return the updated client
     */
    Client updateClient(String workspaceId, Client client);

    /**
     * Deletes a client document.
     *
     * @param workspaceId target workspace ID
     * @param clientId client document ID
     * @return true if deleted
     */
    boolean deleteClient(String workspaceId, String clientId);
}

