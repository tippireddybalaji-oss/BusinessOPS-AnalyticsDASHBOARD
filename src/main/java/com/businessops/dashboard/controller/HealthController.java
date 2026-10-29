package com.businessops.dashboard.controller;

import com.businessops.dashboard.model.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public health-check endpoint.
 *
 * <p>{@code GET /api/health} returns a JSON payload indicating that the
 * application is running.  No authentication is required for this endpoint –
 * it is intended for load-balancer probes and CI smoke-tests.
 *
 * <p>Example response:
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "application": "BusinessOPS and analyticsDASHBOARD"
 * }
 * }</pre>
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * Returns a 200 OK response confirming the service is alive.
     *
     * @return a {@link HealthResponse} wrapped in a 200 ResponseEntity
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.up());
    }
}
