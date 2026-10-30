package com.businessops.dashboard.controller;

import com.businessops.dashboard.config.SecurityConfig;
import com.businessops.dashboard.security.FirebaseAuthFilter;
import com.google.firebase.auth.FirebaseAuth;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link AuthController}.
 *
 * <p>Uses {@code @WebMvcTest} (no full context, no real Firebase) with
 * {@code @MockitoBean} for {@link FirebaseAuth}.  The filter and security
 * rules are loaded via {@code @Import} so we test the real security behaviour
 * without any credentials.
 *
 * <p>Firebase final classes (FirebaseAuth, FirebaseToken) are mocked with
 * Mockito's default mock() — Spring Boot test's auto-configuration handles
 * the byte-buddy agent automatically in the test classpath.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, FirebaseAuthFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    // ── No token → 401 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/auth/me without token → 401 Unauthorized")
    void meWithoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isUnauthorized());
    }

    // ── Invalid token → 401 ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/auth/me with invalid token → 401 Unauthorized")
    void meWithInvalidToken_returns401() throws Exception {
        when(firebaseAuth.verifyIdToken(anyString()))
                .thenThrow(new com.google.firebase.auth.FirebaseAuthException(
                        com.google.firebase.ErrorCode.INVALID_ARGUMENT,
                        "Invalid token",
                        null, null, null));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isUnauthorized());
    }

    // ── Valid token → 200 with uid + email ───────────────────────────────────

    @Test
    @DisplayName("GET /api/auth/me with valid token → 200 OK with uid and email")
    void meWithValidToken_returns200WithUidAndEmail() throws Exception {
        // Build a mock FirebaseToken (final class – use Mockito.mock())
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("test-uid-abc123");
        when(mockToken.getEmail()).thenReturn("user@example.com");

        when(firebaseAuth.verifyIdToken("valid.test.token")).thenReturn(mockToken);

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid.test.token")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.uid").value("test-uid-abc123"))
               .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    // ── Valid token, null email (e.g. anonymous / phone auth) → 200 ──────────

    @Test
    @DisplayName("GET /api/auth/me with valid token and no email → 200 OK, email is null")
    void meWithValidToken_noEmail_returns200() throws Exception {
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("anon-uid-xyz");
        when(mockToken.getEmail()).thenReturn(null);

        when(firebaseAuth.verifyIdToken("valid.anon.token")).thenReturn(mockToken);

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid.anon.token")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.uid").value("anon-uid-xyz"))
               .andExpect(jsonPath("$.email").isEmpty());
    }

    // ── Response must never include raw token fields ──────────────────────────

    @Test
    @DisplayName("GET /api/auth/me → response does not expose raw token fields")
    void meResponse_doesNotExposeRawTokenFields() throws Exception {
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("safe-uid");
        when(mockToken.getEmail()).thenReturn("safe@example.com");

        when(firebaseAuth.verifyIdToken("safe.token")).thenReturn(mockToken);

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer safe.token")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               // Only uid and email must be present
               .andExpect(jsonPath("$.uid").exists())
               .andExpect(jsonPath("$.email").exists())
               // Raw token, claims, and other sensitive fields must NOT be present
               .andExpect(jsonPath("$.token").doesNotExist())
               .andExpect(jsonPath("$.claims").doesNotExist())
               .andExpect(jsonPath("$.issuer").doesNotExist());
    }
}
