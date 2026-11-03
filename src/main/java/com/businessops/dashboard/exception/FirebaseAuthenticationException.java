package com.businessops.dashboard.exception;

/**
 * Thrown by the Firebase authentication filter when a Bearer token is absent,
 * malformed, expired, or otherwise cannot be verified by Firebase Auth.
 *
 * <p>The Spring Security framework converts this into an HTTP 401 Unauthorized
 * response via the configured {@link org.springframework.security.web.AuthenticationEntryPoint}.
 */
public class FirebaseAuthenticationException extends RuntimeException {

    public FirebaseAuthenticationException(String message) {
        super(message);
    }

    public FirebaseAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}

