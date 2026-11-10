package com.businessops.dashboard.service;

import com.businessops.dashboard.dto.CategoryAnalyticsResponse;
import com.businessops.dashboard.dto.CategoryPerformanceItem;
import com.businessops.dashboard.dto.ClientPerformanceItem;
import com.businessops.dashboard.dto.ClientPerformanceResponse;
import com.businessops.dashboard.dto.CompletenessMetadata;
import com.businessops.dashboard.dto.FinancialSummaryResponse;
import com.businessops.dashboard.dto.FinancialTrendsResponse;
import com.businessops.dashboard.dto.TrendPeriodItem;
import com.businessops.dashboard.dto.WorkspaceResponse;
import com.businessops.dashboard.exception.AnalyticsProcessingLimitException;
import com.businessops.dashboard.exception.DatabaseOperationException;
import com.businessops.dashboard.exception.LimitSafeguardType;
import com.businessops.dashboard.model.FinancialSummaryAggregate;
import com.businessops.dashboard.model.TransactionStreamBatch;
import com.businessops.dashboard.model.TransactionStreamItem;
import com.businessops.dashboard.repository.TransactionRepository;
import com.businessops.dashboard.util.CurrencyUtils;
import com.businessops.dashboard.util.FinancialMathUtils;
import com.businessops.dashboard.validation.SupportedCurrencies;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service orchestrating financial analytics, time-series trends, category distributions,
 * and client revenue performance for Phase 5.
 *
 * <p><strong>Key Invariants:</strong>
 * <ul>
 *   <li>Multi-currency isolation: cross-currency arithmetic is strictly prohibited.</li>
 *   <li>64-bit integer monetary arithmetic: no binary floating-point operations.</li>
 *   <li>Two-tier processing limit safeguards: pre-flight check and authoritative 10,000 runtime streaming cap.</li>
 *   <li>Contiguous zero-filled trend periods for unbroken frontend charts.</li>
 * </ul>
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private static final long MAX_STREAM_DOCUMENTS = 10_000L;
    private static final int STREAM_BATCH_SIZE = 1_000;

    private final WorkspaceService workspaceService;
    private final TransactionRepository transactionRepository;

    public AnalyticsService(WorkspaceService workspaceService, TransactionRepository transactionRepository) {
        this.workspaceService = workspaceService;
        this.transactionRepository = transactionRepository;
    }

    // =========================================================================
    // 1. Executive Financial Summary
    // =========================================================================

    public FinancialSummaryResponse getFinancialSummary(
            String authenticatedUid,
            String workspaceId,
            String currency,
            Instant startInclusive,
            Instant endExclusive,
            boolean includePending
    ) {
        return getFinancialSummary(authenticatedUid, workspaceId, currency, startInclusive, endExclusive, "UTC", includePending);
    }

    public FinancialSummaryResponse getFinancialSummary(
            String authenticatedUid,
            String workspaceId,
            String currency,
            Instant startInclusive,
            Instant endExclusive,
            String reportingTimezone,
            boolean includePending
    ) {
        workspaceService.verifyWorkspaceAccess(authenticatedUid, workspaceId);
        WorkspaceResponse workspace = workspaceService.getWorkspaceById(authenticatedUid, workspaceId);

        validateInstantBoundaries(startInclusive, endExclusive);

        String targetCurrency = resolveCurrency(currency, workspace);
        int fractionDigits = CurrencyUtils.getFractionDigits(targetCurrency);
        String tz = (reportingTimezone != null && !reportingTimezone.isBlank()) ? reportingTimezone.trim() : "UTC";

        try {
            FinancialSummaryAggregate aggregate = transactionRepository.aggregateFinancialSummary(
                    workspaceId, targetCurrency, startInclusive, endExclusive, includePending
            );
            List<String> availableCurrencies = transactionRepository.discoverCurrencies(
                    workspaceId, startInclusive, endExclusive, includePending
            );

            long totalRevenueMinor = aggregate.totalRevenueMinor();
            long totalExpenseMinor = aggregate.totalExpenseMinor();
            long netProfitMinor = FinancialMathUtils.safeSubtract(totalRevenueMinor, totalExpenseMinor);

            String formattedRevenue = CurrencyUtils.formatAmount(totalRevenueMinor, targetCurrency);
            String formattedExpense = CurrencyUtils.formatAmount(totalExpenseMinor, targetCurrency);
            String formattedNetProfit = CurrencyUtils.formatAmount(netProfitMinor, targetCurrency);

            BigDecimal profitMarginPercentage = FinancialMathUtils.calculateProfitMargin(totalRevenueMinor, totalExpenseMinor);
            boolean isMarginComputable = FinancialMathUtils.isMarginComputable(totalRevenueMinor, totalExpenseMinor);

            long totalCompletedTransactions = aggregate.incomeTransactionCount() + aggregate.expenseTransactionCount();
            long incomeTransactionCount = aggregate.incomeTransactionCount();
            long expenseTransactionCount = aggregate.expenseTransactionCount();

            Long pendingIncomeMinor;
            String formattedPendingIncome;
            Long pendingExpenseMinor;
            String formattedPendingExpense;
            Long projectedNetProfitMinor;
            String formattedProjectedNetProfit;
            long pendingTransactionCount;

            if (includePending) {
                pendingIncomeMinor = aggregate.pendingIncomeMinor();
                formattedPendingIncome = CurrencyUtils.formatAmount(pendingIncomeMinor, targetCurrency);
                pendingExpenseMinor = aggregate.pendingExpenseMinor();
                formattedPendingExpense = CurrencyUtils.formatAmount(pendingExpenseMinor, targetCurrency);
                projectedNetProfitMinor = FinancialMathUtils.calculateProjectedNetProfit(netProfitMinor, pendingIncomeMinor, pendingExpenseMinor);
                formattedProjectedNetProfit = CurrencyUtils.formatAmount(projectedNetProfitMinor, targetCurrency);
                pendingTransactionCount = aggregate.pendingIncomeTransactionCount() + aggregate.pendingExpenseTransactionCount();
            } else {
                pendingIncomeMinor = 0L;
                formattedPendingIncome = CurrencyUtils.formatAmount(0L, targetCurrency);
                pendingExpenseMinor = 0L;
                formattedPendingExpense = CurrencyUtils.formatAmount(0L, targetCurrency);
                projectedNetProfitMinor = netProfitMinor;
                formattedProjectedNetProfit = formattedNetProfit;
                pendingTransactionCount = 0L;
            }

            long totalCount = totalCompletedTransactions + pendingTransactionCount;
            CompletenessMetadata completeness = CompletenessMetadata.complete(totalCount, totalCount);

            return new FinancialSummaryResponse(
                    workspaceId,
                    targetCurrency,
                    fractionDigits,
                    startInclusive,
                    endExclusive,
                    tz,
                    totalRevenueMinor,
                    formattedRevenue,
                    totalExpenseMinor,
                    formattedExpense,
                    netProfitMinor,
                    formattedNetProfit,
                    profitMarginPercentage,
                    isMarginComputable,
                    totalCompletedTransactions,
                    incomeTransactionCount,
                    expenseTransactionCount,
                    pendingIncomeMinor,
                    formattedPendingIncome,
                    pendingExpenseMinor,
                    formattedPendingExpense,
                    projectedNetProfitMinor,
                    formattedProjectedNetProfit,
                    pendingTransactionCount,
                    availableCurrencies,
                    completeness
            );
        } catch (ArithmeticException e) {
            log.error("Arithmetic overflow in getFinancialSummary for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Arithmetic overflow during financial analytics calculation", e);
        }
    }

    public FinancialSummaryResponse getFinancialSummary(
            String authenticatedUid,
            String workspaceId,
            String currency,
            String startDate,
            String endDate,
            String startInstant,
            String endInstant,
            String timezone,
            boolean includePending
    ) {
        ResolvedDateRange range = resolveDateRange(startDate, endDate, startInstant, endInstant, timezone);
        return getFinancialSummary(authenticatedUid, workspaceId, currency, range.startInclusive(), range.endExclusive(), range.reportingTimezone(), includePending);
    }

    // =========================================================================
    // 2. Financial Trends (Time-Series)
    // =========================================================================

    public FinancialTrendsResponse getFinancialTrends(
            String authenticatedUid,
            String workspaceId,
            String interval,
            String currency,
            Instant startInclusive,
            Instant endExclusive,
            boolean includePending
    ) {
        return getFinancialTrends(authenticatedUid, workspaceId, interval, currency, startInclusive, endExclusive, "UTC", includePending);
    }

    public FinancialTrendsResponse getFinancialTrends(
            String authenticatedUid,
            String workspaceId,
            String interval,
            String currency,
            Instant startInclusive,
            Instant endExclusive,
            String reportingTimezone,
            boolean includePending
    ) {
        workspaceService.verifyWorkspaceAccess(authenticatedUid, workspaceId);
        WorkspaceResponse workspace = workspaceService.getWorkspaceById(authenticatedUid, workspaceId);

        String normInterval = (interval == null || interval.isBlank()) ? "MONTHLY" : interval.trim().toUpperCase();
        if (!"MONTHLY".equals(normInterval) && !"YEARLY".equals(normInterval)) {
            throw new IllegalArgumentException("Invalid interval: '" + interval + "'. Expected 'MONTHLY' or 'YEARLY'");
        }

        validateInstantBoundaries(startInclusive, endExclusive);

        String targetCurrency = resolveCurrency(currency, workspace);
        int fractionDigits = CurrencyUtils.getFractionDigits(targetCurrency);
        String tz = (reportingTimezone != null && !reportingTimezone.isBlank()) ? reportingTimezone.trim() : "UTC";

        // Tier 1: Pre-flight Count Safeguard
        long preflightCount = transactionRepository.countEligibleTransactions(
                workspaceId, targetCurrency, startInclusive, endExclusive, includePending
        );
        if (preflightCount > MAX_STREAM_DOCUMENTS) {
            throw new AnalyticsProcessingLimitException(LimitSafeguardType.PREFLIGHT_COUNT_SAFEGUARD, 0L, preflightCount);
        }

        List<String> availableCurrencies = transactionRepository.discoverCurrencies(
                workspaceId, startInclusive, endExclusive, includePending
        );

        // Pre-populate contiguous zero-filled period buckets
        Map<String, TrendAccumulator> buckets = buildContiguousTrendBuckets(normInterval, startInclusive, endExclusive);

        try {
            long processedCount = 0L;

            // Stream COMPLETED transactions
            QueryDocumentSnapshot lastDoc = null;
            boolean hasMore = true;
            while (hasMore) {
                TransactionStreamBatch batch = transactionRepository.fetchStreamBatch(
                        workspaceId, targetCurrency, "COMPLETED", startInclusive, endExclusive, STREAM_BATCH_SIZE, lastDoc
                );
                if (batch == null || batch.items() == null || batch.items().isEmpty()) {
                    break;
                }
                for (TransactionStreamItem item : batch.items()) {
                    // Tier 2: Runtime Streaming Hard-Cap Safeguard
                    if (processedCount + 1 > MAX_STREAM_DOCUMENTS) {
                        throw new AnalyticsProcessingLimitException(LimitSafeguardType.RUNTIME_STREAMING_SAFEGUARD, MAX_STREAM_DOCUMENTS, null);
                    }
                    processedCount++;

                    accumulateTrendItem(buckets, item, normInterval, false);
                }
                lastDoc = batch.lastDocument();
                hasMore = batch.hasMore();
            }

            // Stream PENDING transactions if requested
            if (includePending) {
                lastDoc = null;
                hasMore = true;
                while (hasMore) {
                    TransactionStreamBatch batch = transactionRepository.fetchStreamBatch(
                            workspaceId, targetCurrency, "PENDING", startInclusive, endExclusive, STREAM_BATCH_SIZE, lastDoc
                    );
                    if (batch == null || batch.items() == null || batch.items().isEmpty()) {
                        break;
                    }
                    for (TransactionStreamItem item : batch.items()) {
                        // Tier 2: Runtime Streaming Hard-Cap Safeguard
                        if (processedCount + 1 > MAX_STREAM_DOCUMENTS) {
                            throw new AnalyticsProcessingLimitException(LimitSafeguardType.RUNTIME_STREAMING_SAFEGUARD, MAX_STREAM_DOCUMENTS, null);
                        }
                        processedCount++;

                        accumulateTrendItem(buckets, item, normInterval, true);
                    }
                    lastDoc = batch.lastDocument();
                    hasMore = batch.hasMore();
                }
            }

            // Transform buckets to response items
            List<TrendPeriodItem> periodItems = new ArrayList<>();
            for (TrendAccumulator acc : buckets.values()) {
                long netProfitMinor = FinancialMathUtils.safeSubtract(acc.revenueMinor, acc.expenseMinor);
                String formattedRevenue = CurrencyUtils.formatAmount(acc.revenueMinor, targetCurrency);
                String formattedExpense = CurrencyUtils.formatAmount(acc.expenseMinor, targetCurrency);
                String formattedNetProfit = CurrencyUtils.formatAmount(netProfitMinor, targetCurrency);
                BigDecimal profitMarginPercentage = FinancialMathUtils.calculateProfitMargin(acc.revenueMinor, acc.expenseMinor);

                Long pendingIncomeMinor;
                String formattedPendingIncome;
                Long pendingExpenseMinor;
                String formattedPendingExpense;
                Long projectedNetProfitMinor;
                String formattedProjectedNetProfit;
                long pendingTransactionCount;

                if (includePending) {
                    pendingIncomeMinor = acc.pendingIncomeMinor;
                    formattedPendingIncome = CurrencyUtils.formatAmount(pendingIncomeMinor, targetCurrency);
                    pendingExpenseMinor = acc.pendingExpenseMinor;
                    formattedPendingExpense = CurrencyUtils.formatAmount(pendingExpenseMinor, targetCurrency);
                    projectedNetProfitMinor = FinancialMathUtils.calculateProjectedNetProfit(netProfitMinor, pendingIncomeMinor, pendingExpenseMinor);
                    formattedProjectedNetProfit = CurrencyUtils.formatAmount(projectedNetProfitMinor, targetCurrency);
                    pendingTransactionCount = acc.pendingTransactionCount;
                } else {
                    pendingIncomeMinor = 0L;
                    formattedPendingIncome = CurrencyUtils.formatAmount(0L, targetCurrency);
                    pendingExpenseMinor = 0L;
                    formattedPendingExpense = CurrencyUtils.formatAmount(0L, targetCurrency);
                    projectedNetProfitMinor = netProfitMinor;
                    formattedProjectedNetProfit = formattedNetProfit;
                    pendingTransactionCount = 0L;
                }

                periodItems.add(new TrendPeriodItem(
                        acc.period,
                        acc.startInclusive,
                        acc.endExclusive,
                        acc.revenueMinor,
                        formattedRevenue,
                        acc.expenseMinor,
                        formattedExpense,
                        netProfitMinor,
                        formattedNetProfit,
                        profitMarginPercentage,
                        acc.transactionCount,
                        pendingIncomeMinor,
                        formattedPendingIncome,
                        pendingExpenseMinor,
                        formattedPendingExpense,
                        projectedNetProfitMinor,
                        formattedProjectedNetProfit,
                        pendingTransactionCount
                ));
            }

            CompletenessMetadata completeness = CompletenessMetadata.complete(processedCount, preflightCount);

            return new FinancialTrendsResponse(
                    workspaceId,
                    targetCurrency,
                    fractionDigits,
                    normInterval,
                    startInclusive,
                    endExclusive,
                    tz,
                    periodItems,
                    availableCurrencies,
                    completeness
            );
        } catch (ArithmeticException e) {
            log.error("Arithmetic overflow in getFinancialTrends for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Arithmetic overflow during financial trends calculation", e);
        }
    }

    public FinancialTrendsResponse getFinancialTrends(
            String authenticatedUid,
            String workspaceId,
            String interval,
            String currency,
            String startDate,
            String endDate,
            String startInstant,
            String endInstant,
            String timezone,
            boolean includePending
    ) {
        ResolvedDateRange range = resolveDateRange(startDate, endDate, startInstant, endInstant, timezone);
        return getFinancialTrends(authenticatedUid, workspaceId, interval, currency, range.startInclusive(), range.endExclusive(), range.reportingTimezone(), includePending);
    }

    // =========================================================================
    // 3. Category Distribution Analytics
    // =========================================================================

    public CategoryAnalyticsResponse getCategoryAnalytics(
            String authenticatedUid,
            String workspaceId,
            String type,
            String currency,
            Instant startInclusive,
            Instant endExclusive,
            boolean includePending
    ) {
        return getCategoryAnalytics(authenticatedUid, workspaceId, type, currency, startInclusive, endExclusive, "UTC", includePending);
    }

    public CategoryAnalyticsResponse getCategoryAnalytics(
            String authenticatedUid,
            String workspaceId,
            String type,
            String currency,
            Instant startInclusive,
            Instant endExclusive,
            String reportingTimezone,
            boolean includePending
    ) {
        workspaceService.verifyWorkspaceAccess(authenticatedUid, workspaceId);
        WorkspaceResponse workspace = workspaceService.getWorkspaceById(authenticatedUid, workspaceId);

        String normalizedFilterType = null;
        if (type != null && !type.isBlank()) {
            String trimmedUpper = type.trim().toUpperCase();
            if (!"INCOME".equals(trimmedUpper) && !"EXPENSE".equals(trimmedUpper)) {
                throw new IllegalArgumentException("Invalid transaction type: '" + type + "'. Expected 'INCOME' or 'EXPENSE'");
            }
            normalizedFilterType = trimmedUpper;
        }

        validateInstantBoundaries(startInclusive, endExclusive);

        String targetCurrency = resolveCurrency(currency, workspace);
        int fractionDigits = CurrencyUtils.getFractionDigits(targetCurrency);
        String tz = (reportingTimezone != null && !reportingTimezone.isBlank()) ? reportingTimezone.trim() : "UTC";

        // Tier 1: Pre-flight Count Safeguard
        long preflightCount = transactionRepository.countEligibleTransactions(
                workspaceId, targetCurrency, startInclusive, endExclusive, includePending
        );
        if (preflightCount > MAX_STREAM_DOCUMENTS) {
            throw new AnalyticsProcessingLimitException(LimitSafeguardType.PREFLIGHT_COUNT_SAFEGUARD, 0L, preflightCount);
        }

        List<String> availableCurrencies = transactionRepository.discoverCurrencies(
                workspaceId, startInclusive, endExclusive, includePending
        );

        Map<String, CategoryAccumulator> categoryMap = new LinkedHashMap<>();

        try {
            long totalIncomeMinor = 0L;
            long totalExpenseMinor = 0L;
            long pendingIncomeMinor = 0L;
            long pendingExpenseMinor = 0L;
            long processedCount = 0L;

            // Stream COMPLETED transactions
            QueryDocumentSnapshot lastDoc = null;
            boolean hasMore = true;
            while (hasMore) {
                TransactionStreamBatch batch = transactionRepository.fetchStreamBatch(
                        workspaceId, targetCurrency, "COMPLETED", startInclusive, endExclusive, STREAM_BATCH_SIZE, lastDoc
                );
                if (batch == null || batch.items() == null || batch.items().isEmpty()) {
                    break;
                }
                for (TransactionStreamItem item : batch.items()) {
                    // Tier 2: Runtime Streaming Hard-Cap Safeguard
                    if (processedCount + 1 > MAX_STREAM_DOCUMENTS) {
                        throw new AnalyticsProcessingLimitException(LimitSafeguardType.RUNTIME_STREAMING_SAFEGUARD, MAX_STREAM_DOCUMENTS, null);
                    }
                    processedCount++;

                    long amt = item.amountMinor() != null ? item.amountMinor() : 0L;
                    String itemType = item.type() != null ? item.type().toUpperCase() : "EXPENSE";

                    if ("INCOME".equals(itemType)) {
                        totalIncomeMinor = FinancialMathUtils.safeAdd(totalIncomeMinor, amt);
                    } else {
                        totalExpenseMinor = FinancialMathUtils.safeAdd(totalExpenseMinor, amt);
                    }

                    accumulateCategoryItem(categoryMap, item, false);
                }
                lastDoc = batch.lastDocument();
                hasMore = batch.hasMore();
            }

            // Stream PENDING transactions if requested
            if (includePending) {
                lastDoc = null;
                hasMore = true;
                while (hasMore) {
                    TransactionStreamBatch batch = transactionRepository.fetchStreamBatch(
                            workspaceId, targetCurrency, "PENDING", startInclusive, endExclusive, STREAM_BATCH_SIZE, lastDoc
                    );
                    if (batch == null || batch.items() == null || batch.items().isEmpty()) {
                        break;
                    }
                    for (TransactionStreamItem item : batch.items()) {
                        // Tier 2: Runtime Streaming Hard-Cap Safeguard
                        if (processedCount + 1 > MAX_STREAM_DOCUMENTS) {
                            throw new AnalyticsProcessingLimitException(LimitSafeguardType.RUNTIME_STREAMING_SAFEGUARD, MAX_STREAM_DOCUMENTS, null);
                        }
                        processedCount++;

                        long amt = item.amountMinor() != null ? item.amountMinor() : 0L;
                        String itemType = item.type() != null ? item.type().toUpperCase() : "EXPENSE";

                        if ("INCOME".equals(itemType)) {
                            pendingIncomeMinor = FinancialMathUtils.safeAdd(pendingIncomeMinor, amt);
                        } else {
                            pendingExpenseMinor = FinancialMathUtils.safeAdd(pendingExpenseMinor, amt);
                        }

                        accumulateCategoryItem(categoryMap, item, true);
                    }
                    lastDoc = batch.lastDocument();
                    hasMore = batch.hasMore();
                }
            }

            // Transform and compute percentages
            List<CategoryPerformanceItem> performanceItems = new ArrayList<>();
            for (CategoryAccumulator acc : categoryMap.values()) {
                if (normalizedFilterType != null && !normalizedFilterType.equalsIgnoreCase(acc.type)) {
                    continue; // Query filter applies only to items, preserving header totals
                }

                long denominator = "EXPENSE".equalsIgnoreCase(acc.type) ? totalExpenseMinor : totalIncomeMinor;
                BigDecimal percentageOfTotal = FinancialMathUtils.calculatePercentage(acc.totalAmountMinor, denominator);

                String formattedTotalAmount = CurrencyUtils.formatAmount(acc.totalAmountMinor, targetCurrency);
                Long pendingAmt;
                String formattedPendingAmt;
                long pendingTxnCount;

                if (includePending) {
                    pendingAmt = acc.pendingAmountMinor;
                    formattedPendingAmt = CurrencyUtils.formatAmount(pendingAmt, targetCurrency);
                    pendingTxnCount = acc.pendingTransactionCount;
                } else {
                    pendingAmt = 0L;
                    formattedPendingAmt = CurrencyUtils.formatAmount(0L, targetCurrency);
                    pendingTxnCount = 0L;
                }

                performanceItems.add(new CategoryPerformanceItem(
                        acc.categoryId,
                        acc.categoryName,
                        acc.categoryColor,
                        acc.type,
                        acc.totalAmountMinor,
                        formattedTotalAmount,
                        acc.transactionCount,
                        percentageOfTotal,
                        pendingAmt,
                        formattedPendingAmt,
                        pendingTxnCount
                ));
            }

            // Primary sort: totalAmountMinor DESC, secondary sort: categoryName ASC
            performanceItems.sort(
                    Comparator.comparing(CategoryPerformanceItem::totalAmountMinor, Comparator.reverseOrder())
                            .thenComparing(item -> item.categoryName() != null ? item.categoryName() : "", String.CASE_INSENSITIVE_ORDER)
            );

            String formattedTotalExpense = CurrencyUtils.formatAmount(totalExpenseMinor, targetCurrency);
            String formattedTotalIncome = CurrencyUtils.formatAmount(totalIncomeMinor, targetCurrency);
            String formattedPendingExpense = CurrencyUtils.formatAmount(pendingExpenseMinor, targetCurrency);
            String formattedPendingIncome = CurrencyUtils.formatAmount(pendingIncomeMinor, targetCurrency);

            CompletenessMetadata completeness = CompletenessMetadata.complete(processedCount, preflightCount);

            return new CategoryAnalyticsResponse(
                    workspaceId,
                    targetCurrency,
                    fractionDigits,
                    startInclusive,
                    endExclusive,
                    tz,
                    totalExpenseMinor,
                    formattedTotalExpense,
                    totalIncomeMinor,
                    formattedTotalIncome,
                    pendingExpenseMinor,
                    formattedPendingExpense,
                    pendingIncomeMinor,
                    formattedPendingIncome,
                    performanceItems,
                    availableCurrencies,
                    completeness
            );
        } catch (ArithmeticException e) {
            log.error("Arithmetic overflow in getCategoryAnalytics for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Arithmetic overflow during category analytics calculation", e);
        }
    }

    public CategoryAnalyticsResponse getCategoryAnalytics(
            String authenticatedUid,
            String workspaceId,
            String type,
            String currency,
            String startDate,
            String endDate,
            String startInstant,
            String endInstant,
            String timezone,
            boolean includePending
    ) {
        ResolvedDateRange range = resolveDateRange(startDate, endDate, startInstant, endInstant, timezone);
        return getCategoryAnalytics(authenticatedUid, workspaceId, type, currency, range.startInclusive(), range.endExclusive(), range.reportingTimezone(), includePending);
    }

    // =========================================================================
    // 4. Client Performance Analytics
    // =========================================================================

    public ClientPerformanceResponse getClientPerformance(
            String authenticatedUid,
            String workspaceId,
            String currency,
            Instant startInclusive,
            Instant endExclusive,
            boolean includePending,
            Integer limit
    ) {
        return getClientPerformance(authenticatedUid, workspaceId, currency, startInclusive, endExclusive, "UTC", includePending, limit);
    }

    public ClientPerformanceResponse getClientPerformance(
            String authenticatedUid,
            String workspaceId,
            String currency,
            Instant startInclusive,
            Instant endExclusive,
            String reportingTimezone,
            boolean includePending,
            Integer limit
    ) {
        workspaceService.verifyWorkspaceAccess(authenticatedUid, workspaceId);
        WorkspaceResponse workspace = workspaceService.getWorkspaceById(authenticatedUid, workspaceId);

        int maxClients = (limit != null) ? limit : 100;
        if (maxClients < 1 || maxClients > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100, got: " + maxClients);
        }

        validateInstantBoundaries(startInclusive, endExclusive);

        String targetCurrency = resolveCurrency(currency, workspace);
        int fractionDigits = CurrencyUtils.getFractionDigits(targetCurrency);
        String tz = (reportingTimezone != null && !reportingTimezone.isBlank()) ? reportingTimezone.trim() : "UTC";

        // Tier 1: Pre-flight Count Safeguard
        long preflightCount = transactionRepository.countEligibleTransactions(
                workspaceId, targetCurrency, startInclusive, endExclusive, includePending
        );
        if (preflightCount > MAX_STREAM_DOCUMENTS) {
            throw new AnalyticsProcessingLimitException(LimitSafeguardType.PREFLIGHT_COUNT_SAFEGUARD, 0L, preflightCount);
        }

        List<String> availableCurrencies = transactionRepository.discoverCurrencies(
                workspaceId, startInclusive, endExclusive, includePending
        );

        Map<String, ClientAccumulator> clientMap = new LinkedHashMap<>();

        try {
            long totalRealizedRevenueMinor = 0L;
            long unassignedClientRevenueMinor = 0L;
            long totalPendingRevenueMinor = 0L;
            long processedCount = 0L;

            // Stream COMPLETED transactions
            QueryDocumentSnapshot lastDoc = null;
            boolean hasMore = true;
            while (hasMore) {
                TransactionStreamBatch batch = transactionRepository.fetchStreamBatch(
                        workspaceId, targetCurrency, "COMPLETED", startInclusive, endExclusive, STREAM_BATCH_SIZE, lastDoc
                );
                if (batch == null || batch.items() == null || batch.items().isEmpty()) {
                    break;
                }
                for (TransactionStreamItem item : batch.items()) {
                    // Tier 2: Runtime Streaming Hard-Cap Safeguard
                    if (processedCount + 1 > MAX_STREAM_DOCUMENTS) {
                        throw new AnalyticsProcessingLimitException(LimitSafeguardType.RUNTIME_STREAMING_SAFEGUARD, MAX_STREAM_DOCUMENTS, null);
                    }
                    processedCount++;

                    // Client performance tracks revenue generation (INCOME only)
                    if (!"INCOME".equalsIgnoreCase(item.type())) {
                        continue;
                    }

                    long amt = item.amountMinor() != null ? item.amountMinor() : 0L;
                    totalRealizedRevenueMinor = FinancialMathUtils.safeAdd(totalRealizedRevenueMinor, amt);

                    String clientId = item.clientId() != null ? item.clientId().trim() : null;
                    if (clientId == null || clientId.isBlank()) {
                        unassignedClientRevenueMinor = FinancialMathUtils.safeAdd(unassignedClientRevenueMinor, amt);
                    } else {
                        ClientAccumulator acc = clientMap.computeIfAbsent(clientId, id -> {
                            String cName = (item.clientName() != null && !item.clientName().isBlank())
                                    ? item.clientName().trim()
                                    : "Unknown Client";
                            return new ClientAccumulator(id, cName);
                        });
                        acc.totalRevenueMinor = FinancialMathUtils.safeAdd(acc.totalRevenueMinor, amt);
                        acc.incomeTransactionCount++;
                        if (item.clientName() != null && !item.clientName().isBlank()) {
                            acc.clientName = item.clientName().trim();
                        }
                    }
                }
                lastDoc = batch.lastDocument();
                hasMore = batch.hasMore();
            }

            // Stream PENDING transactions if requested
            if (includePending) {
                lastDoc = null;
                hasMore = true;
                while (hasMore) {
                    TransactionStreamBatch batch = transactionRepository.fetchStreamBatch(
                            workspaceId, targetCurrency, "PENDING", startInclusive, endExclusive, STREAM_BATCH_SIZE, lastDoc
                    );
                    if (batch == null || batch.items() == null || batch.items().isEmpty()) {
                        break;
                    }
                    for (TransactionStreamItem item : batch.items()) {
                        // Tier 2: Runtime Streaming Hard-Cap Safeguard
                        if (processedCount + 1 > MAX_STREAM_DOCUMENTS) {
                            throw new AnalyticsProcessingLimitException(LimitSafeguardType.RUNTIME_STREAMING_SAFEGUARD, MAX_STREAM_DOCUMENTS, null);
                        }
                        processedCount++;

                        if (!"INCOME".equalsIgnoreCase(item.type())) {
                            continue;
                        }

                        long amt = item.amountMinor() != null ? item.amountMinor() : 0L;
                        totalPendingRevenueMinor = FinancialMathUtils.safeAdd(totalPendingRevenueMinor, amt);

                        String clientId = item.clientId() != null ? item.clientId().trim() : null;
                        if (clientId != null && !clientId.isBlank()) {
                            ClientAccumulator acc = clientMap.computeIfAbsent(clientId, id -> {
                                String cName = (item.clientName() != null && !item.clientName().isBlank())
                                        ? item.clientName().trim()
                                        : "Unknown Client";
                                return new ClientAccumulator(id, cName);
                            });
                            acc.pendingRevenueMinor = FinancialMathUtils.safeAdd(acc.pendingRevenueMinor, amt);
                            acc.pendingIncomeTransactionCount++;
                        }
                    }
                    lastDoc = batch.lastDocument();
                    hasMore = batch.hasMore();
                }
            }

            // Build client performance items
            List<ClientPerformanceItem> clientItems = new ArrayList<>();
            for (ClientAccumulator acc : clientMap.values()) {
                BigDecimal averageMinor = FinancialMathUtils.calculateAverageMinor(acc.totalRevenueMinor, acc.incomeTransactionCount);
                String formattedAverage = FinancialMathUtils.formatAverageAmount(averageMinor, targetCurrency);
                BigDecimal percentageOfTotal = FinancialMathUtils.calculatePercentage(acc.totalRevenueMinor, totalRealizedRevenueMinor);
                String formattedRevenue = CurrencyUtils.formatAmount(acc.totalRevenueMinor, targetCurrency);

                Long pendingRevenue;
                String formattedPendingRevenue;
                long pendingTxnCount;

                if (includePending) {
                    pendingRevenue = acc.pendingRevenueMinor;
                    formattedPendingRevenue = CurrencyUtils.formatAmount(pendingRevenue, targetCurrency);
                    pendingTxnCount = acc.pendingIncomeTransactionCount;
                } else {
                    pendingRevenue = 0L;
                    formattedPendingRevenue = CurrencyUtils.formatAmount(0L, targetCurrency);
                    pendingTxnCount = 0L;
                }

                clientItems.add(new ClientPerformanceItem(
                        acc.clientId,
                        acc.clientName,
                        acc.totalRevenueMinor,
                        formattedRevenue,
                        acc.incomeTransactionCount,
                        averageMinor,
                        formattedAverage,
                        percentageOfTotal,
                        pendingRevenue,
                        formattedPendingRevenue,
                        pendingTxnCount
                ));
            }

            // Primary sort: totalRevenueMinor DESC, secondary sort: clientName ASC
            clientItems.sort(
                    Comparator.comparing(ClientPerformanceItem::totalRevenueMinor, Comparator.reverseOrder())
                            .thenComparing(item -> item.clientName() != null ? item.clientName() : "", String.CASE_INSENSITIVE_ORDER)
            );

            // Apply limit (1..100)
            List<ClientPerformanceItem> limitedClients = clientItems.stream().limit(maxClients).toList();

            String formattedTotalRevenue = CurrencyUtils.formatAmount(totalRealizedRevenueMinor, targetCurrency);
            String formattedUnassignedRevenue = CurrencyUtils.formatAmount(unassignedClientRevenueMinor, targetCurrency);
            String formattedTotalPendingRevenue = CurrencyUtils.formatAmount(totalPendingRevenueMinor, targetCurrency);

            CompletenessMetadata completeness = CompletenessMetadata.complete(processedCount, preflightCount);

            return new ClientPerformanceResponse(
                    workspaceId,
                    targetCurrency,
                    fractionDigits,
                    startInclusive,
                    endExclusive,
                    tz,
                    totalRealizedRevenueMinor,
                    formattedTotalRevenue,
                    unassignedClientRevenueMinor,
                    formattedUnassignedRevenue,
                    totalPendingRevenueMinor,
                    formattedTotalPendingRevenue,
                    limitedClients,
                    availableCurrencies,
                    completeness
            );
        } catch (ArithmeticException e) {
            log.error("Arithmetic overflow in getClientPerformance for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Arithmetic overflow during client performance calculation", e);
        }
    }

    public ClientPerformanceResponse getClientPerformance(
            String authenticatedUid,
            String workspaceId,
            String currency,
            String startDate,
            String endDate,
            String startInstant,
            String endInstant,
            String timezone,
            boolean includePending,
            Integer limit
    ) {
        ResolvedDateRange range = resolveDateRange(startDate, endDate, startInstant, endInstant, timezone);
        return getClientPerformance(authenticatedUid, workspaceId, currency, range.startInclusive(), range.endExclusive(), range.reportingTimezone(), includePending, limit);
    }

    // =========================================================================
    // Helper Methods & Internal Accumulators
    // =========================================================================

    public record ResolvedDateRange(Instant startInclusive, Instant endExclusive, String reportingTimezone) {}

    public ResolvedDateRange resolveDateRange(String startDate, String endDate,
                                            String startInstant, String endInstant,
                                            String timezone) {
        boolean hasSetA = (startDate != null && !startDate.isBlank()) || (endDate != null && !endDate.isBlank());
        boolean hasSetB = (startInstant != null && !startInstant.isBlank()) || (endInstant != null && !endInstant.isBlank());

        if (hasSetA && hasSetB) {
            throw new IllegalArgumentException(
                    "Ambiguous date parameters: cannot mix calendar dates (startDate/endDate) with exact timestamps (startInstant/endInstant)"
            );
        }

        if (hasSetA) {
            if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
                throw new IllegalArgumentException("Both startDate and endDate must be provided when using calendar date parameters");
            }
            ZoneId zoneId = ZoneOffset.UTC;
            String tzStr = "UTC";
            if (timezone != null && !timezone.isBlank()) {
                try {
                    zoneId = ZoneId.of(timezone.trim());
                    tzStr = zoneId.getId();
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid timezone: '" + timezone + "'", e);
                }
            }

            LocalDate sDate;
            LocalDate eDate;
            try {
                sDate = LocalDate.parse(startDate.trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid startDate format: '" + startDate + "'. Expected YYYY-MM-DD", e);
            }
            try {
                eDate = LocalDate.parse(endDate.trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid endDate format: '" + endDate + "'. Expected YYYY-MM-DD", e);
            }

            Instant startInclusive = sDate.atStartOfDay(zoneId).toInstant();
            Instant endExclusive = eDate.plusDays(1).atStartOfDay(zoneId).toInstant();

            if (!startInclusive.isBefore(endExclusive)) {
                throw new IllegalArgumentException("startInclusive must be strictly before endExclusive");
            }

            return new ResolvedDateRange(startInclusive, endExclusive, tzStr);
        }

        if (hasSetB) {
            if (startInstant == null || startInstant.isBlank() || endInstant == null || endInstant.isBlank()) {
                throw new IllegalArgumentException("Both startInstant and endInstant must be provided when using exact timestamp parameters");
            }

            Instant startInclusive;
            Instant endExclusive;
            try {
                startInclusive = Instant.parse(startInstant.trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid startInstant format: '" + startInstant + "'. Expected ISO-8601 instant", e);
            }
            try {
                endExclusive = Instant.parse(endInstant.trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid endInstant format: '" + endInstant + "'. Expected ISO-8601 instant", e);
            }

            if (!startInclusive.isBefore(endExclusive)) {
                throw new IllegalArgumentException("startInclusive must be strictly before endExclusive");
            }

            return new ResolvedDateRange(startInclusive, endExclusive, "UTC");
        }

        // Default: current month in UTC
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        LocalDate sDate = now.withDayOfMonth(1);
        LocalDate eDate = now.withDayOfMonth(now.lengthOfMonth());
        Instant startInclusive = sDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endExclusive = eDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new ResolvedDateRange(startInclusive, endExclusive, "UTC");
    }

    private void validateInstantBoundaries(Instant startInclusive, Instant endExclusive) {
        if (startInclusive == null || endExclusive == null) {
            throw new IllegalArgumentException("Date boundaries cannot be null");
        }
        if (!startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("startInclusive must be strictly before endExclusive");
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

    private Map<String, TrendAccumulator> buildContiguousTrendBuckets(
            String interval,
            Instant startInclusive,
            Instant endExclusive
    ) {
        Map<String, TrendAccumulator> buckets = new LinkedHashMap<>();

        if ("YEARLY".equals(interval)) {
            Year startYear = Year.from(startInclusive.atZone(ZoneOffset.UTC));
            Year endYear = Year.from(endExclusive.minusNanos(1).atZone(ZoneOffset.UTC));
            Year current = startYear;
            while (!current.isAfter(endYear)) {
                String key = current.format(DateTimeFormatter.ofPattern("uuuu"));
                Instant bStart = current.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant bEnd = current.plusYears(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                buckets.put(key, new TrendAccumulator(key, bStart, bEnd));
                current = current.plusYears(1);
            }
        } else {
            YearMonth startYm = YearMonth.from(startInclusive.atZone(ZoneOffset.UTC));
            YearMonth endYm = YearMonth.from(endExclusive.minusNanos(1).atZone(ZoneOffset.UTC));
            YearMonth current = startYm;
            while (!current.isAfter(endYm)) {
                String key = current.format(DateTimeFormatter.ofPattern("uuuu-MM"));
                Instant bStart = current.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant bEnd = current.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                buckets.put(key, new TrendAccumulator(key, bStart, bEnd));
                current = current.plusMonths(1);
            }
        }

        return buckets;
    }

    private void accumulateTrendItem(
            Map<String, TrendAccumulator> buckets,
            TransactionStreamItem item,
            String interval,
            boolean isPending
    ) {
        if (item.transactionDate() == null) {
            return;
        }
        Instant itemInstant = Instant.ofEpochSecond(item.transactionDate().getSeconds(), item.transactionDate().getNanos());
        String key = "YEARLY".equals(interval)
                ? DateTimeFormatter.ofPattern("uuuu").withZone(ZoneOffset.UTC).format(itemInstant)
                : DateTimeFormatter.ofPattern("uuuu-MM").withZone(ZoneOffset.UTC).format(itemInstant);

        TrendAccumulator acc = buckets.get(key);
        if (acc == null) {
            // In case transaction boundary is slightly off pre-calculated intervals
            Instant bStart = itemInstant;
            Instant bEnd = itemInstant;
            acc = new TrendAccumulator(key, bStart, bEnd);
            buckets.put(key, acc);
        }

        long amt = item.amountMinor() != null ? item.amountMinor() : 0L;
        String type = item.type() != null ? item.type().toUpperCase() : "EXPENSE";

        if (isPending) {
            if ("INCOME".equals(type)) {
                acc.pendingIncomeMinor = FinancialMathUtils.safeAdd(acc.pendingIncomeMinor, amt);
                acc.pendingTransactionCount++;
            } else {
                acc.pendingExpenseMinor = FinancialMathUtils.safeAdd(acc.pendingExpenseMinor, amt);
                acc.pendingTransactionCount++;
            }
        } else {
            if ("INCOME".equals(type)) {
                acc.revenueMinor = FinancialMathUtils.safeAdd(acc.revenueMinor, amt);
                acc.transactionCount++;
            } else {
                acc.expenseMinor = FinancialMathUtils.safeAdd(acc.expenseMinor, amt);
                acc.transactionCount++;
            }
        }
    }

    private void accumulateCategoryItem(
            Map<String, CategoryAccumulator> categoryMap,
            TransactionStreamItem item,
            boolean isPending
    ) {
        String catId = item.categoryId() != null ? item.categoryId().trim() : null;
        String catName;
        String catColor;

        if (catId == null || catId.isBlank()) {
            catId = null;
            catName = "Uncategorized";
            catColor = "#9CA3AF";
        } else {
            catName = (item.categoryName() != null && !item.categoryName().isBlank())
                    ? item.categoryName().trim()
                    : "Unknown Category";
            catColor = (item.categoryColor() != null && !item.categoryColor().isBlank())
                    ? item.categoryColor().trim()
                    : "#6B7280";
        }

        String type = item.type() != null ? item.type().toUpperCase() : "EXPENSE";
        String groupKey = type + ":" + (catId != null ? catId : "UNCATEGORIZED");

        final String finalCatId = catId;
        final String finalCatName = catName;
        final String finalCatColor = catColor;

        CategoryAccumulator acc = categoryMap.computeIfAbsent(groupKey, k ->
                new CategoryAccumulator(finalCatId, finalCatName, finalCatColor, type)
        );

        long amt = item.amountMinor() != null ? item.amountMinor() : 0L;

        if (isPending) {
            acc.pendingAmountMinor = FinancialMathUtils.safeAdd(acc.pendingAmountMinor, amt);
            acc.pendingTransactionCount++;
        } else {
            acc.totalAmountMinor = FinancialMathUtils.safeAdd(acc.totalAmountMinor, amt);
            acc.transactionCount++;
        }
    }

    private static class TrendAccumulator {
        final String period;
        final Instant startInclusive;
        final Instant endExclusive;
        long revenueMinor = 0L;
        long expenseMinor = 0L;
        long transactionCount = 0L;
        long pendingIncomeMinor = 0L;
        long pendingExpenseMinor = 0L;
        long pendingTransactionCount = 0L;

        TrendAccumulator(String period, Instant startInclusive, Instant endExclusive) {
            this.period = period;
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
        }
    }

    private static class CategoryAccumulator {
        final String categoryId;
        final String categoryName;
        final String categoryColor;
        final String type;
        long totalAmountMinor = 0L;
        long transactionCount = 0L;
        long pendingAmountMinor = 0L;
        long pendingTransactionCount = 0L;

        CategoryAccumulator(String categoryId, String categoryName, String categoryColor, String type) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.categoryColor = categoryColor;
            this.type = type;
        }
    }

    private static class ClientAccumulator {
        final String clientId;
        String clientName;
        long totalRevenueMinor = 0L;
        long incomeTransactionCount = 0L;
        long pendingRevenueMinor = 0L;
        long pendingIncomeTransactionCount = 0L;

        ClientAccumulator(String clientId, String clientName) {
            this.clientId = clientId;
            this.clientName = clientName;
        }
    }
}

