package com.businessops.dashboard.repository;

import com.businessops.dashboard.exception.DatabaseOperationException;
import com.businessops.dashboard.model.Client;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Cloud Firestore implementation of {@link ClientRepository}.
 *
 * <p>All documents reside strictly at {@code workspaces/{workspaceId}/clients/{clientId}}.
 */
@Repository
public class FirestoreClientRepository implements ClientRepository {

    private static final Logger log = LoggerFactory.getLogger(FirestoreClientRepository.class);

    private static final String WORKSPACES_COLLECTION = "workspaces";
    private static final String CLIENTS_SUBCOLLECTION = "clients";

    private final Firestore firestore;

    public FirestoreClientRepository(@Autowired(required = false) Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public Client createClient(String workspaceId, Client client) {
        checkFirestoreAvailable();

        try {
            CollectionReference clientsRef = getClientsCollection(workspaceId);
            DocumentReference docRef = (client.getId() != null && !client.getId().isBlank())
                    ? clientsRef.document(client.getId())
                    : clientsRef.document();

            String clientId = docRef.getId();
            client.setId(clientId);
            client.setWorkspaceId(workspaceId);

            Timestamp now = Timestamp.now();
            client.setCreatedAt(now);
            client.setUpdatedAt(now);

            Map<String, Object> data = new HashMap<>();
            data.put("name", client.getName());
            data.put("company", client.getCompany());
            data.put("email", client.getEmail());
            data.put("phone", client.getPhone());
            data.put("status", client.getStatus());
            data.put("notes", client.getNotes());
            data.put("createdAt", now);
            data.put("updatedAt", now);
            data.put("createdBy", client.getCreatedBy());
            data.put("updatedBy", client.getUpdatedBy());

            docRef.set(data).get();
            log.info("Client created: id={} in workspace={}", clientId, workspaceId);

            return client;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while creating client", e);
        } catch (ExecutionException e) {
            log.error("Failed to create client in Firestore for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to persist client: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Client> findById(String workspaceId, String clientId) {
        checkFirestoreAvailable();

        try {
            DocumentSnapshot snap = getClientsCollection(workspaceId)
                    .document(clientId)
                    .get()
                    .get();

            if (!snap.exists()) {
                return Optional.empty();
            }

            return Optional.ofNullable(mapDocumentToClient(snap, workspaceId));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while fetching client", e);
        } catch (ExecutionException e) {
            log.error("Failed to fetch client {} from workspace {}: {}", clientId, workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to retrieve client: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Client> findClients(String workspaceId, String search, String status) {
        checkFirestoreAvailable();

        try {
            Query query = getClientsCollection(workspaceId);

            if (status != null && !status.isBlank()) {
                query = query.whereEqualTo("status", status.trim().toUpperCase());
            }

            QuerySnapshot querySnapshot = query.get().get();
            List<Client> clients = new ArrayList<>();

            String searchLower = (search != null && !search.isBlank()) ? search.trim().toLowerCase() : null;

            for (QueryDocumentSnapshot doc : querySnapshot.getDocuments()) {
                Client client = mapDocumentToClient(doc, workspaceId);
                if (client != null) {
                    if (searchLower != null) {
                        boolean matchName = client.getName() != null && client.getName().toLowerCase().contains(searchLower);
                        boolean matchCompany = client.getCompany() != null && client.getCompany().toLowerCase().contains(searchLower);
                        if (!matchName && !matchCompany) {
                            continue; // Skip if search term does not match name or company
                        }
                    }
                    clients.add(client);
                }
            }

            // Sort by createdAt descending (newest first)
            clients.sort((a, b) -> {
                if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            });

            return clients;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while querying clients", e);
        } catch (ExecutionException e) {
            log.error("Failed to list clients for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to query clients: " + e.getMessage(), e);
        }
    }

    @Override
    public Client updateClient(String workspaceId, Client client) {
        checkFirestoreAvailable();

        try {
            DocumentReference docRef = getClientsCollection(workspaceId).document(client.getId());

            Timestamp now = Timestamp.now();
            client.setUpdatedAt(now);

            Map<String, Object> updates = new HashMap<>();
            updates.put("name", client.getName());
            updates.put("company", client.getCompany());
            updates.put("email", client.getEmail());
            updates.put("phone", client.getPhone());
            updates.put("status", client.getStatus());
            updates.put("notes", client.getNotes());
            updates.put("updatedAt", now);
            updates.put("updatedBy", client.getUpdatedBy());

            docRef.update(updates).get();
            log.info("Client updated: id={} in workspace={}", client.getId(), workspaceId);

            return client;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while updating client", e);
        } catch (ExecutionException e) {
            log.error("Failed to update client {} in workspace {}: {}", client.getId(), workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to update client: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteClient(String workspaceId, String clientId) {
        checkFirestoreAvailable();

        try {
            getClientsCollection(workspaceId).document(clientId).delete().get();
            log.info("Client deleted: id={} in workspace={}", clientId, workspaceId);
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while deleting client", e);
        } catch (ExecutionException e) {
            log.error("Failed to delete client {} from workspace {}: {}", clientId, workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to delete client: " + e.getMessage(), e);
        }
    }

    private CollectionReference getClientsCollection(String workspaceId) {
        return firestore.collection(WORKSPACES_COLLECTION)
                .document(workspaceId)
                .collection(CLIENTS_SUBCOLLECTION);
    }

    private void checkFirestoreAvailable() {
        if (firestore == null) {
            throw new DatabaseOperationException("Firestore client is unconfigured. Set GOOGLE_APPLICATION_CREDENTIALS.");
        }
    }

    private Client mapDocumentToClient(DocumentSnapshot doc, String workspaceId) {
        if (doc == null || !doc.exists()) {
            return null;
        }
        Client client = new Client();
        client.setId(doc.getId());
        client.setWorkspaceId(workspaceId);
        client.setName(doc.getString("name"));
        client.setCompany(doc.getString("company"));
        client.setEmail(doc.getString("email"));
        client.setPhone(doc.getString("phone"));
        client.setStatus(doc.getString("status"));
        client.setNotes(doc.getString("notes"));
        client.setCreatedAt(doc.getTimestamp("createdAt"));
        client.setUpdatedAt(doc.getTimestamp("updatedAt"));
        client.setCreatedBy(doc.getString("createdBy"));
        client.setUpdatedBy(doc.getString("updatedBy"));
        return client;
    }
}
