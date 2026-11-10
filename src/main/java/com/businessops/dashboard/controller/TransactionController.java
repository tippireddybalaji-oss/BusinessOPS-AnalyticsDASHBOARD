package com.businessops.dashboard.controller;

import com.businessops.dashboard.dto.PagedResponse;
import com.businessops.dashboard.dto.TransactionCreateRequest;
import com.businessops.dashboard.dto.TransactionResponse;
import com.businessops.dashboard.dto.TransactionUpdateRequest;
import com.businessops.dashboard.security.SecurityUtils;
import com.businessops.dashboard.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for financial Transaction management within a specific workspace.
 *
 * <p>Base path: {@code /api/v1/workspaces/{workspaceId}/transactions}
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final SecurityUtils securityUtils;

    public TransactionController(TransactionService transactionService, SecurityUtils securityUtils) {
        this.transactionService = transactionService;
        this.securityUtils = securityUtils;
    }

    /**
     * Creates a new financial transaction within the workspace.
     *
     * @param workspaceId target workspace ID
     * @param request validated creation payload
     * @return 201 Created with created transaction details
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @PathVariable("workspaceId") String workspaceId,
            @Valid @RequestBody TransactionCreateRequest request) {
        String uid = securityUtils.getAuthenticatedUid();
        TransactionResponse response = transactionService.createTransaction(uid, workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves paginated transactions with optional filters and text search.
     *
     * @param workspaceId target workspace ID
     * @param type optional category type (INCOME or EXPENSE)
     * @param categoryId optional category filter
     * @param clientId optional client filter
     * @param startDate optional start date (inclusive)
     * @param endDate optional end date (inclusive)
     * @param search optional text search term
     * @param page zero-based page index (default: 0)
     * @param size page size (default: 20, min: 1, max: 100)
     * @return 200 OK with paginated transactions
     */
    @GetMapping
    public ResponseEntity<PagedResponse<TransactionResponse>> listTransactions(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "clientId", required = false) String clientId,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        String uid = securityUtils.getAuthenticatedUid();
        PagedResponse<TransactionResponse> response = transactionService.listTransactions(
                uid, workspaceId, type, categoryId, clientId, startDate, endDate, search, page, size
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves an individual transaction by ID.
     *
     * @param workspaceId target workspace ID
     * @param transactionId target transaction ID
     * @return 200 OK with transaction details
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("transactionId") String transactionId) {
        String uid = securityUtils.getAuthenticatedUid();
        TransactionResponse response = transactionService.getTransactionById(uid, workspaceId, transactionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing financial transaction.
     *
     * @param workspaceId target workspace ID
     * @param transactionId target transaction ID
     * @param request validated update payload
     * @return 200 OK with updated transaction details
     */
    @PutMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("transactionId") String transactionId,
            @Valid @RequestBody TransactionUpdateRequest request) {
        String uid = securityUtils.getAuthenticatedUid();
        TransactionResponse response = transactionService.updateTransaction(uid, workspaceId, transactionId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a financial transaction from the workspace.
     *
     * @param workspaceId target workspace ID
     * @param transactionId target transaction ID
     * @return 204 No Content
     */
    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("transactionId") String transactionId) {
        String uid = securityUtils.getAuthenticatedUid();
        transactionService.deleteTransaction(uid, workspaceId, transactionId);
        return ResponseEntity.noContent().build();
    }
}

