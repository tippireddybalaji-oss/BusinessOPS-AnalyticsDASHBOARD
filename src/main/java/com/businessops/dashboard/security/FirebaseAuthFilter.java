package com.businessops.dashboard.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Servlet filter that extracts and verifies a Firebase ID token from every
 * incoming HTTP request.
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li>Read the {@code Authorization} header.</li>
 *   <li>If the header is absent or does not start with {@code "Bearer "}, pass
 *       the request through unauthenticated (Spring Security will later enforce
 *       access rules — public endpoints are unaffected).</li>
 *   <li>Extract the raw ID token string.</li>
 *   <li>Call {@link FirebaseAuth#verifyIdToken(String)} — this validates the
 *       signature, issuer, audience, and expiry server-side.</li>
 *   <li>Extract the {@code uid} exclusively from the verified token.  The UID
 *       supplied by the client (if any) is <strong>never trusted</strong>.</li>
 *   <li>Store the authenticated principal in the Spring Security context so
 *       that downstream controllers can call
 *       {@code SecurityContextHolder.getContext().getAuthentication()}.</li>
 * </ol>
 *
 * <h2>Error handling</h2>
 * <ul>
 *   <li>Missing / malformed header → unauthenticated (no 401 here; Spring
 *       Security rejects protected paths later).</li>
 *   <li>Invalid / expired token → HTTP 401 with a JSON error body.</li>
 *   <li>{@link FirebaseAuth} bean is null (no credentials configured) →
 *       HTTP 503 with a clear message.</li>
 * </ul>
 *
 * <p><strong>Security invariant:</strong> the UID is read only from
 * {@link FirebaseToken#getUid()}, never from any request parameter or body.
 */
@Component
public class FirebaseAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * May be {@code null} when Firebase credentials are not configured.
     * The filter handles this gracefully.
     */
    private final FirebaseAuth firebaseAuth;

    public FirebaseAuthFilter(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // ── No Bearer token present – let the request through unauthenticated ──
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Firebase not configured ───────────────────────────────────────────
        if (firebaseAuth == null) {
            log.error("FirebaseAuth is not available – GOOGLE_APPLICATION_CREDENTIALS not set.");
            sendError(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Authentication service is not configured on this server.");
            return;
        }

        String idToken = authHeader.substring(BEARER_PREFIX.length()).strip();

        try {
            // Server-side token verification (signature + expiry + audience)
            FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken);

            // Extract UID exclusively from the verified server-side token
            String uid = decoded.getUid();

            // Store in Spring Security context so controllers can read it
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            decoded,                                      // principal = full decoded token
                            null,                                         // credentials (not needed)
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated Firebase user uid={}", uid);

        } catch (FirebaseAuthException e) {
            // Token is invalid, expired, or revoked
            log.warn("Firebase token verification failed: {}", e.getMessage());
            sendError(response, HttpStatus.UNAUTHORIZED,
                    "Invalid or expired Firebase ID token.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = String.format("{\"error\":\"%s\",\"status\":%d}", message, status.value());
        response.getWriter().write(body);
    }
}

