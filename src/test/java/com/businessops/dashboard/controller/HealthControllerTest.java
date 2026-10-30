package com.businessops.dashboard.controller;

import com.businessops.dashboard.config.SecurityConfig;
import com.businessops.dashboard.security.FirebaseAuthFilter;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link HealthController}.
 *
 * <p>Uses {@code @WebMvcTest} with the real {@link SecurityConfig} and
 * {@link FirebaseAuthFilter} loaded to verify that the health endpoint is
 * publicly accessible (no token required) even after Spring Security is enabled.
 *
 * <p>No real Firebase credentials are needed – {@link FirebaseAuth} is mocked
 * and the filter simply passes unauthenticated requests to the public path.
 */
@WebMvcTest(controllers = HealthController.class)
@Import({SecurityConfig.class, FirebaseAuthFilter.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * FirebaseAuth must be present as a bean even when mocked, because
     * FirebaseAuthFilter takes it as a constructor argument.
     */
    @MockitoBean
    private FirebaseAuth firebaseAuth;

    @Test
    @DisplayName("GET /api/health → 200 OK with correct JSON body (no auth required)")
    void healthEndpointReturns200WithCorrectBody() throws Exception {
        mockMvc.perform(get("/api/health")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.status").value("UP"))
               .andExpect(jsonPath("$.application").value("BusinessOPS and analyticsDASHBOARD"));
    }

    @Test
    @DisplayName("GET /api/health → response body contains both required fields")
    void healthEndpointBodyHasBothFields() throws Exception {
        mockMvc.perform(get("/api/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").exists())
               .andExpect(jsonPath("$.application").exists());
    }

    @Test
    @DisplayName("GET /api/health → Content-Type is application/json")
    void healthEndpointReturnsJsonContentType() throws Exception {
        mockMvc.perform(get("/api/health"))
               .andExpect(status().isOk())
               .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("GET /api/health → accessible without Authorization header")
    void healthEndpointAccessibleWithoutToken() throws Exception {
        // No Authorization header – should still return 200 for the public health path
        mockMvc.perform(get("/api/health"))
               .andExpect(status().isOk());
    }
}
