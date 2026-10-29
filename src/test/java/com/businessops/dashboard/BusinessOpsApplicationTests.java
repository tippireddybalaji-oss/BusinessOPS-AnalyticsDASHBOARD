package com.businessops.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test – verifies that the Spring application context loads without errors.
 *
 * <p>Firebase graceful-degradation means no real credentials are needed here.
 */
@SpringBootTest
@DisplayName("Application context loads successfully")
class BusinessOpsApplicationTests {

    @Test
    @DisplayName("Context loads without throwing exceptions")
    void contextLoads() {
        // If the context fails to load, Spring Boot throws an exception
        // and this test fails automatically.
    }
}
