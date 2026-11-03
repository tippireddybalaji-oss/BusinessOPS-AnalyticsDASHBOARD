package com.businessops.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the BusinessOPS Analytics Dashboard backend.
 *
 * <p>Phase 2 – Firebase Auth + Firestore integration:
 * <ul>
 *   <li>Spring Boot REST API</li>
 *   <li>Firebase Admin SDK (Auth + Firestore)</li>
 *   <li>Spring Security with Firebase ID token filter</li>
 *   <li>Health endpoint at {@code GET /api/health} (public)</li>
 *   <li>Auth endpoint at {@code GET /api/auth/me} (protected)</li>
 * </ul>
 */
@SpringBootApplication
public class BusinessOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessOpsApplication.class, args);
    }
}

