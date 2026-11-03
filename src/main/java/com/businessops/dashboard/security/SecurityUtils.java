package com.businessops.dashboard.security;

import com.businessops.dashboard.exception.FirebaseAuthenticationException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Helper component for retrieving authenticated user details from the
 * Spring Security context.
 *
 * <p><strong>Security invariant:</strong> UID and token claims are derived
 * strictly from the verified {@link FirebaseToken} placed in the context by
 * {@link FirebaseAuthFilter}. Client-supplied query, header, or body UIDs
 * are never trusted.
 */
@Component
public class SecurityUtils {

    /**
     * Retrieves the verified Firebase UID of the currently authenticated user.
     *
     * @return the verified UID
     * @throws FirebaseAuthenticationException if the request is unauthenticated
     */
    public String getAuthenticatedUid() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof FirebaseToken token)) {
            throw new FirebaseAuthenticationException("Unauthenticated request. Valid Firebase ID token required.");
        }
        return token.getUid();
    }

    /**
     * Retrieves the verified {@link FirebaseToken} of the currently authenticated user.
     *
     * @return the verified token
     * @throws FirebaseAuthenticationException if the request is unauthenticated
     */
    public FirebaseToken getAuthenticatedToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof FirebaseToken token)) {
            throw new FirebaseAuthenticationException("Unauthenticated request. Valid Firebase ID token required.");
        }
        return token;
    }
}
