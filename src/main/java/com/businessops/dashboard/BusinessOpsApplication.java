package com.businessops.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the BusinessOPS Analytics Dashboard backend.
 *
 * <p>Phase 1 – Backend foundation:
 * <ul>
 *   <li>Spring Boot REST API</li>
 *   <li>Firebase Admin SDK integration</li>
 *   <li>Health endpoint at {@code GET /api/health}</li>
 * </ul>
 */
@SpringBootApplication
public class BusinessOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessOpsApplication.class, args);
    }
}
