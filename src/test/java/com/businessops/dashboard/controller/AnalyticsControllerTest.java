package com.businessops.dashboard.controller;

import com.businessops.dashboard.config.SecurityConfig;
import com.businessops.dashboard.dto.CategoryAnalyticsResponse;
import com.businessops.dashboard.dto.CategoryPerformanceItem;
import com.businessops.dashboard.dto.ClientPerformanceItem;
import com.businessops.dashboard.dto.ClientPerformanceResponse;
import com.businessops.dashboard.dto.CompletenessMetadata;
import com.businessops.dashboard.dto.FinancialSummaryResponse;
import com.businessops.dashboard.dto.FinancialTrendsResponse;
import com.businessops.dashboard.dto.TrendPeriodItem;
import com.businessops.dashboard.exception.AnalyticsProcessingLimitException;
import com.businessops.dashboard.exception.DatabaseOperationException;
import com.businessops.dashboard.exception.GlobalExceptionHandler;
import com.businessops.dashboard.exception.LimitSafeguardType;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.exception.WorkspaceAccessDeniedException;
import com.businessops.dashboard.security.FirebaseAuthFilter;
import com.businessops.dashboard.security.SecurityUtils;
import com.businessops.dashboard.service.AnalyticsService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer tests for {@link AnalyticsController}.
 *
 * <p>Verifies REST routing, request parameter binding, workspace security,
 * status code mapping (200, 400, 401, 403, 404, 422, 500), and JSON structure.
 */
