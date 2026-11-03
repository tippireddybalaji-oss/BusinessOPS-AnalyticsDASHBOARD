package com.businessops.dashboard.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized API error payload for all REST error responses.
 *
 * <p>Ensures consistent error formatting across validation errors,
 * security exceptions, and unexpected server errors without leaking
 * sensitive internals or stack traces.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String error,
        int status,
        String message,
        String path,
        Instant timestamp,
        Map<String, String> validationErrors
) {
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(error, status, message, path, Instant.now(), null);
    }

    public static ApiErrorResponse of(int status, String error, String message, String path, Map<String, String> validationErrors) {
        return new ApiErrorResponse(error, status, message, path, Instant.now(), validationErrors);
    }
}
