package com.businessops.dashboard.service;

import com.businessops.dashboard.dto.CategoryAnalyticsResponse;
import com.businessops.dashboard.dto.CategoryPerformanceItem;
import com.businessops.dashboard.dto.ClientPerformanceItem;
import com.businessops.dashboard.dto.ClientPerformanceResponse;
import com.businessops.dashboard.dto.FinancialSummaryResponse;
import com.businessops.dashboard.dto.FinancialTrendsResponse;
import com.businessops.dashboard.dto.TrendPeriodItem;
import com.businessops.dashboard.dto.WorkspaceResponse;
import com.businessops.dashboard.exception.AnalyticsProcessingLimitException;
import com.businessops.dashboard.exception.DatabaseOperationException;
import com.businessops.dashboard.exception.LimitSafeguardType;
import com.businessops.dashboard.exception.WorkspaceAccessDeniedException;
import com.businessops.dashboard.model.FinancialSummaryAggregate;
import com.businessops.dashboard.model.TransactionStreamBatch;
import com.businessops.dashboard.model.TransactionStreamItem;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AnalyticsService} verifying Phase 5 Milestone 3 contracts.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceTest {

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private TransactionRepository transactionRepository;

    private AnalyticsService analyticsService;

    private static final String UID = "user-123";
    private static final String WS_ID = "ws-test-1";
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-07-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(workspaceService, transactionRepository);

        WorkspaceResponse defaultWorkspace = new WorkspaceResponse(
                WS_ID, "Test Workspace", UID, "INR",
                Instant.now(), Instant.now(), WorkspaceRole.OWNER
        );
        when(workspaceService.getWorkspaceById(anyString(), eq(WS_ID))).thenReturn(defaultWorkspace);
        when(workspaceService.verifyWorkspaceAccess(anyString(), eq(WS_ID))).thenReturn(WorkspaceRole.OWNER);

        when(transactionRepository.discoverCurrencies(eq(WS_ID), any(), any(), anyBoolean()))
                .thenReturn(List.of("INR", "USD"));
    }

    // =========================================================================
    // 1. Financial Summary Tests
    // =========================================================================

    @Test
    @DisplayName("1. Financial Summary: Realized only (includePending = false) calculates net profit and margins")
    void testSummary_RealizedOnly_Success() {
        FinancialSummaryAggregate aggregate = new FinancialSummaryAggregate(
                2000000L, 10L, 500000L, 4L, 300000L, 2L, 100000L, 1L
        );
        when(transactionRepository.aggregateFinancialSummary(eq(WS_ID), eq("INR"), eq(START), eq(END), eq(false)))
                .thenReturn(aggregate);

        FinancialSummaryResponse response = analyticsService.getFinancialSummary(UID, WS_ID, "INR", START, END, false);

        assertThat(response.workspaceId()).isEqualTo(WS_ID);
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.fractionDigits()).isEqualTo(2);
        assertThat(response.totalRevenueMinor()).isEqualTo(2000000L);
        assertThat(response.formattedRevenue()).isEqualTo("20000.00");
        assertThat(response.totalExpenseMinor()).isEqualTo(500000L);
        assertThat(response.formattedExpense()).isEqualTo("5000.00");
        assertThat(response.netProfitMinor()).isEqualTo(1500000L);
        assertThat(response.formattedNetProfit()).isEqualTo("15000.00");
        assertThat(response.profitMarginPercentage()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(response.isMarginComputable()).isTrue();
        assertThat(response.totalCompletedTransactions()).isEqualTo(14L);
        assertThat(response.incomeTransactionCount()).isEqualTo(10L);
        assertThat(response.expenseTransactionCount()).isEqualTo(4L);

        // When includePending is false, pipeline metrics must be 0L and projected net equals realized net
        assertThat(response.pendingIncomeMinor()).isEqualTo(0L);
        assertThat(response.formattedPendingIncome()).isEqualTo("0.00");
        assertThat(response.pendingExpenseMinor()).isEqualTo(0L);
        assertThat(response.formattedPendingExpense()).isEqualTo("0.00");
        assertThat(response.projectedNetProfitMinor()).isEqualTo(1500000L);
        assertThat(response.formattedProjectedNetProfit()).isEqualTo("15000.00");
        assertThat(response.pendingTransactionCount()).isEqualTo(0L);

        assertThat(response.completeness().isComplete()).isTrue();
        assertThat(response.completeness().processedCount()).isEqualTo(14L);
        assertThat(response.completeness().totalEligibleCount()).isEqualTo(14L);
        assertThat(response.availableCurrencies()).containsExactly("INR", "USD");
    }

    @Test
    @DisplayName("2. Financial Summary: With pending (includePending = true) incorporates pending income & expense")
    void testSummary_WithPending_Success() {
        FinancialSummaryAggregate aggregate = new FinancialSummaryAggregate(
                2000000L, 10L, 500000L, 4L, 300000L, 2L, 100000L, 1L
        );
        when(transactionRepository.aggregateFinancialSummary(eq(WS_ID), eq("INR"), eq(START), eq(END), eq(true)))
                .thenReturn(aggregate);

        FinancialSummaryResponse response = analyticsService.getFinancialSummary(UID, WS_ID, "INR", START, END, true);

        assertThat(response.pendingIncomeMinor()).isEqualTo(300000L);
        assertThat(response.formattedPendingIncome()).isEqualTo("3000.00");
        assertThat(response.pendingExpenseMinor()).isEqualTo(100000L);
        assertThat(response.formattedPendingExpense()).isEqualTo("1000.00");
        // Projected: 1500000 + 300000 - 100000 = 1700000
        assertThat(response.projectedNetProfitMinor()).isEqualTo(1700000L);
        assertThat(response.formattedProjectedNetProfit()).isEqualTo("17000.00");
        assertThat(response.pendingTransactionCount()).isEqualTo(3L);
        assertThat(response.completeness().processedCount()).isEqualTo(17L);
    }

    @Test
    @DisplayName("3. Financial Summary: Zero revenue and zero expense yields 0.00% computable margin")
    void testSummary_ZeroRevenueZeroExpense() {
        when(transactionRepository.aggregateFinancialSummary(eq(WS_ID), eq("INR"), eq(START), eq(END), eq(false)))
                .thenReturn(FinancialSummaryAggregate.empty());

        FinancialSummaryResponse response = analyticsService.getFinancialSummary(UID, WS_ID, "INR", START, END, false);

        assertThat(response.totalRevenueMinor()).isEqualTo(0L);
        assertThat(response.totalExpenseMinor()).isEqualTo(0L);
        assertThat(response.netProfitMinor()).isEqualTo(0L);
        assertThat(response.profitMarginPercentage()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(response.isMarginComputable()).isTrue();
    }

    @Test
    @DisplayName("4. Financial Summary: Zero revenue with positive expense yields null incalculable margin")
    void testSummary_ZeroRevenueWithExpenses() {
        FinancialSummaryAggregate aggregate = new FinancialSummaryAggregate(
                0L, 0L, 450000L, 2L, 0L, 0L, 0L, 0L
        );
        when(transactionRepository.aggregateFinancialSummary(eq(WS_ID), eq("INR"), eq(START), eq(END), eq(false)))
                .thenReturn(aggregate);

        FinancialSummaryResponse response = analyticsService.getFinancialSummary(UID, WS_ID, "INR", START, END, false);

        assertThat(response.totalRevenueMinor()).isEqualTo(0L);
        assertThat(response.totalExpenseMinor()).isEqualTo(450000L);
        assertThat(response.netProfitMinor()).isEqualTo(-450000L);
        assertThat(response.profitMarginPercentage()).isNull();
        assertThat(response.isMarginComputable()).isFalse();
    }

    @Test
    @DisplayName("5. Financial Summary: Discovered currencies injected into response")
    void testSummary_CurrencyDiscoveryInjected() {
        when(transactionRepository.aggregateFinancialSummary(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(FinancialSummaryAggregate.empty());
        when(transactionRepository.discoverCurrencies(any(), any(), any(), anyBoolean()))
                .thenReturn(List.of("AED", "EUR", "INR", "USD"));

        FinancialSummaryResponse response = analyticsService.getFinancialSummary(UID, WS_ID, "USD", START, END, false);

        assertThat(response.availableCurrencies()).containsExactly("AED", "EUR", "INR", "USD");
    }

    @Test
    @DisplayName("6. Financial Summary: Null currency falls back to workspace default currency")
    void testSummary_WorkspaceDefaultCurrencyFallback() {
        WorkspaceResponse ws = new WorkspaceResponse(
                WS_ID, "Test Workspace", UID, "EUR",
                Instant.now(), Instant.now(), WorkspaceRole.OWNER
        );
        when(workspaceService.getWorkspaceById(anyString(), eq(WS_ID))).thenReturn(ws);
        when(transactionRepository.aggregateFinancialSummary(eq(WS_ID), eq("EUR"), any(), any(), anyBoolean()))
                .thenReturn(FinancialSummaryAggregate.empty());

        FinancialSummaryResponse response = analyticsService.getFinancialSummary(UID, WS_ID, null, START, END, false);

        assertThat(response.currency()).isEqualTo("EUR");
    }

    // =========================================================================
    // 2. Financial Trends Tests
    // =========================================================================

    @Test
    @DisplayName("7. Financial Trends: Monthly contiguous timeline generates unbroken zero-filled intervals")
    void testTrends_Monthly_ContiguousZeroFill() {
        when(transactionRepository.countEligibleTransactions(eq(WS_ID), eq("INR"), eq(START), eq(END), eq(false)))
                .thenReturn(2L);

        TransactionStreamItem janItem = new TransactionStreamItem(
                "tx-1", "INCOME", 100000L, "INR", "COMPLETED",
                Timestamp.ofTimeSecondsAndNanos(Instant.parse("2026-01-15T10:00:00Z").getEpochSecond(), 0),
                "cat-1", "Sales", "#10B981", "client-1", "Client A"
        );
        TransactionStreamItem junItem = new TransactionStreamItem(
                "tx-2", "EXPENSE", 40000L, "INR", "COMPLETED",
                Timestamp.ofTimeSecondsAndNanos(Instant.parse("2026-06-20T10:00:00Z").getEpochSecond(), 0),
                "cat-2", "Software", "#EF4444", null, null
        );

        when(transactionRepository.fetchStreamBatch(eq(WS_ID), eq("INR"), eq("COMPLETED"), eq(START), eq(END), anyInt(), isNull()))
                .thenReturn(new TransactionStreamBatch(List.of(janItem, junItem), null, false));

        FinancialTrendsResponse response = analyticsService.getFinancialTrends(UID, WS_ID, "MONTHLY", "INR", START, END, false);

        assertThat(response.interval()).isEqualTo("MONTHLY");
        assertThat(response.periods()).hasSize(6);

        // Period 1: 2026-01 (has income)
        TrendPeriodItem jan = response.periods().get(0);
        assertThat(jan.period()).isEqualTo("2026-01");
        assertThat(jan.revenueMinor()).isEqualTo(100000L);
        assertThat(jan.expenseMinor()).isEqualTo(0L);
        assertThat(jan.netProfitMinor()).isEqualTo(100000L);
        assertThat(jan.transactionCount()).isEqualTo(1L);

        // Periods 2-5: 2026-02 to 2026-05 (zero-filled contiguous intervals)
        for (int i = 1; i <= 4; i++) {
            TrendPeriodItem zeroPeriod = response.periods().get(i);
            assertThat(zeroPeriod.revenueMinor()).isEqualTo(0L);
            assertThat(zeroPeriod.expenseMinor()).isEqualTo(0L);
            assertThat(zeroPeriod.netProfitMinor()).isEqualTo(0L);
            assertThat(zeroPeriod.transactionCount()).isEqualTo(0L);
        }

        // Period 6: 2026-06 (has expense)
        TrendPeriodItem jun = response.periods().get(5);
        assertThat(jun.period()).isEqualTo("2026-06");
        assertThat(jun.revenueMinor()).isEqualTo(0L);
        assertThat(jun.expenseMinor()).isEqualTo(40000L);
        assertThat(jun.netProfitMinor()).isEqualTo(-40000L);
        assertThat(jun.transactionCount()).isEqualTo(1L);

        assertThat(response.completeness().isComplete()).isTrue();
        assertThat(response.completeness().processedCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("8. Financial Trends: Yearly contiguous timeline generates unbroken intervals")
    void testTrends_Yearly_ContiguousZeroFill() {
        Instant startYears = Instant.parse("2024-01-01T00:00:00Z");
        Instant endYears = Instant.parse("2027-01-01T00:00:00Z");

        when(transactionRepository.countEligibleTransactions(eq(WS_ID), eq("INR"), eq(startYears), eq(endYears), eq(false)))
                .thenReturn(1L);

        TransactionStreamItem item2024 = new TransactionStreamItem(
                "tx-1", "INCOME", 500000L, "INR", "COMPLETED",
                Timestamp.ofTimeSecondsAndNanos(Instant.parse("2024-05-10T00:00:00Z").getEpochSecond(), 0),
                null, null, null, null, null
        );

        when(transactionRepository.fetchStreamBatch(eq(WS_ID), eq("INR"), eq("COMPLETED"), eq(startYears), eq(endYears), anyInt(), isNull()))
                .thenReturn(new TransactionStreamBatch(List.of(item2024), null, false));

        FinancialTrendsResponse response = analyticsService.getFinancialTrends(UID, WS_ID, "YEARLY", "INR", startYears, endYears, false);

        assertThat(response.interval()).isEqualTo("YEARLY");
        assertThat(response.periods()).hasSize(3); // 2024, 2025, 2026
        assertThat(response.periods().get(0).period()).isEqualTo("2024");
        assertThat(response.periods().get(0).revenueMinor()).isEqualTo(500000L);
        assertThat(response.periods().get(1).period()).isEqualTo("2025");
        assertThat(response.periods().get(1).revenueMinor()).isEqualTo(0L);
        assertThat(response.periods().get(2).period()).isEqualTo("2026");
        assertThat(response.periods().get(2).revenueMinor()).isEqualTo(0L);
    }

    @Test
    @DisplayName("9. Financial Trends: Invalid interval throws IllegalArgumentException (400)")
    void testTrends_InvalidInterval_ThrowsIllegalArgument() {
        assertThatThrownBy(() -> analyticsService.getFinancialTrends(UID, WS_ID, "WEEKLY", "INR", START, END, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected 'MONTHLY' or 'YEARLY'");
    }

    @Test
    @DisplayName("10. Financial Trends: Tier 1 Pre-flight count > 10,000 aborts with PREFLIGHT_COUNT_SAFEGUARD (422)")
    void testTrends_PreflightLimitExceeded_Throws422() {
        when(transactionRepository.countEligibleTransactions(eq(WS_ID), eq("INR"), eq(START), eq(END), eq(false)))
                .thenReturn(10001L);

        assertThatThrownBy(() -> analyticsService.getFinancialTrends(UID, WS_ID, "MONTHLY", "INR", START, END, false))
                .isInstanceOf(AnalyticsProcessingLimitException.class)
                .satisfies(ex -> {
                    AnalyticsProcessingLimitException aple = (AnalyticsProcessingLimitException) ex;
                    assertThat(aple.getSafeguardType()).isEqualTo(LimitSafeguardType.PREFLIGHT_COUNT_SAFEGUARD);
                    assertThat(aple.getProcessedCount()).isEqualTo(0L);
                    assertThat(aple.getTotalEligibleCount()).isEqualTo(10001L);
                    assertThat(aple.getMaxAllowedCount()).isEqualTo(10000);
                });

        verify(transactionRepository, never()).fetchStreamBatch(any(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("11. Financial Trends: Tier 2 Runtime streaming document 10,001 aborts with RUNTIME_STREAMING_SAFEGUARD (422)")
    void testTrends_RuntimeLimitExceeded_Throws422() {
        when(transactionRepository.countEligibleTransactions(eq(WS_ID), eq("INR"), eq(START), eq(END), eq(false)))
                .thenReturn(10000L); // Preflight passes

        List<TransactionStreamItem> batchItems = new ArrayList<>();
        Timestamp ts = Timestamp.ofTimeSecondsAndNanos(Instant.parse("2026-01-10T00:00:00Z").getEpochSecond(), 0);
        for (int i = 0; i < 1000; i++) {
            batchItems.add(new TransactionStreamItem("tx-" + i, "INCOME", 100L, "INR", "COMPLETED", ts, null, null, null, null, null));
        }

        // Return 10 full batches + 1 item on batch 11 = 10,001 items
        TransactionStreamBatch batch1000 = new TransactionStreamBatch(batchItems, null, true);
        List<TransactionStreamItem> extraItem = List.of(new TransactionStreamItem("tx-10001", "INCOME", 100L, "INR", "COMPLETED", ts, null, null, null, null, null));
        TransactionStreamBatch batchExtra = new TransactionStreamBatch(extraItem, null, false);

        when(transactionRepository.fetchStreamBatch(eq(WS_ID), eq("INR"), eq("COMPLETED"), eq(START), eq(END), anyInt(), any()))
                .thenReturn(batch1000, batch1000, batch1000, batch1000, batch1000, batch1000, batch1000, batch1000, batch1000, batch1000, batchExtra);

        assertThatThrownBy(() -> analyticsService.getFinancialTrends(UID, WS_ID, "MONTHLY", "INR", START, END, false))
                .isInstanceOf(AnalyticsProcessingLimitException.class)
                .satisfies(ex -> {
                    AnalyticsProcessingLimitException aple = (AnalyticsProcessingLimitException) ex;
                    assertThat(aple.getSafeguardType()).isEqualTo(LimitSafeguardType.RUNTIME_STREAMING_SAFEGUARD);
                    assertThat(aple.getProcessedCount()).isEqualTo(10000L);
                    assertThat(aple.getTotalEligibleCount()).isNull();
                });
    }

    // =========================================================================
    // 3. Category Analytics Tests
    // =========================================================================

    @Test
    @DisplayName("12. Category Analytics: Combined header totals preserved across entire reporting window")
    void testCategories_CombinedHeaderTotalsPreserved() {
        when(transactionRepository.countEligibleTransactions(eq(WS_ID), eq("INR"), eq(START), eq(END), eq(false)))
                .thenReturn(2L);

        TransactionStreamItem income = new TransactionStreamItem(
                "tx-1", "INCOME", 500000L, "INR", "COMPLETED",
                Timestamp.now(), "cat-inc", "Consulting", "#10B981", null, null
        );
        TransactionStreamItem expense = new TransactionStreamItem(
                "tx-2", "EXPENSE", 200000L, "INR", "COMPLETED",
                Timestamp.now(), "cat-exp", "Rent", "#EF4444", null, null
        );

        when(transactionRepository.fetchStreamBatch(eq(WS_ID), eq("INR"), eq("COMPLETED"), eq(START), eq(END), anyInt(), isNull()))
                .thenReturn(new TransactionStreamBatch(List.of(income, expense), null, false));

        CategoryAnalyticsResponse response = analyticsService.getCategoryAnalytics(UID, WS_ID, null, "INR", START, END, false);

        assertThat(response.totalIncomeMinor()).isEqualTo(500000L);
        assertThat(response.formattedTotalIncome()).isEqualTo("5000.00");
        assertThat(response.totalExpenseMinor()).isEqualTo(200000L);
        assertThat(response.formattedTotalExpense()).isEqualTo("2000.00");
        assertThat(response.categories()).hasSize(2);
    }

    @Test
    @DisplayName("13. Category Analytics: Type filter 'INCOME' filters items list while preserving header totals")
    void testCategories_TypeFilter_IncomeOnly() {
        when(transactionRepository.countEligibleTransactions(eq(WS_ID), eq("INR"), eq(START), eq(END), eq(false)))
                .thenReturn(2L);

        TransactionStreamItem income = new TransactionStreamItem(
                "tx-1", "INCOME", 500000L, "INR", "COMPLETED",
                Timestamp.now(), "cat-inc", "Consulting", "#10B981", null, null
        );
        TransactionStreamItem expense = new TransactionStreamItem(
                "tx-2", "EXPENSE", 200000L, "INR", "COMPLETED",
                Timestamp.now(), "cat-exp", "Rent", "#EF4444", null, null
        );

        when(transactionRepository.fetchStreamBatch(eq(WS_ID), eq("INR"), eq("COMPLETED"), eq(START), eq(END), anyInt(), isNull()))
                .thenReturn(new TransactionStreamBatch(List.of(income, expense), null, false));

        CategoryAnalyticsResponse response = analyticsService.getCategoryAnalytics(UID, WS_ID, "INCOME", "INR", START, END, false);

        // Headers still reflect both income and expense
        assertThat(response.totalIncomeMinor()).isEqualTo(500000L);
        assertThat(response.totalExpenseMinor()).isEqualTo(200000L);

        // But categories list contains only the matching type
        assertThat(response.categories()).hasSize(1);
        assertThat(response.categories().get(0).categoryId()).isEqualTo("cat-inc");
        assertThat(response.categories().get(0).type()).isEqualTo("INCOME");
    }

    @Test
    @DisplayName("14. Category Analytics: Percentage calculations evaluate against respective type denominators")
    void testCategories_PercentageCalculations() {
        when(transactionRepository.countEligibleTransactions(eq(WS_ID), eq("INR"), eq(START), eq(END), eq(false)))
                .thenReturn(2L);

        TransactionStreamItem exp1 = new TransactionStreamItem(
                "tx-1", "EXPENSE", 750000L, "INR", "COMPLETED",
                Timestamp.now(), "c-1", "Payroll", "#111", null, null
        );
        TransactionStreamItem exp2 = new TransactionStreamItem(
                "tx-2", "EXPENSE", 250000L, "INR", "COMPLETED",
                Timestamp.now(), "c-2", "Cloud", "#222", null, null
        );

        when(transactionRepository.fetchStreamBatch(eq(WS_ID), eq("INR"), eq("COMPLETED"), eq(START), eq(END), anyInt(), isNull()))
                .thenReturn(new TransactionStreamBatch(List.of(exp1, exp2), null, false));

        CategoryAnalyticsResponse response = analyticsService.getCategoryAnalytics(UID, WS_ID, "EXPENSE", "INR", START, END, false);

        assertThat(response.categories().get(0).percentageOfTotal()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(response.categories().get(1).percentageOfTotal()).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    @DisplayName("15. Category Analytics: Null categoryId groups under 'Uncategorized'")
    void testCategories_UncategorizedGrouping() {
        when(transactionRepository.countEligibleTransactions(eq(WS_ID), eq("INR"), eq(START), eq(END), eq(false)))
                .thenReturn(1L);

        TransactionStreamItem item = new TransactionStreamItem(
                "tx-1", "EXPENSE", 100000L, "INR", "COMPLETED",
                Timestamp.now(), null, null, null, null, null
        );

        when(transactionRepository.fetchStreamBatch(eq(WS_ID), eq("INR"), eq("COMPLETED"), eq(START), eq(END), anyInt(), isNull()))
                .thenReturn(new TransactionStreamBatch(List.of(item), null, false));

        CategoryAnalyticsResponse response = analyticsService.getCategoryAnalytics(UID, WS_ID, null, "INR", START, END, false);

        assertThat(response.categories()).hasSize(1);
        CategoryPerformanceItem cat = response.categories().get(0);
        assertThat(cat.categoryId()).isNull();
        assertThat(cat.categoryName()).isEqualTo("Uncategorized");
        assertThat(cat.categoryColor()).isEqualTo("#9CA3AF");
    }

    @Test
    @DisplayName("16. Category Analytics: Items sorted totalAmountMinor DESC with name tie-breaking")
    void testCategories_DescSortAndTieBreak() {
        when(transactionRepository.countEligibleTransactions(eq(WS_ID), eq("INR"), eq(START), eq(END), eq(false)))
                .thenReturn(3L);

        TransactionStreamItem itemA = new TransactionStreamItem("t1", "EXPENSE", 10000L, "INR", "COMPLETED", Timestamp.now(), "c1", "Alpha", "#1", null, null);
        TransactionStreamItem itemZ = new TransactionStreamItem("t2", "EXPENSE", 50000L, "INR", "COMPLETED", Timestamp.now(), "c2", "Zulu", "#2", null, null);
        TransactionStreamItem itemB = new TransactionStreamItem("t3", "EXPENSE", 50000L, "INR", "COMPLETED", Timestamp.now(), "c3", "Bravo", "#3", null, null);

        when(transactionRepository.fetchStreamBatch(eq(WS_ID), eq("INR"), eq("COMPLETED"), eq(START), eq(END), anyInt(), isNull()))
                .thenReturn(new TransactionStreamBatch(List.of(itemA, itemZ, itemB), null, false));

        CategoryAnalyticsResponse response = analyticsService.getCategoryAnalytics(UID, WS_ID, null, "INR", START, END, false);

        assertThat(response.categories()).extracting(CategoryPerformanceItem::categoryName)
                .containsExactly("Bravo", "Zulu", "Alpha");
    }

    // =========================================================================
    // 4. Client Performance Tests
    // =========================================================================

    @Test
    @DisplayName("17. Client Performance: Revenue rankings and average transaction values calculated accurately")
    void testClients_RevenueRankingsAndAverages() {
        when(transactionRepository.countEligibleTransactions(eq(WS_ID), eq("USD"), eq(START), eq(END), eq(false)))
                .thenReturn(3L);

        TransactionStreamItem c1_1 = new TransactionStreamItem("t1", "INCOME", 10000L, "USD", "COMPLETED", Timestamp.now(), null, null, null, "cl-1", "Acme Corp");
        TransactionStreamItem c1_2 = new TransactionStreamItem("t2", "INCOME", 20000L, "USD", "COMPLETED", Timestamp.now(), null, null, null, "cl-1", "Acme Corp");
        TransactionStreamItem c2_1 = new TransactionStreamItem("t3", "INCOME", 15000L, "USD", "COMPLETED", Timestamp.now(), null, null, null, "cl-2", "Beta LLC");

        when(transactionRepository.fetchStreamBatch(eq(WS_ID), eq("USD"), eq("COMPLETED"), eq(START), eq(END), anyInt(), isNull()))
                .thenReturn(new TransactionStreamBatch(List.of(c1_1, c1_2, c2_1), null, false));

        ClientPerformanceResponse response = analyticsService.getClientPerformance(UID, WS_ID, "USD", START, END, false, 10);

        assertThat(response.totalRealizedRevenueMinor()).isEqualTo(45000L);
        assertThat(response.formattedTotalRevenue()).isEqualTo("450.00");
        assertThat(response.clients()).hasSize(2);

        ClientPerformanceItem topClient = response.clients().get(0);
        assertThat(topClient.clientId()).isEqualTo("cl-1");
        assertThat(topClient.totalRevenueMinor()).isEqualTo(30000L);
        assertThat(topClient.incomeTransactionCount()).isEqualTo(2L);
        // Average: 30000 / 2 = 15000.00 minor units -> $150.00
        assertThat(topClient.averageTransactionValueMinor()).isEqualByComparingTo(new BigDecimal("15000.00"));
        assertThat(topClient.formattedAverageValue()).isEqualTo("150.00");
        // 30000 / 45000 * 100 = 66.67%
        assertThat(topClient.percentageOfTotalRevenue()).isEqualByComparingTo(new BigDecimal("66.67"));
    }

    @Test
    @DisplayName("18. Client Performance: Unassigned client transactions separated from ranking list")
    void testClients_UnassignedClientRevenueSeparation() {
        when(transactionRepository.countEligibleTransactions(eq(WS_ID), eq("INR"), eq(START), eq(END), eq(false)))
                .thenReturn(2L);

        TransactionStreamItem assigned = new TransactionStreamItem("t1", "INCOME", 400000L, "INR", "COMPLETED", Timestamp.now(), null, null, null, "cl-1", "Client One");
        TransactionStreamItem unassigned = new TransactionStreamItem("t2", "INCOME", 100000L, "INR", "COMPLETED", Timestamp.now(), null, null, null, null, null);

        when(transactionRepository.fetchStreamBatch(eq(WS_ID), eq("INR"), eq("COMPLETED"), eq(START), eq(END), anyInt(), isNull()))
                .thenReturn(new TransactionStreamBatch(List.of(assigned, unassigned), null, false));

        ClientPerformanceResponse response = analyticsService.getClientPerformance(UID, WS_ID, "INR", START, END, false, 10);

        assertThat(response.totalRealizedRevenueMinor()).isEqualTo(500000L);
        assertThat(response.unassignedClientRevenueMinor()).isEqualTo(100000L);
        assertThat(response.formattedUnassignedRevenue()).isEqualTo("1000.00");
        // Unassigned item is not added as a client in the list
        assertThat(response.clients()).hasSize(1);
        assertThat(response.clients().get(0).clientId()).isEqualTo("cl-1");
    }

    @Test
    @DisplayName("19. Client Performance: EXPENSE transactions are excluded from client rankings")
    void testClients_ExpenseTransactionsExcluded() {
        when(transactionRepository.countEligibleTransactions(eq(WS_ID), eq("INR"), eq(START), eq(END), eq(false)))
                .thenReturn(2L);

        TransactionStreamItem inc = new TransactionStreamItem("t1", "INCOME", 300000L, "INR", "COMPLETED", Timestamp.now(), null, null, null, "cl-1", "Client One");
        TransactionStreamItem exp = new TransactionStreamItem("t2", "EXPENSE", 150000L, "INR", "COMPLETED", Timestamp.now(), null, null, null, "cl-1", "Client One");

        when(transactionRepository.fetchStreamBatch(eq(WS_ID), eq("INR"), eq("COMPLETED"), eq(START), eq(END), anyInt(), isNull()))
                .thenReturn(new TransactionStreamBatch(List.of(inc, exp), null, false));

        ClientPerformanceResponse response = analyticsService.getClientPerformance(UID, WS_ID, "INR", START, END, false, 10);

        assertThat(response.totalRealizedRevenueMinor()).isEqualTo(300000L);
        assertThat(response.clients()).hasSize(1);
        assertThat(response.clients().get(0).totalRevenueMinor()).isEqualTo(300000L);
        assertThat(response.clients().get(0).incomeTransactionCount()).isEqualTo(1L);
    }

    // =========================================================================
    // 5. Security, Date Boundaries & Error Translation
    // =========================================================================

    @Test
    @DisplayName("20. Security: Access denied exception propagated when caller lacks membership")
    void testAccessDenied_ThrowsWorkspaceAccessDeniedException() {
        doThrow(new WorkspaceAccessDeniedException("Access denied"))
                .when(workspaceService).verifyWorkspaceAccess(eq(UID), eq("ws-unauthorized"));

        assertThatThrownBy(() -> analyticsService.getFinancialSummary(UID, "ws-unauthorized", "INR", START, END, false))
                .isInstanceOf(WorkspaceAccessDeniedException.class);
    }

    @Test
    @DisplayName("21. Date Boundaries: startInclusive not before endExclusive throws IllegalArgumentException (400)")
    void testInvalidDateRange_StartDateAfterEndDate_ThrowsIllegalArgument() {
        Instant invertedStart = Instant.parse("2026-08-01T00:00:00Z");
        Instant invertedEnd = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> analyticsService.getFinancialSummary(UID, WS_ID, "INR", invertedStart, invertedEnd, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startInclusive must be strictly before endExclusive");
    }

    @Test
    @DisplayName("22. Error Translation: Arithmetic overflow translates into DatabaseOperationException (500)")
    void testArithmeticOverflow_TranslatesToDatabaseOperationException() {
        FinancialSummaryAggregate overflowAggregate = new FinancialSummaryAggregate(
                Long.MAX_VALUE, 1L, -100L, 1L, 0L, 0L, 0L, 0L
        );
        when(transactionRepository.aggregateFinancialSummary(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(overflowAggregate);

        assertThatThrownBy(() -> analyticsService.getFinancialSummary(UID, WS_ID, "INR", START, END, false))
                .isInstanceOf(DatabaseOperationException.class)
                .hasMessageContaining("Arithmetic overflow");
    }

    @Test
    @DisplayName("23. Date Range Resolution: Supports calendar dates and exact timestamps, rejects ambiguous mix")
    void testDateResolution_CalendarDatesAndExactTimestamps() {
        // Parameter Set A: Calendar dates
        var rangeA = analyticsService.resolveDateRange("2026-01-01", "2026-01-31", null, null, "Asia/Kolkata");
        assertThat(rangeA.reportingTimezone()).isEqualTo("Asia/Kolkata");
        assertThat(rangeA.startInclusive()).isBefore(rangeA.endExclusive());

        // Parameter Set B: Exact timestamps
        var rangeB = analyticsService.resolveDateRange(null, null, "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", null);
        assertThat(rangeB.reportingTimezone()).isEqualTo("UTC");
        assertThat(rangeB.startInclusive()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(rangeB.endExclusive()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));

        // Ambiguous mix: Set A + Set B
        assertThatThrownBy(() -> analyticsService.resolveDateRange("2026-01-01", null, "2026-01-01T00:00:00Z", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ambiguous date parameters");
    }

    @Test
    @DisplayName("24. Client Performance: Limit parameter validated and restricts returned list")
    void testClientPerformance_LimitParameter() {
        assertThatThrownBy(() -> analyticsService.getClientPerformance(UID, WS_ID, "INR", START, END, false, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit must be between 1 and 100");

        assertThatThrownBy(() -> analyticsService.getClientPerformance(UID, WS_ID, "INR", START, END, false, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit must be between 1 and 100");

        TransactionStreamItem c1 = new TransactionStreamItem("t1", "INCOME", 100L, "INR", "COMPLETED", Timestamp.now(), null, null, null, "cl-1", "Client 1");
        TransactionStreamItem c2 = new TransactionStreamItem("t2", "INCOME", 200L, "INR", "COMPLETED", Timestamp.now(), null, null, null, "cl-2", "Client 2");

        when(transactionRepository.fetchStreamBatch(eq(WS_ID), eq("INR"), eq("COMPLETED"), eq(START), eq(END), anyInt(), isNull()))
                .thenReturn(new TransactionStreamBatch(List.of(c1, c2), null, false));
        when(transactionRepository.countEligibleTransactions(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(2L);

        ClientPerformanceResponse res = analyticsService.getClientPerformance(UID, WS_ID, "INR", START, END, false, 1);
        assertThat(res.clients()).hasSize(1);
    }

    @Test
    @DisplayName("25. Safeguard Exception: Getters and message verification")
    void testException_GettersAndLimitDetails() {
        AnalyticsProcessingLimitException ex = new AnalyticsProcessingLimitException(
                LimitSafeguardType.PREFLIGHT_COUNT_SAFEGUARD,
                0L,
                15000L
        );

        assertThat(ex.getSafeguardType()).isEqualTo(LimitSafeguardType.PREFLIGHT_COUNT_SAFEGUARD);
        assertThat(ex.getProcessedCount()).isEqualTo(0L);
        assertThat(ex.getTotalEligibleCount()).isEqualTo(15000L);
        assertThat(ex.getMaxAllowedCount()).isEqualTo(10000);
        assertThat(ex.getMessage()).contains("exceeds the dimensional breakdown limit of 10,000 transactions");
    }
}

