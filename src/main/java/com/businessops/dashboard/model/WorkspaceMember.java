package com.businessops.dashboard.model;

import com.google.cloud.Timestamp;

import java.util.Objects;

/**
 * Domain entity representing a member of a workspace.
 *
 * <p>Stored at path: {@code workspaces/{workspaceId}/members/{uid}}
 *
 * <h2>Data Fields</h2>
 * <ul>
 *   <li>{@code uid}: Firebase UID of the member</li>
 *   <li>{@code role}: Role within the workspace (OWNER, ADMIN, MEMBER)</li>
 *   <li>{@code joinedAt}: Server timestamp when the user became a member</li>
 * </ul>
 */
public class WorkspaceMember {

    private String uid;
    private WorkspaceRole role;
    private Timestamp joinedAt;

    public WorkspaceMember() {
    }

    public WorkspaceMember(String uid, WorkspaceRole role, Timestamp joinedAt) {
        this.uid = uid;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public WorkspaceRole getRole() {
        return role;
    }

    public void setRole(WorkspaceRole role) {
        this.role = role;
    }

    public Timestamp getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Timestamp joinedAt) {
        this.joinedAt = joinedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkspaceMember that)) return false;
        return Objects.equals(uid, that.uid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uid);
    }

    @Override
    public String toString() {
        return "WorkspaceMember{" +
                "uid='" + uid + '\'' +
                ", role=" + role +
                ", joinedAt=" + joinedAt +
                '}';
    }
}
