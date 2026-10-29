package com.businessops.dashboard.model;

/**
 * Immutable response body for {@code GET /api/health}.
 *
 * <p>Uses Java 16+ records for concise, immutable value semantics.
 * Jackson serialises this to JSON automatically.
 */
public record HealthResponse(String status, String application) {

    /** Convenience factory that always returns an UP response. */
    public static HealthResponse up() {
        return new HealthResponse("UP", "BusinessOPS and analyticsDASHBOARD");
    }
}
