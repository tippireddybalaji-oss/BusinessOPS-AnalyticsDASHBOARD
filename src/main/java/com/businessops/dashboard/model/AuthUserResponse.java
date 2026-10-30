package com.businessops.dashboard.model;

/**
 * Response body for {@code GET /api/auth/me}.
 *
 * <p>Contains only the information that is safe to return to the client:
 * <ul>
 *   <li>{@code uid}   – the Firebase UID extracted from the verified ID token
 *       (never from a client-supplied value).</li>
 *   <li>{@code email} – the email associated with the Firebase account, or
 *       {@code null} if the provider does not supply one.</li>
 * </ul>
 *
 * <p>Raw token data, private keys, and internal identifiers are <em>never</em>
 * included in this response.
 */
public record AuthUserResponse(String uid, String email) {
}
