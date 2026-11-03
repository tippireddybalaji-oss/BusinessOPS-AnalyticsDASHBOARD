package com.businessops.dashboard.controller;

import com.businessops.dashboard.config.SecurityConfig;
import com.businessops.dashboard.exception.GlobalExceptionHandler;
import com.businessops.dashboard.security.FirebaseAuthFilter;
import com.google.firebase.ErrorCode;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit and integration tests for {@link AuthController}.
 *
 * <p>Tests both the standardized {@code GET /api/v1/me} and legacy {@code GET /api/auth/me}.
 * Verifies server-side ID token verification, 401 on missing, invalid, or expired tokens,
 * prevention of client UID spoofing, and CORS preflight handling.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, FirebaseAuthFilter.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    // ── Missing Token → 401 ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/me without token → 401 Unauthorized")
    void v1MeWithoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.status").value(401))
               .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("GET /api/auth/me without token → 401 Unauthorized (legacy path)")
    void legacyMeWithoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.status").value(401));
    }

    // ── Invalid Token → 401 ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/me with invalid token → 401 Unauthorized")
    void meWithInvalidToken_returns401() throws Exception {
        when(firebaseAuth.verifyIdToken(anyString()))
                .thenThrow(new FirebaseAuthException(
                        ErrorCode.INVALID_ARGUMENT,
                        "Invalid token signature",
                        null, null, null));

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.string")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.status").value(401))
               .andExpect(jsonPath("$.error").value("Invalid or expired Firebase ID token."));
    }

    // ── Expired Token → 401 ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/me with expired token → 401 Unauthorized")
    void meWithExpiredToken_returns401() throws Exception {
        when(firebaseAuth.verifyIdToken(anyString()))
                .thenThrow(new FirebaseAuthException(
                        ErrorCode.UNAUTHENTICATED,
                        "Firebase ID token has expired",
                        null, null, AuthErrorCode.EXPIRED_ID_TOKEN));

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired.firebase.token")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.status").value(401))
               .andExpect(jsonPath("$.error").value("Invalid or expired Firebase ID token."));
    }

    // ── Valid Token → 200 with UID and Email ─────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/me with valid token → 200 OK with uid and email")
    void v1MeWithValidToken_returns200WithUidAndEmail() throws Exception {
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("verified-uid-12345");
        when(mockToken.getEmail()).thenReturn("developer@businessops.io");

        when(firebaseAuth.verifyIdToken("valid.jwt.token")).thenReturn(mockToken);

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid.jwt.token")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.uid").value("verified-uid-12345"))
               .andExpect(jsonPath("$.email").value("developer@businessops.io"));
    }

    @Test
    @DisplayName("GET /api/auth/me with valid token → 200 OK (legacy path)")
    void legacyMeWithValidToken_returns200() throws Exception {
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("legacy-uid-999");
        when(mockToken.getEmail()).thenReturn("legacy@businessops.io");

        when(firebaseAuth.verifyIdToken("valid.legacy.token")).thenReturn(mockToken);

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid.legacy.token")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.uid").value("legacy-uid-999"))
               .andExpect(jsonPath("$.email").value("legacy@businessops.io"));
    }

    // ── Client UID Spoofing Prevention ───────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/me ignores client query parameter and returns server-verified UID")
    void meIgnoresClientSuppliedUid() throws Exception {
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("authentic-server-uid");
        when(mockToken.getEmail()).thenReturn("user@businessops.io");

        when(firebaseAuth.verifyIdToken("valid.token")).thenReturn(mockToken);

        mockMvc.perform(get("/api/v1/me?uid=attacker-spoofed-uid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.uid").value("authentic-server-uid"));
    }

    // ── Valid Token, Null Email ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/me with valid token and null email → 200 OK, email empty/null")
    void meWithValidToken_noEmail_returns200() throws Exception {
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("phone-auth-uid");
        when(mockToken.getEmail()).thenReturn(null);

        when(firebaseAuth.verifyIdToken("valid.phone.token")).thenReturn(mockToken);

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid.phone.token")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.uid").value("phone-auth-uid"))
               .andExpect(jsonPath("$.email").isEmpty());
    }

    // ── Sensitive Information Leak Prevention ────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/me response never leaks raw token fields or credentials")
    void meResponse_doesNotExposeRawTokenFields() throws Exception {
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("safe-uid");
        when(mockToken.getEmail()).thenReturn("safe@businessops.io");

        when(firebaseAuth.verifyIdToken("safe.token")).thenReturn(mockToken);

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer safe.token")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.uid").exists())
               .andExpect(jsonPath("$.email").exists())
               .andExpect(jsonPath("$.token").doesNotExist())
               .andExpect(jsonPath("$.claims").doesNotExist())
               .andExpect(jsonPath("$.issuer").doesNotExist())
               .andExpect(jsonPath("$.privateKey").doesNotExist());
    }

    // ── CORS Preflight Check ─────────────────────────────────────────────────

    @Test
    @DisplayName("OPTIONS /api/v1/me pre-flight request succeeds with CORS headers for allowed origin")
    void corsPreflight_succeedsForAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
               .andExpect(status().isOk())
               .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
               .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
    }
}
