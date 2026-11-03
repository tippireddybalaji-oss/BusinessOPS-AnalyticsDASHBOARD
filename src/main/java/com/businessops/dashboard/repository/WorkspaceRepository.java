package com.businessops.dashboard.repository;

import com.businessops.dashboard.model.Workspace;
import com.businessops.dashboard.model.WorkspaceMember;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing {@link Workspace} and {@link WorkspaceMember}
 * entities in Cloud Firestore.
 */
public interface WorkspaceRepository {

    /**
     * Atomically creates a workspace document and its initial owner membership document.
     *
     * @param workspace the workspace entity to create
     * @param ownerMember the initial owner membership entity
     * @return the created workspace with generated ID and timestamps
     */
    Workspace createWorkspace(Workspace workspace, WorkspaceMember ownerMember);

    /**
     * Retrieves a workspace document by its ID.
     *
     * @param workspaceId the workspace document ID
     * @return Optional containing the workspace if found, or empty Optional
     */
    Optional<Workspace> findById(String workspaceId);

    /**
     * Retrieves a member document from the {@code workspaces/{workspaceId}/members/{uid}} subcollection.
     *
     * @param workspaceId the workspace document ID
     * @param uid the Firebase user UID
     * @return Optional containing the membership if found, or empty Optional
     */
    Optional<WorkspaceMember> findMember(String workspaceId, String uid);

    /**
     * Retrieves all workspaces accessible to the user (either owned or as an active member).
     *
     * @param uid the Firebase user UID
     * @return list of accessible workspaces, or empty list
     */
    List<Workspace> findWorkspacesByMemberUid(String uid);

    /**
     * Checks whether a user is an active member or owner of a workspace.
     *
     * @param workspaceId the workspace document ID
     * @param uid the Firebase user UID
     * @return true if the user is a member or owner, false otherwise
     */
    boolean isMember(String workspaceId, String uid);
}
