package com.businessops.dashboard.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsDtoContractTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // ── COMPLETENESS METADATA ────────────────────────────────────────────────

    @Test
    @DisplayName("CompletenessMetadata: complete factory creates valid instance")
    void testCompletenessMetadata_Complete() {
        CompletenessMetadata metadata = CompletenessMetadata.complete(500L, 500L);

        assertThat(metadata.isComplete()).isTrue();
        assertThat(metadata.status()).isEqualTo("COMPLETE");
        assertThat(metadata.processedCount()).isEqualTo(500L);
        assertThat(metadata.totalEligibleCount()).isEqualTo(500L);
        assertThat(metadata.message()).isNull();
    }

    @Test
    @DisplayName("CompletenessMetadata: complete factory handles skew correctly")
    void testCompletenessMetadata_SkewDetected() {
        CompletenessMetadata metadata = CompletenessMetadata.complete(502L, 500L);

        assertThat(metadata.isComplete()).isTrue();
        assertThat(metadata.status()).isEqualTo("COMPLETE");
        assertThat(metadata.processedCount()).isEqualTo(502L);
        assertThat(metadata.totalEligibleCount()).isEqualTo(500L);
    }

    @Test
    @DisplayName("CompletenessMetadata: limitExceeded runtime abort preserves 10,000 processedCount and null totalEligibleCount")
    void testCompletenessMetadata_LimitExceeded() {
        CompletenessMetadata metadata = CompletenessMetadata.limitExceeded(
                10000L,
                null,
                "Processing limit of 10,000 documents exceeded. Stream aborted."
        );

        assertThat(metadata.isComplete()).isFalse();
        assertThat(metadata.status()).isEqualTo("PROCESSING_LIMIT_EXCEEDED");
        assertThat(metadata.processedCount()).isEqualTo(10000L);
        assertThat(metadata.totalEligibleCount()).isNull();
        assertThat(metadata.message()).contains("10,000");
    }

    // ── FINANCIAL SUMMARY RESPONSE (26 FIELDS) ───────────────────────────────

    @Test
    @DisplayName("FinancialSummaryResponse: verifies all 26 fields and serialization")
    void testFinancialSummaryResponse_Contract() throws Exception {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-02-01T00:00:00Z");
        CompletenessMetadata completeness = CompletenessMetadata.complete(100L, 100L);

        FinancialSummaryResponse response = new FinancialSummaryResponse(
                "ws-123",
                "USD",
                2,
                start,
                end,
                "UTC",
                // Realized
                100_000L,
                "1000.00",
                60_000L,
                "600.00",
                40_000L,
                "400.00",
                new BigDecimal("40.00"),
                true,
                10L,
                6L,
                4L,
                // Pipeline (includePending = false contract: 0L / 0.00 / projected = net)
                0L,
                "0.00",
                0L,
                "0.00",
                40_000L,
                "400.00",
                0L,
                List.of("USD", "EUR"),
                completeness
        );

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"workspaceId\":\"ws-123\"");
        assertThat(json).contains("\"totalRevenueMinor\":100000");
        assertThat(json).contains("\"pendingIncomeMinor\":0");
        assertThat(json).contains("\"projectedNetProfitMinor\":40000");
        assertThat(json).contains("\"isMarginComputable\":true");

        // Verify deserialization
        FinancialSummaryResponse deserialized = objectMapper.readValue(json, FinancialSummaryResponse.class);
        assertThat(deserialized).isEqualTo(response);
        assertThat(FinancialSummaryResponse.class.getRecordComponents()).hasSize(26);
    }

    // ── FINANCIAL TRENDS RESPONSE (10 FIELDS) & ITEM (18 FIELDS) ──────────────

    @Test
    @DisplayName("FinancialTrendsResponse: verifies Monthly/Yearly intervals and 18-field TrendPeriodItem")
    void testFinancialTrendsResponse_Contract() throws Exception {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-02-01T00:00:00Z");

        TrendPeriodItem item = new TrendPeriodItem(
                "2026-01",
                start,
                end,
                50_000L,
                "500.00",
                30_000L,
                "300.00",
                20_000L,
                "200.00",
                new BigDecimal("40.00"),
                5L,
                0L,
                "0.00",
                0L,
                "0.00",
                20_000L,
                "200.00",
                0L
        );

        FinancialTrendsResponse response = new FinancialTrendsResponse(
                "ws-123",
                "USD",
                2,
                "MONTHLY",
                start,
                end,
                "UTC",
                List.of(item),
                List.of("USD"),
                CompletenessMetadata.complete(5L, 5L)
        );

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"interval\":\"MONTHLY\"");
        assertThat(json).contains("\"period\":\"2026-01\"");
        assertThat(json).contains("\"revenueMinor\":50000");

        FinancialTrendsResponse deserialized = objectMapper.readValue(json, FinancialTrendsResponse.class);
        assertThat(deserialized).isEqualTo(response);
        assertThat(TrendPeriodItem.class.getRecordComponents()).hasSize(18);
        assertThat(FinancialTrendsResponse.class.getRecordComponents()).hasSize(10);
    }

    // ── CATEGORY ANALYTICS RESPONSE (17 FIELDS) & ITEM (11 FIELDS) ────────────

    @Test
    @DisplayName("CategoryAnalyticsResponse: verifies 17-field response and 11-field CategoryPerformanceItem")
    void testCategoryAnalyticsResponse_Contract() throws Exception {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-02-01T00:00:00Z");

        CategoryPerformanceItem item = new CategoryPerformanceItem(
                "cat-1",
                "Consulting",
                "#00FF00",
                "INCOME",
                75_000L,
                "750.00",
                3L,
                new BigDecimal("75.00"),
                0L,
                "0.00",
                0L
        );

        CategoryAnalyticsResponse response = new CategoryAnalyticsResponse(
                "ws-123",
                "USD",
                2,
                start,
                end,
                "UTC",
                // Realized Header Metrics (Always reports both income and expenses)
                25_000L,
                "250.00",
                100_000L,
                "1000.00",
                // Pipeline Header Metrics
                0L,
                "0.00",
                0L,
                "0.00",
                List.of(item),
                List.of("USD"),
                CompletenessMetadata.complete(4L, 4L)
        );

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"categoryName\":\"Consulting\"");
        assertThat(json).contains("\"categoryColor\":\"#00FF00\"");
        assertThat(json).contains("\"totalExpenseMinor\":25000");
        assertThat(json).contains("\"totalIncomeMinor\":100000");

        CategoryAnalyticsResponse deserialized = objectMapper.readValue(json, CategoryAnalyticsResponse.class);
        assertThat(deserialized).isEqualTo(response);
        assertThat(CategoryPerformanceItem.class.getRecordComponents()).hasSize(11);
        assertThat(CategoryAnalyticsResponse.class.getRecordComponents()).hasSize(17);
    }

    // ── CLIENT PERFORMANCE RESPONSE (15 FIELDS) & ITEM (11 FIELDS) ──────────

    @Test
    @DisplayName("ClientPerformanceResponse: verifies 15-field response and 11-field ClientPerformanceItem")
    void testClientPerformanceResponse_Contract() throws Exception {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-02-01T00:00:00Z");

        ClientPerformanceItem item = new ClientPerformanceItem(
                "client-1",
                "Acme Corp",
                90_000L,
                "900.00",
                3L,
                new BigDecimal("30000.00"),
                "300.00",
                new BigDecimal("90.00"),
                0L,
                "0.00",
                0L
        );

        ClientPerformanceResponse response = new ClientPerformanceResponse(
                "ws-123",
                "USD",
                2,
                start,
                end,
                "UTC",
                // Realized Header Metrics
                100_000L,
                "1000.00",
                10_000L,
                "100.00",
                // Pipeline Header Metrics
                0L,
                "0.00",
                List.of(item),
                List.of("USD"),
                CompletenessMetadata.complete(4L, 4L)
        );

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"clientName\":\"Acme Corp\"");
        assertThat(json).contains("\"averageTransactionValueMinor\":30000.00");
        assertThat(json).contains("\"unassignedClientRevenueMinor\":10000");

        ClientPerformanceResponse deserialized = objectMapper.readValue(json, ClientPerformanceResponse.class);
        assertThat(deserialized).isEqualTo(response);
        assertThat(ClientPerformanceItem.class.getRecordComponents()).hasSize(11);
        assertThat(ClientPerformanceResponse.class.getRecordComponents()).hasSize(15);
    }
}

