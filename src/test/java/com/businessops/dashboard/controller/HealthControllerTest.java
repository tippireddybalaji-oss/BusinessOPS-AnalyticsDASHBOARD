package com.businessops.dashboard.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link HealthController}.
 *
 * <p>Starts the full Spring application context but fires requests through
 * MockMvc (no real HTTP port needed).  Firebase initialisation is tested in
 * graceful-degradation mode – no real credentials are required.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/health → 200 OK with correct JSON body")
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
}
