package com.businessops.dashboard.service;

import com.businessops.dashboard.dto.PagedResponse;
import com.businessops.dashboard.dto.TransactionCreateRequest;
import com.businessops.dashboard.dto.TransactionResponse;
import com.businessops.dashboard.dto.TransactionUpdateRequest;
import com.businessops.dashboard.dto.WorkspaceResponse;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.exception.WorkspaceAccessDeniedException;
import com.businessops.dashboard.model.Transaction;
import com.businessops.dashboard.model.WorkspaceRole;
import com.businessops.dashboard.repository.TransactionRepository;
import com.google.cloud.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TransactionService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceTest {

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private TransactionRepository transactionRepository;

    private TransactionService service;

    private static final String AUTH_UID = "user-123";
    private static final String WS_ID = "ws-abc-456";
    private static final String TX_ID = "tx-xyz-789";

    @BeforeEach
    void setUp() {
        service = new TransactionService(workspaceService, transactionRepository);

        WorkspaceResponse defaultWorkspace = new WorkspaceResponse(
                WS_ID, "Acme Workspace", AUTH_UID, "USD",
                Instant.now(), Instant.now(), WorkspaceRole.OWNER
        );
        when(workspaceService.getWorkspaceById(anyString(), eq(WS_ID))).thenReturn(defaultWorkspace);
        when(workspaceService.verifyWorkspaceWriteAccess(anyString(), eq(WS_ID))).thenReturn(WorkspaceRole.OWNER);
        when(workspaceService.verifyWorkspaceAccess(anyString(), eq(WS_ID))).thenReturn(WorkspaceRole.OWNER);
    }

    @Test
    @DisplayName("createTransaction: OWNER and ADMIN succeed and use workspace default currency when request omits currency")
    void createTransaction_omittedCurrency_usesWorkspaceDefaultCurrency() {
        TransactionCreateRequest request = new TransactionCreateRequest(
                "INCOME", 75000L, null, "cat-1", null, "Client invoice", null, "BANK_TRANSFER", "COMPLETED"
        );

        when(transactionRepository.createTransaction(eq(WS_ID), any(Transaction.class)))
                .thenAnswer(inv -> {
                    Transaction tx = inv.getArgument(1);
                    tx.setId(TX_ID);
                    return tx;
                });

        TransactionResponse response = service.createTransaction(AUTH_UID, WS_ID, request);

        assertThat(response.id()).isEqualTo(TX_ID);
        assertThat(response.currency()).isEqualTo("USD"); // inherited from workspace, not defaulted to INR
        assertThat(response.amountMinor()).isEqualTo(75000L);
        assertThat(response.formattedAmount()).isEqualTo("750.00");
    }

    @Test
    @DisplayName("createTransaction: MEMBER role is rejected with 403 Forbidden")
    void createTransaction_whenMemberRole_throwsWorkspaceAccessDenied() {
        doThrow(new WorkspaceAccessDeniedException("User lacks write access"))
                .when(workspaceService).verifyWorkspaceWriteAccess(AUTH_UID, WS_ID);

        TransactionCreateRequest request = new TransactionCreateRequest(
                "INCOME", 1000L, "USD", "cat-1", null, null, null, null, null
        );

        assertThatThrownBy(() -> service.createTransaction(AUTH_UID, WS_ID, request))
                .isInstanceOf(WorkspaceAccessDeniedException.class);
    }

    @Test
    @DisplayName("createTransaction: rejects unsupported currency")
    void createTransaction_unsupportedCurrency_throwsIllegalArgument() {
        TransactionCreateRequest request = new TransactionCreateRequest(
                "INCOME", 1000L, "BITCOIN", "cat-1", null, null, null, null, null
        );

        assertThatThrownBy(() -> service.createTransaction(AUTH_UID, WS_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported currency");
    }

    @Test
    @DisplayName("createTransaction: rejects zero and negative amounts")
    void createTransaction_nonPositiveAmount_throwsIllegalArgument() {
        TransactionCreateRequest zeroRequest = new TransactionCreateRequest(
                "INCOME", 0L, "USD", "cat-1", null, null, null, null, null
        );

        assertThatThrownBy(() -> service.createTransaction(AUTH_UID, WS_ID, zeroRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be greater than zero");

        TransactionCreateRequest negativeRequest = new TransactionCreateRequest(
                "INCOME", -500L, "USD", "cat-1", null, null, null, null, null
        );

        assertThatThrownBy(() -> service.createTransaction(AUTH_UID, WS_ID, negativeRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be greater than zero");
    }

    @Test
    @DisplayName("createTransaction: audit fields are populated from verified UID")
    void createTransaction_populatesAuditFieldsFromToken() {
        TransactionCreateRequest request = new TransactionCreateRequest(
                "EXPENSE", 12000L, "USD", "cat-exp", null, "Software license", null, null, null
        );

        when(transactionRepository.createTransaction(eq(WS_ID), any(Transaction.class)))
                .thenAnswer(inv -> {
                    Transaction tx = inv.getArgument(1);
                    tx.setId(TX_ID);
                    return tx;
                });

        TransactionResponse response = service.createTransaction(AUTH_UID, WS_ID, request);

        assertThat(response.createdByUid()).isEqualTo(AUTH_UID);
        assertThat(response.updatedByUid()).isEqualTo(AUTH_UID);
    }

    @Test
    @DisplayName("getTransactionById: MEMBER can read transaction")
    void getTransactionById_memberCanRead() {
        when(workspaceService.verifyWorkspaceAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.MEMBER);

        Transaction tx = new Transaction();
        tx.setId(TX_ID);
        tx.setWorkspaceId(WS_ID);
        tx.setType("INCOME");
        tx.setAmountMinor(30000L);
        tx.setCurrency("USD");
        when(transactionRepository.findById(WS_ID, TX_ID)).thenReturn(Optional.of(tx));

        TransactionResponse response = service.getTransactionById(AUTH_UID, WS_ID, TX_ID);

        assertThat(response.id()).isEqualTo(TX_ID);
        assertThat(response.amountMinor()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("getTransactionById: throws ResourceNotFoundException if transaction missing")
    void getTransactionById_whenNotFound_throwsResourceNotFound() {
        when(transactionRepository.findById(WS_ID, "missing-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransactionById(AUTH_UID, WS_ID, "missing-id"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction not found with id: missing-id");
    }

    @Test
    @DisplayName("updateTransaction: MEMBER role cannot update transactions")
    void updateTransaction_memberRole_throwsAccessDenied() {
        doThrow(new WorkspaceAccessDeniedException("Member lacks write privileges"))
                .when(workspaceService).verifyWorkspaceWriteAccess(AUTH_UID, WS_ID);

        TransactionUpdateRequest request = new TransactionUpdateRequest(
                "INCOME", 2000L, "USD", "cat-1", null, "desc", null, null, null
        );

        assertThatThrownBy(() -> service.updateTransaction(AUTH_UID, WS_ID, TX_ID, request))
                .isInstanceOf(WorkspaceAccessDeniedException.class);
    }

    @Test
    @DisplayName("deleteTransaction: deletes when transaction exists")
    void deleteTransaction_whenExists_succeeds() {
        Transaction tx = new Transaction();
        tx.setId(TX_ID);
        when(transactionRepository.findById(WS_ID, TX_ID)).thenReturn(Optional.of(tx));

        service.deleteTransaction(AUTH_UID, WS_ID, TX_ID);

        verify(transactionRepository).deleteTransaction(WS_ID, TX_ID);
    }

    @Test
    @DisplayName("deleteTransaction: throws ResourceNotFoundException when transaction does not exist")
    void deleteTransaction_whenNotFound_throwsResourceNotFound() {
        when(transactionRepository.findById(WS_ID, "unknown-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTransaction(AUTH_UID, WS_ID, "unknown-id"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("listTransactions: validates pagination bounds")
    void listTransactions_invalidPagination_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.listTransactions(AUTH_UID, WS_ID, null, null, null, null, null, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page index cannot be negative");

        assertThatThrownBy(() -> service.listTransactions(AUTH_UID, WS_ID, null, null, null, null, null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page size must be between 1 and 100");

        assertThatThrownBy(() -> service.listTransactions(AUTH_UID, WS_ID, null, null, null, null, null, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page size must be between 1 and 100");
    }

    @Test
    @DisplayName("listTransactions: validates startDate cannot be after endDate")
    void listTransactions_invalidDateRange_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.listTransactions(
                AUTH_UID, WS_ID, null, null, null, "2026-02-01", "2026-01-01", null, 0, 20
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startDate cannot be after endDate");
    }

    @Test
    @DisplayName("listTransactions: bounded search with <= 500 documents marks isWindowTruncated = false")
    void listTransactions_searchExhaustive_underSentinelBoundary() {
        List<Transaction> candidates = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Transaction tx = new Transaction();
            tx.setId("tx-" + i);
            tx.setWorkspaceId(WS_ID);
            tx.setType("INCOME");
            tx.setAmountMinor(1000L);
            tx.setCurrency("USD");
            tx.setDescription(i % 2 == 0 ? "Google Cloud Retainer" : "Office Supplies");
            tx.setTransactionDate(Timestamp.now());
            candidates.add(tx);
        }

        when(transactionRepository.findTransactions(eq(WS_ID), any(), any(), any(), any(), any(), eq(501)))
                .thenReturn(candidates);

        PagedResponse<TransactionResponse> response = service.listTransactions(
                AUTH_UID, WS_ID, null, null, null, null, null, "Google", 0, 10
        );

        assertThat(response.isWindowTruncated()).isFalse();
        assertThat(response.searchWarning()).isNull();
        assertThat(response.totalElements()).isEqualTo(25); // exactly 25 matched
        assertThat(response.content()).hasSize(10); // page size 10
        assertThat(response.hasMore()).isTrue();
    }

    @Test
    @DisplayName("listTransactions: bounded search with 501 documents marks isWindowTruncated = true and attaches warning")
    void listTransactions_searchTruncated_reachesSentinelBoundary() {
        List<Transaction> candidates = new ArrayList<>();
        // 501 items returned from repository
        for (int i = 0; i < 501; i++) {
            Transaction tx = new Transaction();
            tx.setId("tx-" + i);
            tx.setWorkspaceId(WS_ID);
            tx.setType("INCOME");
            tx.setAmountMinor(1000L);
            tx.setCurrency("USD");
            tx.setDescription("Payroll salary expense " + i);
            tx.setTransactionDate(Timestamp.now());
            candidates.add(tx);
        }

        when(transactionRepository.findTransactions(eq(WS_ID), any(), any(), any(), any(), any(), eq(501)))
                .thenReturn(candidates);

        PagedResponse<TransactionResponse> response = service.listTransactions(
                AUTH_UID, WS_ID, null, null, null, null, null, "Payroll", 0, 20
        );

        assertThat(response.isWindowTruncated()).isTrue();
        assertThat(response.searchWarning()).contains("Search results are bounded to the 500 most recent transactions");
        // Exactly 500 candidates evaluated (the 501st sentinel is discarded)
        assertThat(response.totalElements()).isEqualTo(500);
        assertThat(response.content()).hasSize(20);
    }
}

