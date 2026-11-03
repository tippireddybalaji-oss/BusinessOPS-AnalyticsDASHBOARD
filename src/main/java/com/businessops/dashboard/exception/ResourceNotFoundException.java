package com.businessops.dashboard.exception;

/**
 * Thrown when a requested resource (e.g. Workspace) does not exist in Firestore.
 *
 * <p>Maps to HTTP 404 NOT_FOUND.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
