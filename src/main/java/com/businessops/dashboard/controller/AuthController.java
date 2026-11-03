package com.businessops.dashboard.controller;

import com.businessops.dashboard.model.AuthUserResponse;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication and identity verification endpoint.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/me} (Standardized API v1 identity endpoint)</li>
 *   <li>{@code GET /api/auth/me} (Preserved for compatibility)</li>
 * </ul>
 *
 * <p>Both routes require a valid Firebase ID token supplied as a
 * {@code Authorization: Bearer <token>} header. The token is verified server-side
 * by {@link com.businessops.dashboard.security.FirebaseAuthFilter} before this controller is reached.
 *
 * <p><strong>Security invariant:</strong> The UID returned here is derived
 * exclusively from the server-verified {@link FirebaseToken}, never from any
 * client-supplied query parameter, header, or body.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    /**
     * Returns the verified identity of the authenticated caller.
     *
     * <p>Requires: {@code Authorization: Bearer <Firebase ID token>}
     *
     * <p>Example response (200 OK):
     * <pre>{@code
     * {
     *   "uid": "abc123firebaseUid",
     *   "email": "user@example.com"
     * }
     * }</pre>
     *
     * @return 200 with the caller's Firebase UID and email (email may be null)
     */
    @GetMapping({"/v1/me", "/auth/me"})
    public ResponseEntity<AuthUserResponse> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // The principal is a FirebaseToken set by FirebaseAuthFilter
        FirebaseToken token = (FirebaseToken) authentication.getPrincipal();

        // Extract UID and email strictly from the server-verified token
        String uid = token.getUid();
        String email = token.getEmail();

        return ResponseEntity.ok(new AuthUserResponse(uid, email));
    }
}
