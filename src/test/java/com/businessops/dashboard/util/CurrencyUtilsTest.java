package com.businessops.dashboard.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CurrencyUtils}.
 *
 * <p>Verifies ISO-4217 fraction digits, zero-decimal currency (JPY) handling,
 * precision boundaries, overflow detection, zero/negative rejection, and deterministic formatting.
 */
class CurrencyUtilsTest {

    // ── FRACTION DIGITS ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getFractionDigits: INR defaults and returns 2 decimal places")
    void getFractionDigits_inr_returns2() {
        assertThat(CurrencyUtils.getFractionDigits("INR")).isEqualTo(2);
        assertThat(CurrencyUtils.getFractionDigits(null)).isEqualTo(2);
        assertThat(CurrencyUtils.getFractionDigits("")).isEqualTo(2);
        assertThat(CurrencyUtils.getFractionDigits("  ")).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "GBP", "AUD", "CAD", "SGD", "AED"})
    @DisplayName("getFractionDigits: standard global currencies return 2 decimal places")
    void getFractionDigits_standardCurrencies_returns2(String currency) {
        assertThat(CurrencyUtils.getFractionDigits(currency)).isEqualTo(2);
    }

    @Test
    @DisplayName("getFractionDigits: JPY returns 0 decimal places (zero-decimal currency)")
    void getFractionDigits_jpy_returns0() {
        assertThat(CurrencyUtils.getFractionDigits("JPY")).isEqualTo(0);
        assertThat(CurrencyUtils.getFractionDigits("jpy")).isEqualTo(0);
    }

    @Test
    @DisplayName("getFractionDigits: unsupported currency throws IllegalArgumentException")
    void getFractionDigits_unsupported_throwsException() {
        assertThatThrownBy(() -> CurrencyUtils.getFractionDigits("XYZ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported currency: 'XYZ'");
    }

    // ── FORMAT AMOUNT ────────────────────────────────────────────────────────

    @Test
    @DisplayName("formatAmount: formats INR paise correctly with 2 decimal places")
    void formatAmount_inr_formatsCorrectly() {
        assertThat(CurrencyUtils.formatAmount(2500000L, "INR")).isEqualTo("25000.00");
        assertThat(CurrencyUtils.formatAmount(250050L, "INR")).isEqualTo("2500.50");
        assertThat(CurrencyUtils.formatAmount(100L, "INR")).isEqualTo("1.00");
        assertThat(CurrencyUtils.formatAmount(1L, "INR")).isEqualTo("0.01");
        assertThat(CurrencyUtils.formatAmount(0L, "INR")).isEqualTo("0.00");
        assertThat(CurrencyUtils.formatAmount(-5000L, "INR")).isEqualTo("-50.00");
    }

    @Test
    @DisplayName("formatAmount: formats JPY yen correctly with 0 decimal places")
    void formatAmount_jpy_formatsCorrectly() {
        assertThat(CurrencyUtils.formatAmount(5000L, "JPY")).isEqualTo("5000");
        assertThat(CurrencyUtils.formatAmount(1L, "JPY")).isEqualTo("1");
        assertThat(CurrencyUtils.formatAmount(0L, "JPY")).isEqualTo("0");
        assertThat(CurrencyUtils.formatAmount(1000000L, "JPY")).isEqualTo("1000000");
    }

    @Test
    @DisplayName("formatAmount: defaults to INR when currency is null or blank")
    void formatAmount_nullCurrency_defaultsToInr() {
        assertThat(CurrencyUtils.formatAmount(12500L, null)).isEqualTo("125.00");
        assertThat(CurrencyUtils.formatAmount(12500L, "  ")).isEqualTo("125.00");
    }

    @Test
    @DisplayName("formatAmount: unsupported currency throws IllegalArgumentException")
    void formatAmount_unsupportedCurrency_throwsException() {
        assertThatThrownBy(() -> CurrencyUtils.formatAmount(100L, "INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported currency");
    }

    // ── PARSE TO MINOR ───────────────────────────────────────────────────────

    @Test
    @DisplayName("parseToMinor: parses standard INR decimal string to paise")
    void parseToMinor_inr_parsesSuccessfully() {
        assertThat(CurrencyUtils.parseToMinor("25000.00", "INR")).isEqualTo(2500000L);
        assertThat(CurrencyUtils.parseToMinor("2500.50", "INR")).isEqualTo(250050L);
        assertThat(CurrencyUtils.parseToMinor("2500.5", "INR")).isEqualTo(250050L);
        assertThat(CurrencyUtils.parseToMinor("2500", "INR")).isEqualTo(250000L);
        assertThat(CurrencyUtils.parseToMinor("0.01", "INR")).isEqualTo(1L);
    }

    @Test
    @DisplayName("parseToMinor: parses JPY integer amount to minor units")
    void parseToMinor_jpy_parsesSuccessfully() {
        assertThat(CurrencyUtils.parseToMinor("5000", "JPY")).isEqualTo(5000L);
        assertThat(CurrencyUtils.parseToMinor("1", "JPY")).isEqualTo(1L);
    }

    @Test
    @DisplayName("parseToMinor: JPY rejects any fractional decimal places")
    void parseToMinor_jpy_rejectsDecimals() {
        assertThatThrownBy(() -> CurrencyUtils.parseToMinor("5000.5", "JPY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Excess decimal precision")
                .hasMessageContaining("allows at most 0 decimal places");

        assertThatThrownBy(() -> CurrencyUtils.parseToMinor("5000.00", "JPY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Excess decimal precision");
    }

    @Test
    @DisplayName("parseToMinor: INR rejects excess decimal precision (> 2 decimal places)")
    void parseToMinor_inr_rejectsExcessPrecision() {
        assertThatThrownBy(() -> CurrencyUtils.parseToMinor("25.123", "INR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Excess decimal precision")
                .hasMessageContaining("allows at most 2 decimal places");

        assertThatThrownBy(() -> CurrencyUtils.parseToMinor("100.9999", "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Excess decimal precision");
    }

    @Test
    @DisplayName("parseToMinor: rejects zero and negative values")
    void parseToMinor_rejectsZeroAndNegative() {
        assertThatThrownBy(() -> CurrencyUtils.parseToMinor("0", "INR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction amount must be strictly positive (> 0)");

        assertThatThrownBy(() -> CurrencyUtils.parseToMinor("0.00", "INR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction amount must be strictly positive (> 0)");

        assertThatThrownBy(() -> CurrencyUtils.parseToMinor("-10.50", "INR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction amount must be strictly positive (> 0)");
    }

    @Test
    @DisplayName("parseToMinor: rejects blank, null, or malformed input")
    void parseToMinor_rejectsMalformedInput() {
        assertThatThrownBy(() -> CurrencyUtils.parseToMinor(null, "INR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Monetary amount cannot be blank");

        assertThatThrownBy(() -> CurrencyUtils.parseToMinor("   ", "INR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Monetary amount cannot be blank");

        assertThatThrownBy(() -> CurrencyUtils.parseToMinor("abc", "INR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid decimal monetary amount");
    }

    @Test
    @DisplayName("parseToMinor: detects 64-bit integer numeric overflow")
    void parseToMinor_detectsOverflow() {
        // Number well beyond Long.MAX_VALUE (9.22 * 10^18)
        String hugeAmount = "99999999999999999999999999999.00";
        assertThatThrownBy(() -> CurrencyUtils.parseToMinor(hugeAmount, "INR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric overflow");
    }

    // ── VALIDATE POSITIVE AMOUNT ─────────────────────────────────────────────

    @Test
    @DisplayName("validatePositiveAmount: accepts positive long, rejects null, zero and negative")
    void validatePositiveAmount_validatesCorrectly() {
        // Valid
        CurrencyUtils.validatePositiveAmount(1L);
        CurrencyUtils.validatePositiveAmount(250000L);

        // Null
        assertThatThrownBy(() -> CurrencyUtils.validatePositiveAmount(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");

        // Zero
        assertThatThrownBy(() -> CurrencyUtils.validatePositiveAmount(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be greater than zero");

        // Negative
        assertThatThrownBy(() -> CurrencyUtils.validatePositiveAmount(-50L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be greater than zero");
    }
}

