package com.businessops.dashboard.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialMathUtilsTest {

    // ── SAFE ARITHMETIC ──────────────────────────────────────────────────────

    @Test
    @DisplayName("safeAdd: correctly sums amounts within 64-bit limits")
    void testSafeAdd_Success() {
        assertThat(FinancialMathUtils.safeAdd(1_000_000L, 2_500_000L)).isEqualTo(3_500_000L);
    }

    @Test
    @DisplayName("safeAdd: throws ArithmeticException on integer overflow")
    void testSafeAdd_OverflowThrows() {
        assertThatThrownBy(() -> FinancialMathUtils.safeAdd(Long.MAX_VALUE, 1L))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("safeSubtract: correctly subtracts amounts within 64-bit limits")
    void testSafeSubtract_Success() {
        assertThat(FinancialMathUtils.safeSubtract(5_000_000L, 1_500_000L)).isEqualTo(3_500_000L);
    }

    @Test
    @DisplayName("safeSubtract: throws ArithmeticException on integer underflow")
    void testSafeSubtract_UnderflowThrows() {
        assertThatThrownBy(() -> FinancialMathUtils.safeSubtract(Long.MIN_VALUE, 1L))
                .isInstanceOf(ArithmeticException.class);
    }

    // ── AVERAGE CALCULATION ──────────────────────────────────────────────────

    @Test
    @DisplayName("calculateAverageMinor: exact division returns scale 2")
    void testCalculateAverage_Exact() {
        BigDecimal average = FinancialMathUtils.calculateAverageMinor(3000L, 3L);
        assertThat(average).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(average.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("calculateAverageMinor: repeating division applies HALF_UP rounding")
    void testCalculateAverage_RoundingHalfUp() {
        BigDecimal average = FinancialMathUtils.calculateAverageMinor(1000L, 3L);
        assertThat(average).isEqualByComparingTo(new BigDecimal("333.33"));
    }

    @Test
    @DisplayName("calculateAverageMinor: half-up boundary rounds up")
    void testCalculateAverage_HalfUpBoundary() {
        BigDecimal average = FinancialMathUtils.calculateAverageMinor(5L, 2L);
        assertThat(average).isEqualByComparingTo(new BigDecimal("2.50"));
    }

    @Test
    @DisplayName("calculateAverageMinor: returns 0.00 when count is 0 or negative")
    void testCalculateAverage_ZeroCount() {
        assertThat(FinancialMathUtils.calculateAverageMinor(1000L, 0L))
                .isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(FinancialMathUtils.calculateAverageMinor(1000L, -5L))
                .isEqualByComparingTo(new BigDecimal("0.00"));
    }

    // ── PERCENTAGE RATIO CALCULATION ─────────────────────────────────────────

    @Test
    @DisplayName("calculatePercentage: computes standard whole percentage")
    void testCalculatePercentage_Standard() {
        BigDecimal percentage = FinancialMathUtils.calculatePercentage(25L, 100L);
        assertThat(percentage).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    @DisplayName("calculatePercentage: repeating decimal applies HALF_UP rounding")
    void testCalculatePercentage_RepeatingDecimal() {
        BigDecimal percentage = FinancialMathUtils.calculatePercentage(1L, 3L);
        assertThat(percentage).isEqualByComparingTo(new BigDecimal("33.33"));
    }

    @Test
    @DisplayName("calculatePercentage: returns 0.00 when denominator is 0 or negative")
    void testCalculatePercentage_ZeroDenominator() {
        assertThat(FinancialMathUtils.calculatePercentage(100L, 0L))
                .isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(FinancialMathUtils.calculatePercentage(100L, -10L))
                .isEqualByComparingTo(new BigDecimal("0.00"));
    }

    // ── PROFIT MARGIN 4-CASE TRUTH TABLE ─────────────────────────────────────

    @Test
    @DisplayName("calculateProfitMargin Case 1: Positive revenue and profit")
    void testProfitMargin_PositiveProfit() {
        Long revenue = 100_000L;
        Long expense = 60_000L;

        assertThat(FinancialMathUtils.isMarginComputable(revenue, expense)).isTrue();
        BigDecimal margin = FinancialMathUtils.calculateProfitMargin(revenue, expense);
        assertThat(margin).isNotNull();
        assertThat(margin).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(margin.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("calculateProfitMargin Case 2: Positive revenue with net loss")
    void testProfitMargin_NetLoss() {
        Long revenue = 100_000L;
        Long expense = 150_000L;

        assertThat(FinancialMathUtils.isMarginComputable(revenue, expense)).isTrue();
        BigDecimal margin = FinancialMathUtils.calculateProfitMargin(revenue, expense);
        assertThat(margin).isNotNull();
        assertThat(margin).isEqualByComparingTo(new BigDecimal("-50.00"));
        assertThat(margin.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("calculateProfitMargin Case 3: Zero revenue and zero expense yields 0.00%")
    void testProfitMargin_ZeroRevenueZeroExpense() {
        Long revenue = 0L;
        Long expense = 0L;

        assertThat(FinancialMathUtils.isMarginComputable(revenue, expense)).isTrue();
        BigDecimal margin = FinancialMathUtils.calculateProfitMargin(revenue, expense);
        assertThat(margin).isNotNull();
        assertThat(margin).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(margin.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("calculateProfitMargin Case 4: Zero revenue with positive expense yields null / incomputable")
    void testProfitMargin_ZeroRevenuePositiveExpense() {
        Long revenue = 0L;
        Long expense = 50_000L;

        assertThat(FinancialMathUtils.isMarginComputable(revenue, expense)).isFalse();
        BigDecimal margin = FinancialMathUtils.calculateProfitMargin(revenue, expense);
        assertThat(margin).isNull();
    }

    // ── PROJECTED NET PROFIT ─────────────────────────────────────────────────

    @Test
    @DisplayName("calculateProjectedNetProfit: correctly incorporates pipeline income and expense")
    void testProjectedNetProfit_Success() {
        long projected = FinancialMathUtils.calculateProjectedNetProfit(1000L, 500L, 200L);
        assertThat(projected).isEqualTo(1300L);
    }

    @Test
    @DisplayName("calculateProjectedNetProfit: throws ArithmeticException on overflow")
    void testProjectedNetProfit_Overflow() {
        assertThatThrownBy(() -> FinancialMathUtils.calculateProjectedNetProfit(Long.MAX_VALUE, 1L, 0L))
                .isInstanceOf(ArithmeticException.class);
    }

    // ── CURRENCY FORMATTING ──────────────────────────────────────────────────

    @Test
    @DisplayName("formatAverageAmount: formats USD standard fractional minor units to major units")
    void testFormatAverage_USD_Standard() {
        BigDecimal averageMinor = new BigDecimal("1234.56");
        assertThat(FinancialMathUtils.formatAverageAmount(averageMinor, "USD")).isEqualTo("12.35");
    }

    @Test
    @DisplayName("formatAverageAmount: formats USD at HALF_UP boundary")
    void testFormatAverage_USD_RoundingBoundary() {
        BigDecimal averageMinor = new BigDecimal("1234.555");
        assertThat(FinancialMathUtils.formatAverageAmount(averageMinor, "USD")).isEqualTo("12.35");
    }

    @Test
    @DisplayName("formatAverageAmount: formats INR standard minor units")
    void testFormatAverage_INR_Standard() {
        BigDecimal averageMinor = new BigDecimal("250000.50");
        assertThat(FinancialMathUtils.formatAverageAmount(averageMinor, "INR")).isEqualTo("2500.01");
    }

    @Test
    @DisplayName("formatAverageAmount: formats JPY zero-decimal currency without decimal point")
    void testFormatAverage_JPY_ZeroDecimals() {
        BigDecimal averageMinor = new BigDecimal("1234.50");
        assertThat(FinancialMathUtils.formatAverageAmount(averageMinor, "JPY")).isEqualTo("1235");
    }

    @Test
    @DisplayName("formatAverageAmount: returns null when input amount is null")
    void testFormatAverage_Null() {
        assertThat(FinancialMathUtils.formatAverageAmount(null, "USD")).isNull();
    }
}

