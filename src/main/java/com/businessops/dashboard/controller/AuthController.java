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
 * Authentication verification endpoint.
 *
 * <p>All routes under {@code /api/auth/**} require a valid Firebase ID token
 * supplied as a {@code Authorization: Bearer <token>} header.  The token is
 * verified server-side by {@link com.businessops.dashboard.security.FirebaseAuthFilter}
 * before this controller is reached.
 *
 * <p><strong>Security invariant:</strong> the UID returned here is read
 * exclusively from the server-verified {@link FirebaseToken}, never from any
 * client-supplied request parameter or body.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Returns the verified identity of the authenticated caller.
     *
     * <p>Requires: {@code Authorization: Bearer <Firebase ID token>}
     *
     * <p>Example response (200 OK):
     * <pre>{@code
     * {
     *   "uid":   "abc123firebaseUid",
     *   "email": "user@example.com"
     * }
     * }</pre>
     *
     * <p>If no token is supplied or the token is invalid, the security filter
     * returns a 401 before this method is called.
     *
     * @return 200 with the caller's Firebase UID and email (email may be null)
     */
    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // The principal is a FirebaseToken set by FirebaseAuthFilter
        FirebaseToken token = (FirebaseToken) authentication.getPrincipal();

        // Extract UID and email from the server-verified token only
        String uid   = token.getUid();
        String email = token.getEmail();   // may be null for some auth providers

        return ResponseEntity.ok(new AuthUserResponse(uid, email));
    }
}
