package com.businessops.dashboard.dto;

import com.businessops.dashboard.model.Workspace;
import com.businessops.dashboard.model.WorkspaceRole;

import java.time.Instant;

/**
 * Standardized response contract for workspace information.
 */
public record WorkspaceResponse(
        String id,
        String name,
        String ownerUid,
        String defaultCurrency,
        Instant createdAt,
        Instant updatedAt,
        WorkspaceRole role
) {
    public static WorkspaceResponse from(Workspace workspace, WorkspaceRole role) {
        Instant createdInstant = workspace.getCreatedAt() != null
                ? workspace.getCreatedAt().toDate().toInstant()
                : Instant.now();
        Instant updatedInstant = workspace.getUpdatedAt() != null
                ? workspace.getUpdatedAt().toDate().toInstant()
                : Instant.now();

        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getOwnerUid(),
                workspace.getDefaultCurrency(),
                createdInstant,
                updatedInstant,
                role
        );
    }
}
