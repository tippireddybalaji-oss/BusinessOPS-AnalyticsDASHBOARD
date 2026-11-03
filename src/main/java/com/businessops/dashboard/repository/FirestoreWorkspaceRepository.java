package com.businessops.dashboard.repository;

import com.businessops.dashboard.exception.DatabaseOperationException;
import com.businessops.dashboard.model.Workspace;
import com.businessops.dashboard.model.WorkspaceMember;
import com.businessops.dashboard.model.WorkspaceRole;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Cloud Firestore implementation of {@link WorkspaceRepository}.
 *
 * <p>Operates on:
 * <ul>
 *   <li>{@code workspaces/{workspaceId}}</li>
 *   <li>{@code workspaces/{workspaceId}/members/{uid}}</li>
 * </ul>
 */
@Repository
public class FirestoreWorkspaceRepository implements WorkspaceRepository {

    private static final Logger log = LoggerFactory.getLogger(FirestoreWorkspaceRepository.class);

    private static final String WORKSPACES_COLLECTION = "workspaces";
    private static final String MEMBERS_SUBCOLLECTION = "members";

    private final Firestore firestore;

    public FirestoreWorkspaceRepository(@Autowired(required = false) Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public Workspace createWorkspace(Workspace workspace, WorkspaceMember ownerMember) {
        checkFirestoreAvailable();

        try {
            CollectionReference workspacesRef = firestore.collection(WORKSPACES_COLLECTION);
            String workspaceId = (workspace.getId() != null && !workspace.getId().isBlank())
                    ? workspace.getId()
                    : workspacesRef.document().getId();

            workspace.setId(workspaceId);

            DocumentReference workspaceDocRef = workspacesRef.document(workspaceId);
            DocumentReference memberDocRef = workspaceDocRef.collection(MEMBERS_SUBCOLLECTION).document(ownerMember.getUid());

            Timestamp now = Timestamp.now();
            workspace.setCreatedAt(now);
            workspace.setUpdatedAt(now);
            ownerMember.setJoinedAt(now);

            Map<String, Object> workspaceData = new HashMap<>();
            workspaceData.put("name", workspace.getName());
            workspaceData.put("ownerUid", workspace.getOwnerUid());
            workspaceData.put("defaultCurrency", workspace.getDefaultCurrency());
            workspaceData.put("createdAt", now);
            workspaceData.put("updatedAt", now);

            Map<String, Object> memberData = new HashMap<>();
            memberData.put("uid", ownerMember.getUid());
            memberData.put("role", ownerMember.getRole().name());
            memberData.put("joinedAt", now);

            // Execute atomic batch write to ensure workspace and initial owner membership exist together
            WriteBatch batch = firestore.batch();
            batch.set(workspaceDocRef, workspaceData);
            batch.set(memberDocRef, memberData);
            batch.commit().get();

            log.info("Created workspace id={} for ownerUid={}", workspaceId, workspace.getOwnerUid());
            return workspace;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while creating workspace", e);
        } catch (ExecutionException e) {
            log.error("Failed to create workspace in Firestore: {}", e.getMessage(), e);
            throw new DatabaseOperationException("Failed to persist workspace in database: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Workspace> findById(String workspaceId) {
        checkFirestoreAvailable();

        try {
            DocumentSnapshot snap = firestore.collection(WORKSPACES_COLLECTION)
                    .document(workspaceId)
                    .get()
                    .get();

            if (!snap.exists()) {
                return Optional.empty();
            }

            return Optional.ofNullable(mapDocumentToWorkspace(snap));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while fetching workspace", e);
        } catch (ExecutionException e) {
            log.error("Failed to fetch workspace {} from Firestore: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to retrieve workspace: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<WorkspaceMember> findMember(String workspaceId, String uid) {
        checkFirestoreAvailable();

        try {
            DocumentSnapshot snap = firestore.collection(WORKSPACES_COLLECTION)
                    .document(workspaceId)
                    .collection(MEMBERS_SUBCOLLECTION)
                    .document(uid)
                    .get()
                    .get();

            if (!snap.exists()) {
                return Optional.empty();
            }

            return Optional.ofNullable(mapDocumentToMember(snap));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while fetching workspace member", e);
        } catch (ExecutionException e) {
            log.error("Failed to fetch member {} for workspace {} from Firestore: {}", uid, workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to retrieve workspace member: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Workspace> findWorkspacesByMemberUid(String uid) {
        checkFirestoreAvailable();

        Map<String, Workspace> workspacesMap = new LinkedHashMap<>();

        try {
            // 1. Fetch workspaces owned by the user
            ApiFuture<QuerySnapshot> ownedFuture = firestore.collection(WORKSPACES_COLLECTION)
                    .whereEqualTo("ownerUid", uid)
                    .get();

            for (QueryDocumentSnapshot doc : ownedFuture.get().getDocuments()) {
                Workspace ws = mapDocumentToWorkspace(doc);
                if (ws != null) {
                    workspacesMap.put(ws.getId(), ws);
                }
            }

            // 2. Fetch workspaces where user is a member via Collection Group query
            try {
                ApiFuture<QuerySnapshot> membersFuture = firestore.collectionGroup(MEMBERS_SUBCOLLECTION)
                        .whereEqualTo("uid", uid)
                        .get();

                for (QueryDocumentSnapshot memberDoc : membersFuture.get().getDocuments()) {
                    DocumentReference parentWorkspaceRef = memberDoc.getReference().getParent().getParent();
                    if (parentWorkspaceRef != null && !workspacesMap.containsKey(parentWorkspaceRef.getId())) {
                        DocumentSnapshot wsSnap = parentWorkspaceRef.get().get();
                        Workspace ws = mapDocumentToWorkspace(wsSnap);
                        if (ws != null) {
                            workspacesMap.put(ws.getId(), ws);
                        }
                    }
                }
            } catch (Exception e) {
                // If collectionGroup query requires an index or fails, log warning; owned workspaces are already collected
                log.warn("CollectionGroup members query encountered an exception (check Firestore index): {}", e.getMessage());
            }

            return new ArrayList<>(workspacesMap.values());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while listing workspaces", e);
        } catch (ExecutionException e) {
            log.error("Failed to list workspaces for user {}: {}", uid, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to list workspaces: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isMember(String workspaceId, String uid) {
        return findMember(workspaceId, uid).isPresent();
    }

    private void checkFirestoreAvailable() {
        if (firestore == null) {
            throw new DatabaseOperationException("Firestore client is unconfigured. Set GOOGLE_APPLICATION_CREDENTIALS.");
        }
    }

    private Workspace mapDocumentToWorkspace(DocumentSnapshot snap) {
        if (snap == null || !snap.exists()) {
            return null;
        }
        Workspace ws = new Workspace();
        ws.setId(snap.getId());
        ws.setName(snap.getString("name"));
        ws.setOwnerUid(snap.getString("ownerUid"));
        ws.setDefaultCurrency(snap.getString("defaultCurrency"));
        ws.setCreatedAt(snap.getTimestamp("createdAt"));
        ws.setUpdatedAt(snap.getTimestamp("updatedAt"));
        return ws;
    }

    private WorkspaceMember mapDocumentToMember(DocumentSnapshot snap) {
        if (snap == null || !snap.exists()) {
            return null;
        }
        WorkspaceMember member = new WorkspaceMember();
        member.setUid(snap.getString("uid") != null ? snap.getString("uid") : snap.getId());
        member.setRole(WorkspaceRole.fromString(snap.getString("role")));
        member.setJoinedAt(snap.getTimestamp("joinedAt"));
        return member;
    }
}
