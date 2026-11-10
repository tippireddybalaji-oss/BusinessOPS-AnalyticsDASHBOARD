package com.businessops.dashboard.model;

import com.businessops.dashboard.dto.*;
import com.google.cloud.Timestamp;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit and Bean Validation tests for Phase 4 models, enums, and DTOs.
 */
class ModelAndDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ── TRANSACTION TYPE ENUM ────────────────────────────────────────────────

    @Test
    @DisplayName("TransactionType: parses valid values case-insensitively")
    void transactionType_valid_parsesCorrectly() {
        assertThat(TransactionType.fromString("INCOME")).isEqualTo(TransactionType.INCOME);
        assertThat(TransactionType.fromString("income")).isEqualTo(TransactionType.INCOME);
        assertThat(TransactionType.fromString("EXPENSE")).isEqualTo(TransactionType.EXPENSE);
        assertThat(TransactionType.fromString("expense ")).isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    @DisplayName("TransactionType: rejects invalid, blank, or null values")
    void transactionType_invalid_throwsException() {
        assertThatThrownBy(() -> TransactionType.fromString(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be blank");

        assertThatThrownBy(() -> TransactionType.fromString("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be blank");

        assertThatThrownBy(() -> TransactionType.fromString("TRANSFER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid transaction type");
    }

    // ── PAYMENT METHOD ENUM ──────────────────────────────────────────────────

    @Test
    @DisplayName("PaymentMethod: parses valid values and defaults to BANK_TRANSFER if null/blank")
    void paymentMethod_validAndDefaults() {
        assertThat(PaymentMethod.fromString("UPI")).isEqualTo(PaymentMethod.UPI);
        assertThat(PaymentMethod.fromString("card")).isEqualTo(PaymentMethod.CARD);
        assertThat(PaymentMethod.fromString("CASH")).isEqualTo(PaymentMethod.CASH);
        assertThat(PaymentMethod.fromString(null)).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(PaymentMethod.fromString("  ")).isEqualTo(PaymentMethod.BANK_TRANSFER);
    }

    @Test
    @DisplayName("PaymentMethod: rejects invalid values")
    void paymentMethod_invalid_throwsException() {
        assertThatThrownBy(() -> PaymentMethod.fromString("BITCOIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid payment method");
    }

    // ── TRANSACTION STATUS ENUM ──────────────────────────────────────────────

    @Test
    @DisplayName("TransactionStatus: parses valid values and defaults to COMPLETED if null/blank")
    void transactionStatus_validAndDefaults() {
        assertThat(TransactionStatus.fromString("PENDING")).isEqualTo(TransactionStatus.PENDING);
        assertThat(TransactionStatus.fromString("cancelled")).isEqualTo(TransactionStatus.CANCELLED);
        assertThat(TransactionStatus.fromString("COMPLETED")).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(TransactionStatus.fromString(null)).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(TransactionStatus.fromString("")).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("TransactionStatus: rejects invalid values")
    void transactionStatus_invalid_throwsException() {
        assertThatThrownBy(() -> TransactionStatus.fromString("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid transaction status");
    }

    // ── CATEGORY DTO VALIDATION ──────────────────────────────────────────────

    @Test
    @DisplayName("CategoryCreateRequest: valid payload passes validation")
    void categoryCreateRequest_valid_passesValidation() {
        CategoryCreateRequest request = new CategoryCreateRequest(
                "Software & SaaS", "EXPENSE", "#4F46E5", "laptop"
        );
        Set<ConstraintViolation<CategoryCreateRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("CategoryCreateRequest: blank name or invalid color fails validation")
    void categoryCreateRequest_invalid_failsValidation() {
        // Blank name
        CategoryCreateRequest badName = new CategoryCreateRequest(
                " ", "EXPENSE", "#4F46E5", "laptop"
        );
        assertThat(validator.validate(badName)).isNotEmpty();

        // Name too short (< 2 chars)
        CategoryCreateRequest shortName = new CategoryCreateRequest(
                "A", "EXPENSE", "#4F46E5", "laptop"
        );
        assertThat(validator.validate(shortName)).isNotEmpty();

        // Invalid hex color
        CategoryCreateRequest badColor = new CategoryCreateRequest(
                "Valid Name", "EXPENSE", "blue", "laptop"
        );
        assertThat(validator.validate(badColor)).isNotEmpty();

        // Hex color without hash or invalid length
        CategoryCreateRequest badHex = new CategoryCreateRequest(
                "Valid Name", "EXPENSE", "#FFF", "laptop"
        );
        assertThat(validator.validate(badHex)).isNotEmpty();
    }

    @Test
    @DisplayName("CategoryUpdateRequest: valid payload passes, blank name fails")
    void categoryUpdateRequest_validation() {
        CategoryUpdateRequest valid = new CategoryUpdateRequest("Updated Name", "#10B981", "tag");
        assertThat(validator.validate(valid)).isEmpty();

        CategoryUpdateRequest blankName = new CategoryUpdateRequest("   ", "#10B981", "tag");
        assertThat(validator.validate(blankName)).isNotEmpty();
    }

    // ── TRANSACTION DTO VALIDATION ───────────────────────────────────────────

    @Test
    @DisplayName("TransactionCreateRequest: valid payload passes validation")
    void transactionCreateRequest_valid_passesValidation() {
        TransactionCreateRequest request = new TransactionCreateRequest(
                "INCOME", 2500000L, "INR", "cat-123", "client-456",
                "Q4 Retainer invoice", Instant.now(), "BANK_TRANSFER", "COMPLETED"
        );
        Set<ConstraintViolation<TransactionCreateRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("TransactionCreateRequest: null, zero, and negative amounts fail @Positive validation")
    void transactionCreateRequest_invalidAmount_failsValidation() {
        // Null amount
        TransactionCreateRequest nullAmount = new TransactionCreateRequest(
                "INCOME", null, "INR", "cat-1", null, "Test", Instant.now(), null, null
        );
        assertThat(validator.validate(nullAmount)).isNotEmpty();

        // Zero amount
        TransactionCreateRequest zeroAmount = new TransactionCreateRequest(
                "INCOME", 0L, "INR", "cat-1", null, "Test", Instant.now(), null, null
        );
        assertThat(validator.validate(zeroAmount)).isNotEmpty();

        // Negative amount
        TransactionCreateRequest negAmount = new TransactionCreateRequest(
                "INCOME", -500L, "INR", "cat-1", null, "Test", Instant.now(), null, null
        );
        assertThat(validator.validate(negAmount)).isNotEmpty();
    }

    @Test
    @DisplayName("TransactionCreateRequest: blank type or blank categoryId fails validation")
    void transactionCreateRequest_blankFields_failsValidation() {
        TransactionCreateRequest blankType = new TransactionCreateRequest(
                "", 1000L, "INR", "cat-1", null, null, null, null, null
        );
        assertThat(validator.validate(blankType)).isNotEmpty();

        TransactionCreateRequest blankCat = new TransactionCreateRequest(
                "INCOME", 1000L, "INR", "", null, null, null, null, null
        );
        assertThat(validator.validate(blankCat)).isNotEmpty();
    }

    // ── DOMAIN ENTITY & RESPONSE MAPPING ─────────────────────────────────────

    @Test
    @DisplayName("Category: domain entity getters, setters, and CategoryResponse.from() mapping")
    void category_domainAndResponseMapping() {
        Timestamp now = Timestamp.now();
        Category category = new Category(
                "cat-100", "ws-1", "Client Invoicing", "INCOME", true,
                "#10B981", "briefcase", false, null, now, now, "user-1", "user-1"
        );

        assertThat(category.getId()).isEqualTo("cat-100");
        assertThat(category.getWorkspaceId()).isEqualTo("ws-1");
        assertThat(category.getName()).isEqualTo("Client Invoicing");
        assertThat(category.getType()).isEqualTo("INCOME");
        assertThat(category.isDefault()).isTrue();
        assertThat(category.isArchived()).isFalse();

        CategoryResponse response = CategoryResponse.from(category);
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("cat-100");
        assertThat(response.name()).isEqualTo("Client Invoicing");
        assertThat(response.type()).isEqualTo("INCOME");
        assertThat(response.isDefault()).isTrue();
        assertThat(response.isArchived()).isFalse();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Transaction: domain entity getters, setters, and TransactionResponse.from() mapping with currency formatting")
    void transaction_domainAndResponseMapping() {
        Timestamp now = Timestamp.now();
        // INR Transaction (2 decimal places)
        Transaction inrTx = new Transaction(
                "tx-1", "ws-1", "INCOME", 2500000L, "INR",
                "cat-100", "Client Invoicing", "#10B981",
                "cl-200", "Acme Corp", "Invoice #101",
                now, "BANK_TRANSFER", "COMPLETED",
                "user-1", "user-1", now, now
        );

        TransactionResponse inrResponse = TransactionResponse.from(inrTx);
        assertThat(inrResponse).isNotNull();
        assertThat(inrResponse.id()).isEqualTo("tx-1");
        assertThat(inrResponse.amountMinor()).isEqualTo(2500000L);
        assertThat(inrResponse.formattedAmount()).isEqualTo("25000.00");
        assertThat(inrResponse.fractionDigits()).isEqualTo(2);
        assertThat(inrResponse.currency()).isEqualTo("INR");

        // JPY Transaction (0 decimal places)
        Transaction jpyTx = new Transaction(
                "tx-2", "ws-2", "EXPENSE", 5000L, "JPY",
                "cat-101", "Software", "#F59E0B",
                null, null, "License",
                now, "CARD", "COMPLETED",
                "user-2", "user-2", now, now
        );

        TransactionResponse jpyResponse = TransactionResponse.from(jpyTx);
        assertThat(jpyResponse).isNotNull();
        assertThat(jpyResponse.id()).isEqualTo("tx-2");
        assertThat(jpyResponse.amountMinor()).isEqualTo(5000L);
        assertThat(jpyResponse.formattedAmount()).isEqualTo("5000");
        assertThat(jpyResponse.fractionDigits()).isEqualTo(0);
        assertThat(jpyResponse.currency()).isEqualTo("JPY");
    }
}

