package com.businessops.dashboard.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportedCurrenciesTest {

    @ParameterizedTest
    @ValueSource(strings = {"INR", "USD", "EUR", "GBP", "AUD", "CAD", "SGD", "AED", "JPY"})
    @DisplayName("isValid: returns true for all approved application currencies")
    void testSupportedCurrencies_AcceptsApprovedList(String currency) {
        assertThat(SupportedCurrencies.isValid(currency)).isTrue();
        assertThat(SupportedCurrencies.normalizeOrDefault(currency)).isEqualTo(currency);
    }

    @ParameterizedTest
    @ValueSource(strings = {"KWD", "XYZ", "INVALID", "BHD", "OMR"})
    @DisplayName("isValid: returns false and rejects unapproved currencies (including KWD)")
    void testSupportedCurrencies_RejectsKwdAndOthers(String currency) {
        assertThat(SupportedCurrencies.isValid(currency)).isFalse();
        assertThatThrownBy(() -> SupportedCurrencies.normalizeOrDefault(currency))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported currency: '" + currency + "'");
    }

    @Test
    @DisplayName("normalizeOrDefault: defaults null or blank input to INR")
    void testSupportedCurrencies_DefaultsToInr() {
        assertThat(SupportedCurrencies.normalizeOrDefault(null)).isEqualTo("INR");
        assertThat(SupportedCurrencies.normalizeOrDefault("")).isEqualTo("INR");
        assertThat(SupportedCurrencies.normalizeOrDefault("   ")).isEqualTo("INR");
    }
}

