package com.businessops.dashboard.exception;

/**
 * Thrown when an authenticated user attempts to access or modify a workspace
 * to which they do not belong, or where their role has insufficient permissions.
 *
 * <p>Maps to HTTP 403 FORBIDDEN.
 */
public class WorkspaceAccessDeniedException extends RuntimeException {

    public WorkspaceAccessDeniedException(String message) {
        super(message);
    }
}
