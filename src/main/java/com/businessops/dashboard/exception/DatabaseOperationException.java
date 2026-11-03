package com.businessops.dashboard.exception;

/**
 * Thrown when an underlying Cloud Firestore operation fails unexpectedly.
 *
 * <p>Maps to HTTP 500 INTERNAL_SERVER_ERROR via {@link GlobalExceptionHandler}.
 */
public class DatabaseOperationException extends RuntimeException {

    public DatabaseOperationException(String message) {
        super(message);
    }

    public DatabaseOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
