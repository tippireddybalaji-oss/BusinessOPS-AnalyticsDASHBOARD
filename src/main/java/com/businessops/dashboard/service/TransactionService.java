package com.businessops.dashboard.service;

import com.businessops.dashboard.dto.PagedResponse;
import com.businessops.dashboard.dto.TransactionCreateRequest;
import com.businessops.dashboard.dto.TransactionResponse;
import com.businessops.dashboard.dto.TransactionUpdateRequest;
import com.businessops.dashboard.dto.WorkspaceResponse;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.model.PaymentMethod;
import com.businessops.dashboard.model.Transaction;
import com.businessops.dashboard.model.TransactionStatus;
import com.businessops.dashboard.model.TransactionType;
import com.businessops.dashboard.repository.TransactionRepository;
import com.businessops.dashboard.util.CurrencyUtils;
import com.businessops.dashboard.validation.SupportedCurrencies;
import com.google.cloud.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Service managing financial transactions, RBAC, business validations, and bounded search queries.
 *
 * <p><strong>Authorization Matrix:</strong>
 * <ul>
 *   <li>Read operations (list, get): {@code OWNER}, {@code ADMIN}, {@code MEMBER}.</li>
 *   <li>Write operations (create, update, delete): {@code OWNER}, {@code ADMIN}.</li>
 *   <li>Non-members: rejected with HTTP 403 Forbidden.</li>
 * </ul>
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private static final String SEARCH_TRUNCATION_WARNING =
            "Search results are bounded to the 500 most recent transactions. Matches from older transactions are omitted. To perform an exhaustive search, please supply a narrower date range (startDate / endDate).";

    private final WorkspaceService workspaceService;
    private final TransactionRepository transactionRepository;

    public TransactionService(WorkspaceService workspaceService, TransactionRepository transactionRepository) {
        this.workspaceService = workspaceService;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Creates a new financial transaction within a workspace.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param request creation payload
     * @return created transaction response
     */
    public TransactionResponse createTransaction(String authenticatedUid, String workspaceId, TransactionCreateRequest request) {
        workspaceService.verifyWorkspaceWriteAccess(authenticatedUid, workspaceId);
        WorkspaceResponse workspace = workspaceService.getWorkspaceById(authenticatedUid, workspaceId);

        TransactionType type = TransactionType.fromString(request.type());
        CurrencyUtils.validatePositiveAmount(request.amountMinor());

        String currency = resolveCurrency(request.currency(), workspace);
        PaymentMethod paymentMethod = PaymentMethod.fromString(request.paymentMethod());
        TransactionStatus status = TransactionStatus.fromString(request.status());

        Timestamp txnDate = request.transactionDate() != null
                ? Timestamp.ofTimeSecondsAndNanos(request.transactionDate().getEpochSecond(), request.transactionDate().getNano())
                : Timestamp.now();

        Transaction transaction = new Transaction();
        transaction.setWorkspaceId(workspaceId);
        transaction.setType(type.name());
        transaction.setAmountMinor(request.amountMinor());
        transaction.setCurrency(currency);
        transaction.setCategoryId(request.categoryId().trim());
        transaction.setClientId(request.clientId() != null && !request.clientId().isBlank() ? request.clientId().trim() : null);
        transaction.setDescription(request.description() != null ? request.description().trim() : null);
        transaction.setTransactionDate(txnDate);
        transaction.setPaymentMethod(paymentMethod.name());
        transaction.setStatus(status.name());
        transaction.setCreatedByUid(authenticatedUid);
        transaction.setUpdatedByUid(authenticatedUid);

        Transaction created = transactionRepository.createTransaction(workspaceId, transaction);
        log.info("Transaction created: id='{}', type='{}', amountMinor={} in workspace='{}'",
                created.getId(), created.getType(), created.getAmountMinor(), workspaceId);

        return TransactionResponse.from(created);
    }

    /**
     * Retrieves an individual transaction by workspace and transaction ID.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param transactionId target transaction ID
     * @return transaction response
     */
    public TransactionResponse getTransactionById(String authenticatedUid, String workspaceId, String transactionId) {
        workspaceService.verifyWorkspaceAccess(authenticatedUid, workspaceId);

        Transaction transaction = transactionRepository.findById(workspaceId, transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found with id: " + transactionId + " in workspace: " + workspaceId
                ));

        return TransactionResponse.from(transaction);
    }

    /**
     * Updates an existing financial transaction.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param transactionId target transaction ID
     * @param request update payload
     * @return updated transaction response
     */
    public TransactionResponse updateTransaction(String authenticatedUid, String workspaceId,
                                                String transactionId, TransactionUpdateRequest request) {
        workspaceService.verifyWorkspaceWriteAccess(authenticatedUid, workspaceId);
        WorkspaceResponse workspace = workspaceService.getWorkspaceById(authenticatedUid, workspaceId);

        TransactionType type = TransactionType.fromString(request.type());
        CurrencyUtils.validatePositiveAmount(request.amountMinor());

        String currency = resolveCurrency(request.currency(), workspace);
        PaymentMethod paymentMethod = PaymentMethod.fromString(request.paymentMethod());
        TransactionStatus status = TransactionStatus.fromString(request.status());

        Timestamp txnDate = request.transactionDate() != null
                ? Timestamp.ofTimeSecondsAndNanos(request.transactionDate().getEpochSecond(), request.transactionDate().getNano())
                : Timestamp.now();

        Transaction transaction = new Transaction();
        transaction.setId(transactionId);
        transaction.setWorkspaceId(workspaceId);
        transaction.setType(type.name());
        transaction.setAmountMinor(request.amountMinor());
        transaction.setCurrency(currency);
        transaction.setCategoryId(request.categoryId().trim());
        transaction.setClientId(request.clientId() != null && !request.clientId().isBlank() ? request.clientId().trim() : null);
        transaction.setDescription(request.description() != null ? request.description().trim() : null);
        transaction.setTransactionDate(txnDate);
        transaction.setPaymentMethod(paymentMethod.name());
        transaction.setStatus(status.name());
        transaction.setUpdatedByUid(authenticatedUid);

        Transaction updated = transactionRepository.updateTransaction(workspaceId, transaction);
        log.info("Transaction updated: id='{}' in workspace='{}' by user='{}'", transactionId, workspaceId, authenticatedUid);

        return TransactionResponse.from(updated);
    }

    /**
     * Deletes a transaction from the workspace.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param transactionId target transaction ID
     */
    public void deleteTransaction(String authenticatedUid, String workspaceId, String transactionId) {
        workspaceService.verifyWorkspaceWriteAccess(authenticatedUid, workspaceId);

        transactionRepository.findById(workspaceId, transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found with id: " + transactionId + " in workspace: " + workspaceId
                ));

        transactionRepository.deleteTransaction(workspaceId, transactionId);
        log.info("Transaction deleted: id='{}' in workspace='{}' by user='{}'", transactionId, workspaceId, authenticatedUid);
    }

    /**
     * Lists and filters transactions supporting structured Firestore queries and 501-sentinel bounded text search.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param type optional transaction type (INCOME or EXPENSE)
     * @param categoryId optional category ID
     * @param clientId optional client ID
     * @param startDate optional start date string
     * @param endDate optional end date string
     * @param search optional case-insensitive text search query
     * @param page zero-based page index
     * @param size page size (1..100)
     * @return paginated envelope with transaction items and search metadata
     */
    public PagedResponse<TransactionResponse> listTransactions(String authenticatedUid,
                                                              String workspaceId,
                                                              String type,
                                                              String categoryId,
                                                              String clientId,
                                                              String startDate,
                                                              String endDate,
                                                              String search,
                                                              int page,
                                                              int size) {
        workspaceService.verifyWorkspaceAccess(authenticatedUid, workspaceId);

        if (page < 0) {
            throw new IllegalArgumentException("Page index cannot be negative, got: " + page);
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100, got: " + size);
        }

        String normalizedType = null;
        if (type != null && !type.isBlank()) {
            normalizedType = TransactionType.fromString(type).name();
        }

        String normCategoryId = (categoryId != null && !categoryId.isBlank()) ? categoryId.trim() : null;
        String normClientId = (clientId != null && !clientId.isBlank()) ? clientId.trim() : null;

        Timestamp startTimestamp = parseDateParam(startDate, "startDate", false);
        Timestamp endTimestamp = parseDateParam(endDate, "endDate", true);

        if (startTimestamp != null && endTimestamp != null && startTimestamp.compareTo(endTimestamp) > 0) {
            throw new IllegalArgumentException("startDate cannot be after endDate");
        }

        if (search != null && !search.isBlank()) {
            // 501 Sentinel Document Strategy for bounded text search
            List<Transaction> scanned = transactionRepository.findTransactions(
                    workspaceId, normalizedType, normCategoryId, normClientId, startTimestamp, endTimestamp, 501
            );

            boolean isWindowTruncated = scanned.size() > 500;
            List<Transaction> candidates = isWindowTruncated ? scanned.subList(0, 500) : scanned;

            String searchLower = search.trim().toLowerCase();
            List<Transaction> matched = candidates.stream()
                    .filter(tx -> (tx.getDescription() != null && tx.getDescription().toLowerCase().contains(searchLower))
                            || (tx.getCategoryName() != null && tx.getCategoryName().toLowerCase().contains(searchLower))
                            || (tx.getClientName() != null && tx.getClientName().toLowerCase().contains(searchLower)))
                    .toList();

            String warning = isWindowTruncated ? SEARCH_TRUNCATION_WARNING : null;

            List<TransactionResponse> responses = matched.stream()
                    .map(TransactionResponse::from)
                    .toList();

            return PagedResponse.of(responses, page, size, isWindowTruncated, warning);
        } else {
            // Pure structured query without search truncation
            List<Transaction> transactions = transactionRepository.findTransactions(
                    workspaceId, normalizedType, normCategoryId, normClientId, startTimestamp, endTimestamp, null
            );

            List<TransactionResponse> responses = transactions.stream()
                    .map(TransactionResponse::from)
                    .toList();

            return PagedResponse.of(responses, page, size, false, null);
        }
    }

    private String resolveCurrency(String requestedCurrency, WorkspaceResponse workspace) {
        if (requestedCurrency != null && !requestedCurrency.isBlank()) {
            String normalized = requestedCurrency.trim().toUpperCase();
            if (!SupportedCurrencies.isValid(normalized)) {
                throw new IllegalArgumentException("Unsupported currency: '" + requestedCurrency + "'");
            }
            return normalized;
        }

        if (workspace != null && workspace.defaultCurrency() != null && !workspace.defaultCurrency().isBlank()) {
            return workspace.defaultCurrency().trim().toUpperCase();
        }

        return SupportedCurrencies.DEFAULT_CURRENCY;
    }

    private Timestamp parseDateParam(String dateStr, String paramName, boolean isEndOfDay) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        String trimmed = dateStr.trim();
        try {
            Instant instant = Instant.parse(trimmed);
            return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
        } catch (DateTimeParseException ignored) {
            try {
                LocalDate localDate = LocalDate.parse(trimmed);
                Instant instant = isEndOfDay
                        ? localDate.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC)
                        : localDate.atStartOfDay().toInstant(ZoneOffset.UTC);
                return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Invalid date format for parameter '" + paramName + "': '" + dateStr
                                + "'. Expected ISO-8601 format (e.g. 2026-01-01 or 2026-01-01T00:00:00Z)"
                );
            }
        }
    }
}

