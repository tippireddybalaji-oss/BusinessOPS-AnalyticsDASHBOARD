package com.businessops.dashboard.controller;

import com.businessops.dashboard.config.SecurityConfig;
import com.businessops.dashboard.dto.PagedResponse;
import com.businessops.dashboard.dto.TransactionCreateRequest;
import com.businessops.dashboard.dto.TransactionResponse;
import com.businessops.dashboard.dto.TransactionUpdateRequest;
import com.businessops.dashboard.exception.GlobalExceptionHandler;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.exception.WorkspaceAccessDeniedException;
import com.businessops.dashboard.security.FirebaseAuthFilter;
import com.businessops.dashboard.security.SecurityUtils;
import com.businessops.dashboard.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link TransactionController}.
 */
@WebMvcTest(controllers = TransactionController.class)
@Import({SecurityConfig.class, FirebaseAuthFilter.class, GlobalExceptionHandler.class})
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private SecurityUtils securityUtils;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    private static final String VALID_TOKEN = "valid.firebase.token";
    private static final String TEST_UID = "user-test-789";
    private static final String WS_ID = "ws-abc-123";
    private static final String TX_ID = "tx-xyz-456";

    @BeforeEach
    void setUp() throws Exception {
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn(TEST_UID);
        when(firebaseAuth.verifyIdToken(VALID_TOKEN)).thenReturn(mockToken);
        when(securityUtils.getAuthenticatedUid()).thenReturn(TEST_UID);
    }

    @Test
    @DisplayName("POST /api/v1/workspaces/{id}/transactions → 201 Created on valid input")
    void createTransaction_validInput_returns201Created() throws Exception {
        TransactionCreateRequest request = new TransactionCreateRequest(
                "INCOME", 250000L, "INR", "def_inc_sales", "cl-1",
                "Client consulting payment", Instant.now(), "UPI", "COMPLETED"
        );

        TransactionResponse response = new TransactionResponse(
                TX_ID, WS_ID, "INCOME", 250000L, "2500.00", "INR", 2,
                "def_inc_sales", "Client Invoicing / Sales", "#10B981",
                "cl-1", "Acme Corp", "Client consulting payment",
                Instant.now(), "UPI", "COMPLETED",
                TEST_UID, TEST_UID, Instant.now(), Instant.now()
        );

        when(transactionService.createTransaction(eq(TEST_UID), eq(WS_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/transactions", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TX_ID))
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.amountMinor").value(250000))
                .andExpect(jsonPath("$.formattedAmount").value("2500.00"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.fractionDigits").value(2))
                .andExpect(jsonPath("$.categoryName").value("Client Invoicing / Sales"))
                .andExpect(jsonPath("$.clientName").value("Acme Corp"));
    }

    @Test
    @DisplayName("POST /api/v1/workspaces/{id}/transactions → 400 Bad Request on invalid input")
    void createTransaction_invalidInput_returns400() throws Exception {
        TransactionCreateRequest badRequest = new TransactionCreateRequest(
                "", null, null, "", null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/transactions", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.type").exists())
                .andExpect(jsonPath("$.validationErrors.amountMinor").exists())
                .andExpect(jsonPath("$.validationErrors.categoryId").exists());
    }

    @Test
    @DisplayName("POST /api/v1/workspaces/{id}/transactions → 403 Forbidden when MEMBER attempts creation")
    void createTransaction_memberRole_returns403() throws Exception {
        TransactionCreateRequest request = new TransactionCreateRequest(
                "EXPENSE", 1000L, "INR", "cat-1", null, null, null, null, null
        );

        when(transactionService.createTransaction(eq(TEST_UID), eq(WS_ID), any()))
                .thenThrow(new WorkspaceAccessDeniedException("Member lacks write privileges"));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/transactions", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("GET /api/v1/workspaces/{id}/transactions → 200 OK with PagedResponse")
    void listTransactions_returns200OkWithPagedResponse() throws Exception {
        TransactionResponse tx1 = new TransactionResponse(
                TX_ID, WS_ID, "INCOME", 100000L, "1000.00", "INR", 2,
                "cat-1", "Sales", "#10B981", null, null, "Desc",
                Instant.now(), "BANK_TRANSFER", "COMPLETED",
                TEST_UID, TEST_UID, Instant.now(), Instant.now()
        );

        PagedResponse<TransactionResponse> pagedResponse = new PagedResponse<>(
                List.of(tx1), 0, 20, 1, 1, false, false, null
        );

        when(transactionService.listTransactions(eq(TEST_UID), eq(WS_ID), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/transactions", WS_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(TX_ID))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.isWindowTruncated").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/workspaces/{id}/transactions with search warning → 200 OK with truncation metadata")
    void listTransactions_withTruncation_returnsWarningMetadata() throws Exception {
        PagedResponse<TransactionResponse> pagedResponse = new PagedResponse<>(
                List.of(), 0, 20, 500, 25, true, true,
                "Search results are bounded to the 500 most recent transactions."
        );

        when(transactionService.listTransactions(eq(TEST_UID), eq(WS_ID), any(), any(), any(), any(), any(), eq("query"), anyInt(), anyInt()))
                .thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/transactions", WS_ID)
                        .param("search", "query")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isWindowTruncated").value(true))
                .andExpect(jsonPath("$.searchWarning").value(containsString("Search results are bounded")));
    }

    @Test
    @DisplayName("GET /api/v1/workspaces/{id}/transactions/{txId} → 200 OK when found")
    void getTransaction_found_returns200() throws Exception {
        TransactionResponse response = new TransactionResponse(
                TX_ID, WS_ID, "INCOME", 50000L, "500.00", "USD", 2,
                "cat-1", "Sales", "#10B981", null, null, null,
                Instant.now(), "BANK_TRANSFER", "COMPLETED",
                TEST_UID, TEST_UID, Instant.now(), Instant.now()
        );

        when(transactionService.getTransactionById(TEST_UID, WS_ID, TX_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/transactions/{transactionId}", WS_ID, TX_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TX_ID))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @DisplayName("GET /api/v1/workspaces/{id}/transactions/{txId} → 404 Not Found when absent")
    void getTransaction_notFound_returns404() throws Exception {
        when(transactionService.getTransactionById(TEST_UID, WS_ID, "missing-tx"))
                .thenThrow(new ResourceNotFoundException("Transaction not found"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/transactions/{transactionId}", WS_ID, "missing-tx")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PUT /api/v1/workspaces/{id}/transactions/{txId} → 200 OK on valid update")
    void updateTransaction_valid_returns200() throws Exception {
        TransactionUpdateRequest updateRequest = new TransactionUpdateRequest(
                "EXPENSE", 4000L, "EUR", "cat-2", null, "Updated note", null, "CARD", "COMPLETED"
        );

        TransactionResponse updatedResponse = new TransactionResponse(
                TX_ID, WS_ID, "EXPENSE", 4000L, "40.00", "EUR", 2,
                "cat-2", "Supplies", "#EF4444", null, null, "Updated note",
                Instant.now(), "CARD", "COMPLETED",
                TEST_UID, TEST_UID, Instant.now(), Instant.now()
        );

        when(transactionService.updateTransaction(eq(TEST_UID), eq(WS_ID), eq(TX_ID), any()))
                .thenReturn(updatedResponse);

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/transactions/{transactionId}", WS_ID, TX_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TX_ID))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.amountMinor").value(4000))
                .andExpect(jsonPath("$.formattedAmount").value("40.00"))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    @DisplayName("DELETE /api/v1/workspaces/{id}/transactions/{txId} → 204 No Content")
    void deleteTransaction_returns204() throws Exception {
        doNothing().when(transactionService).deleteTransaction(TEST_UID, WS_ID, TX_ID);

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/transactions/{transactionId}", WS_ID, TX_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());

        verify(transactionService).deleteTransaction(TEST_UID, WS_ID, TX_ID);
    }

    @Test
    @DisplayName("DELETE /api/v1/workspaces/{id}/transactions/{txId} → 403 Forbidden for MEMBER")
    void deleteTransaction_memberRole_returns403() throws Exception {
        doThrow(new WorkspaceAccessDeniedException("Member lacks write permissions"))
                .when(transactionService).deleteTransaction(TEST_UID, WS_ID, TX_ID);

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/transactions/{transactionId}", WS_ID, TX_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("DELETE /api/v1/workspaces/{id}/transactions/{txId} → 404 Not Found when absent")
    void deleteTransaction_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Transaction not found"))
                .when(transactionService).deleteTransaction(TEST_UID, WS_ID, "missing-tx");

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/transactions/{transactionId}", WS_ID, "missing-tx")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
