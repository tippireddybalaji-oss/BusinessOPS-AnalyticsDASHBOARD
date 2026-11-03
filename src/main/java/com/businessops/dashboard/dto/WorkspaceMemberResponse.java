package com.businessops.dashboard.dto;

import com.businessops.dashboard.model.WorkspaceMember;
import com.businessops.dashboard.model.WorkspaceRole;

import java.time.Instant;

/**
 * Standardized response contract for workspace membership information.
 */
public record WorkspaceMemberResponse(
        String uid,
        WorkspaceRole role,
        Instant joinedAt
) {
    public static WorkspaceMemberResponse from(WorkspaceMember member) {
        Instant joinedInstant = member.getJoinedAt() != null
                ? member.getJoinedAt().toDate().toInstant()
                : Instant.now();

        return new WorkspaceMemberResponse(
                member.getUid(),
                member.getRole(),
                joinedInstant
        );
    }
}
