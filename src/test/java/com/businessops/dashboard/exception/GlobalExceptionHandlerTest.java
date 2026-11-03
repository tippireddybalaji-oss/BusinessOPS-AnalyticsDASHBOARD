package com.businessops.dashboard.exception;

import com.businessops.dashboard.model.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Verifies status codes, error message sanitization, and structured payload generation.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test/resource");
    }

    @Test
    @DisplayName("Validation exception maps to 400 BAD_REQUEST with field error details")
    void handleValidationException_returns400WithDetails() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError fieldError = new FieldError("clientDto", "name", "Name cannot be blank");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Validation Failed");
        assertThat(response.getBody().path()).isEqualTo("/api/test/resource");
        assertThat(response.getBody().validationErrors()).containsEntry("name", "Name cannot be blank");
    }

    @Test
    @DisplayName("FirebaseAuthenticationException maps to 401 UNAUTHORIZED")
    void handleFirebaseAuthException_returns401() {
        FirebaseAuthenticationException ex = new FirebaseAuthenticationException("Invalid ID token signature");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleFirebaseAuthException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(401);
        assertThat(response.getBody().error()).isEqualTo("Authentication Failed");
        assertThat(response.getBody().message()).isEqualTo("Invalid ID token signature");
    }

    @Test
    @DisplayName("AccessDeniedException maps to 403 FORBIDDEN")
    void handleAccessDenied_returns403() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden workspace");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(403);
        assertThat(response.getBody().error()).isEqualTo("Forbidden");
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException maps to 405 METHOD_NOT_ALLOWED")
    void handleMethodNotSupported_returns405() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleMethodNotSupported(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(405);
        assertThat(response.getBody().error()).isEqualTo("Method Not Allowed");
    }

    @Test
    @DisplayName("IllegalArgumentException maps to 400 BAD_REQUEST")
    void handleIllegalArgument_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Amount must be positive");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleIllegalArgument(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Invalid Argument");
        assertThat(response.getBody().message()).isEqualTo("Amount must be positive");
    }

    @Test
    @DisplayName("Generic unhandled exception maps to 500 INTERNAL_SERVER_ERROR without leaking internals")
    void handleGenericException_returns500WithoutLeakingDetails() {
        RuntimeException ex = new RuntimeException("Database connection timeout at 192.168.1.50");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleGenericException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred. Please try again later.");
        // Ensure sensitive internal message is NOT exposed
        assertThat(response.getBody().message()).doesNotContain("192.168.1.50");
    }
}
