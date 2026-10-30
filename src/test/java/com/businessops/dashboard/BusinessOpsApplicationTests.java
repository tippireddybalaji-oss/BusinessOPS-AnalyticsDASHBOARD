package com.businessops.dashboard;

import com.google.firebase.auth.FirebaseAuth;
import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Smoke test – verifies that the full Spring application context loads without
 * errors, including Security config, Firebase config, and all controllers.
 *
 * <p>Firebase beans are mocked so no real credentials are required in CI
 * or local development environments without a service account configured.
 */
@SpringBootTest
@DisplayName("Application context loads successfully")
class BusinessOpsApplicationTests {

    /**
     * Mock FirebaseAuth so the FirebaseAuthFilter can be constructed
     * without real Firebase credentials.
     */
    @MockitoBean
    private FirebaseAuth firebaseAuth;

    /**
     * Mock Firestore so future repository beans can be constructed
     * without a real Firestore connection.
     */
    @MockitoBean
    private Firestore firestore;

    @Test
    @DisplayName("Context loads without throwing exceptions")
    void contextLoads() {
        // Spring Boot throws an exception before reaching here if context
        // fails to load — no assertions needed.
    }
}