@WebMvcTest(controllers = AnalyticsController.class)
@Import({SecurityConfig.class, FirebaseAuthFilter.class, GlobalExceptionHandler.class})
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private SecurityUtils securityUtils;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    private static final String VALID_TOKEN = "valid.firebase.token";
    private static final String TEST_UID = "user-test-789";
    private static final String WS_ID = "ws-abc-123";

    @BeforeEach
    void setUp() throws Exception {
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn(TEST_UID);
        when(firebaseAuth.verifyIdToken(VALID_TOKEN)).thenReturn(mockToken);
        when(securityUtils.getAuthenticatedUid()).thenReturn(TEST_UID);
    }

    // =========================================================================
    // 1. Successful Responses (200 OK)
    // =========================================================================

    @Test
    @DisplayName("GET /analytics/summary → 200 OK with complete financial metrics")
    void getFinancialSummary_validRequest_returns200() throws Exception {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-02-01T00:00:00Z");

        FinancialSummaryResponse mockResponse = new FinancialSummaryResponse(
                WS_ID, "INR", 2, start, end, "UTC",
                1000000L, "10000.00", 400000L, "4000.00", 600000L, "6000.00",
                new BigDecimal("60.00"), true, 10L, 7L, 3L,
                0L, "0.00", 0L, "0.00", 600000L, "6000.00", 0L,
                List.of("INR", "USD"),
                CompletenessMetadata.complete(10L, 10L)
        );

        when(analyticsService.getFinancialSummary(
                eq(TEST_UID), eq(WS_ID), any(), any(), any(), any(), any(), any(), anyBoolean()
        )).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/analytics/summary", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .param("currency", "INR")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(WS_ID))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.totalRevenueMinor").value(1000000))
                .andExpect(jsonPath("$.formattedRevenue").value("10000.00"))
                .andExpect(jsonPath("$.totalExpenseMinor").value(400000))
                .andExpect(jsonPath("$.netProfitMinor").value(600000))
                .andExpect(jsonPath("$.profitMarginPercentage").value(60.00))
                .andExpect(jsonPath("$.isMarginComputable").value(true))
                .andExpect(jsonPath("$.completeness.isComplete").value(true))
                .andExpect(jsonPath("$.availableCurrencies", hasSize(2)));
    }

    @Test
    @DisplayName("GET /analytics/trends → 200 OK with contiguous period items")
    void getFinancialTrends_validRequest_returns200() throws Exception {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-03-01T00:00:00Z");

        TrendPeriodItem p1 = new TrendPeriodItem(
                "2026-01", start, Instant.parse("2026-02-01T00:00:00Z"),
                50000L, "500.00", 20000L, "200.00", 30000L, "300.00",
                new BigDecimal("60.00"), 2L,
                0L, "0.00", 0L, "0.00", 30000L, "300.00", 0L
        );

        FinancialTrendsResponse mockResponse = new FinancialTrendsResponse(
                WS_ID, "USD", 2, "MONTHLY", start, end, "UTC",
                List.of(p1), List.of("USD"), CompletenessMetadata.complete(2L, 2L)
        );

        when(analyticsService.getFinancialTrends(
                eq(TEST_UID), eq(WS_ID), any(), any(), any(), any(), any(), any(), any(), anyBoolean()
        )).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/analytics/trends", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .param("interval", "MONTHLY")
                        .param("currency", "USD")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(WS_ID))
                .andExpect(jsonPath("$.interval").value("MONTHLY"))
                .andExpect(jsonPath("$.periods", hasSize(1)))
                .andExpect(jsonPath("$.periods[0].period").value("2026-01"))
                .andExpect(jsonPath("$.periods[0].revenueMinor").value(50000));
    }

    @Test
    @DisplayName("GET /analytics/categories → 200 OK with category breakdown")
    void getCategoryAnalytics_validRequest_returns200() throws Exception {
        CategoryPerformanceItem cat = new CategoryPerformanceItem(
                "cat-1", "Software Subscriptions", "#3B82F6", "EXPENSE",
                15000L, "150.00", 3L, new BigDecimal("100.00"),
                0L, "0.00", 0L
        );

        CategoryAnalyticsResponse mockResponse = new CategoryAnalyticsResponse(
                WS_ID, "USD", 2, Instant.now(), Instant.now(), "UTC",
                15000L, "150.00", 0L, "0.00",
                0L, "0.00", 0L, "0.00",
                List.of(cat), List.of("USD"), CompletenessMetadata.complete(3L, 3L)
        );

        when(analyticsService.getCategoryAnalytics(
                eq(TEST_UID), eq(WS_ID), any(), any(), any(), any(), any(), any(), any(), anyBoolean()
        )).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/analytics/categories", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .param("type", "EXPENSE")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenseMinor").value(15000))
                .andExpect(jsonPath("$.categories", hasSize(1)))
                .andExpect(jsonPath("$.categories[0].categoryName").value("Software Subscriptions"));
    }

    @Test
    @DisplayName("GET /analytics/clients → 200 OK with client rankings")
    void getClientPerformance_validRequest_returns200() throws Exception {
        ClientPerformanceItem clientItem = new ClientPerformanceItem(
                "cl-1", "Acme Corporation",
                100000L, "1000.00", 2L,
                new BigDecimal("50000.00"), "500.00", new BigDecimal("100.00"),
                0L, "0.00", 0L
        );

        ClientPerformanceResponse mockResponse = new ClientPerformanceResponse(
                WS_ID, "USD", 2, Instant.now(), Instant.now(), "UTC",
                100000L, "1000.00", 0L, "0.00",
                0L, "0.00", List.of(clientItem), List.of("USD"),
                CompletenessMetadata.complete(2L, 2L)
        );

        when(analyticsService.getClientPerformance(
                eq(TEST_UID), eq(WS_ID), any(), any(), any(), any(), any(), any(), anyBoolean(), anyInt()
        )).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/analytics/clients", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .param("limit", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRealizedRevenueMinor").value(100000))
                .andExpect(jsonPath("$.clients", hasSize(1)))
                .andExpect(jsonPath("$.clients[0].clientName").value("Acme Corporation"));
    }

    // =========================================================================
    // 2. Validation & Parameter Rejections (400 Bad Request)
    // =========================================================================

    @Test
    @DisplayName("GET /analytics/trends with invalid interval → 400 Bad Request")
    void getTrends_invalidInterval_returns400() throws Exception {
        when(analyticsService.getFinancialTrends(
                eq(TEST_UID), eq(WS_ID), anyString(), any(), any(), any(), any(), any(), any(), anyBoolean()
        )).thenThrow(new IllegalArgumentException("Invalid interval: 'DAILY'. Expected 'MONTHLY' or 'YEARLY'"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/analytics/trends", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .param("interval", "DAILY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid Argument"))
                .andExpect(jsonPath("$.message", containsString("Expected 'MONTHLY' or 'YEARLY'")));
    }

    @Test
    @DisplayName("GET /analytics/summary with ambiguous mixed dates → 400 Bad Request")
    void getSummary_mixedDates_returns400() throws Exception {
        when(analyticsService.getFinancialSummary(
                eq(TEST_UID), eq(WS_ID), any(), any(), any(), any(), any(), any(), anyBoolean()
        )).thenThrow(new IllegalArgumentException("Ambiguous date parameters: cannot mix calendar dates with exact timestamps"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/analytics/summary", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .param("startDate", "2026-01-01")
                        .param("startInstant", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Ambiguous date parameters")));
    }

    @Test
    @DisplayName("GET /analytics/clients with invalid limit → 400 Bad Request")
    void getClients_invalidLimit_returns400() throws Exception {
        when(analyticsService.getClientPerformance(
                eq(TEST_UID), eq(WS_ID), any(), any(), any(), any(), any(), any(), anyBoolean(), anyInt()
        )).thenThrow(new IllegalArgumentException("limit must be between 1 and 100, got: 0"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/analytics/clients", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("limit must be between 1 and 100")));
    }

    // =========================================================================
    // 3. Security, Authorization & Not Found (401, 403, 404)
    // =========================================================================

    @Test
    @DisplayName("Unauthenticated request without token → 401 Unauthorized")
    void unauthenticatedRequest_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/analytics/summary", WS_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Non-member request → 403 Forbidden")
    void unauthorizedWorkspaceAccess_returns403() throws Exception {
        when(analyticsService.getFinancialSummary(
                eq(TEST_UID), eq(WS_ID), any(), any(), any(), any(), any(), any(), anyBoolean()
        )).thenThrow(new WorkspaceAccessDeniedException("You do not have permission to access workspace: " + WS_ID));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/analytics/summary", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message", containsString("do not have permission")));
    }

    @Test
    @DisplayName("Non-existent workspace → 404 Not Found")
    void nonExistentWorkspace_returns404() throws Exception {
        when(analyticsService.getFinancialSummary(
                eq(TEST_UID), eq("ws-missing"), any(), any(), any(), any(), any(), any(), anyBoolean()
        )).thenThrow(new ResourceNotFoundException("Workspace not found with id: ws-missing"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/analytics/summary", "ws-missing")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("Workspace not found")));
    }

    // =========================================================================
    // 4. Processing Limit Safeguards (422 Unprocessable Content)
    // =========================================================================

    @Test
    @DisplayName("Tier 1 Pre-flight count > 10,000 → 422 Unprocessable Content with exact count")
    void preflightLimitExceeded_returns422WithLimitDetails() throws Exception {
        AnalyticsProcessingLimitException ex = new AnalyticsProcessingLimitException(
                LimitSafeguardType.PREFLIGHT_COUNT_SAFEGUARD,
                0L,
                24500L
        );

        when(analyticsService.getCategoryAnalytics(
                eq(TEST_UID), eq(WS_ID), any(), any(), any(), any(), any(), any(), any(), anyBoolean()
        )).thenThrow(ex);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/analytics/categories", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Content"))
                .andExpect(jsonPath("$.errorCode").value("ANALYTICS_PROCESSING_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.limitDetails.enforcementTier").value("PREFLIGHT_COUNT_SAFEGUARD"))
                .andExpect(jsonPath("$.limitDetails.maxAllowedCount").value(10000))
                .andExpect(jsonPath("$.limitDetails.totalEligibleCount").value(24500))
                .andExpect(jsonPath("$.limitDetails.observedMatchingCountLowerBound", nullValue()));
    }

    @Test
    @DisplayName("Tier 2 Runtime document 10,001 encounter → 422 Unprocessable Content with lower bound")
    void runtimeLimitExceeded_returns422WithLowerBound() throws Exception {
        AnalyticsProcessingLimitException ex = new AnalyticsProcessingLimitException(
                LimitSafeguardType.RUNTIME_STREAMING_SAFEGUARD,
                10000L,
                null
        );

        when(analyticsService.getFinancialTrends(
                eq(TEST_UID), eq(WS_ID), any(), any(), any(), any(), any(), any(), any(), anyBoolean()
        )).thenThrow(ex);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/analytics/trends", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Content"))
                .andExpect(jsonPath("$.errorCode").value("ANALYTICS_PROCESSING_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.limitDetails.enforcementTier").value("RUNTIME_STREAMING_SAFEGUARD"))
                .andExpect(jsonPath("$.limitDetails.maxAllowedCount").value(10000))
                .andExpect(jsonPath("$.limitDetails.totalEligibleCount", nullValue()))
                .andExpect(jsonPath("$.limitDetails.observedMatchingCountLowerBound").value(10001));
    }

    // =========================================================================
    // 5. Database Failure / Overflow (500 Internal Server Error)
    // =========================================================================

    @Test
    @DisplayName("Database failure or arithmetic overflow → 500 Internal Server Error masked")
    void databaseFailure_returns500Masked() throws Exception {
        when(analyticsService.getFinancialSummary(
                eq(TEST_UID), eq(WS_ID), any(), any(), any(), any(), any(), any(), anyBoolean()
        )).thenThrow(new DatabaseOperationException("Internal database timeout"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/analytics/summary", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message", containsString("Please try again later")));
    }
}

