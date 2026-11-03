package com.businessops.dashboard.model;

/**
 * Roles for workspace membership and access control.
 *
 * <ul>
 *   <li>{@link #OWNER}: Created automatically when a user creates a workspace. Full administrative rights.</li>
 *   <li>{@link #ADMIN}: Administrative member with management privileges.</li>
 *   <li>{@link #MEMBER}: Standard member with read and operational write privileges.</li>
 * </ul>
 */
public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER;

    public static WorkspaceRole fromString(String role) {
        if (role == null || role.isBlank()) {
            return MEMBER;
        }
        try {
            return WorkspaceRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEMBER;
        }
    }
}
