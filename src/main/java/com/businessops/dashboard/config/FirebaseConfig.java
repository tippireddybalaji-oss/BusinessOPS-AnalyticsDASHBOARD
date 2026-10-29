package com.businessops.dashboard.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Firebase Admin SDK initialisation.
 *
 * <p>The service-account credential file is <strong>never</strong> committed to
 * version control.  Set one of the following before starting the application:
 *
 * <ol>
 *   <li><b>Environment variable</b> {@code GOOGLE_APPLICATION_CREDENTIALS}
 *       pointing to the JSON key file – the SDK picks this up automatically.</li>
 *   <li><b>Application property</b> {@code firebase.service-account-path}
 *       (in {@code application.properties} or via {@code -D} flag) pointing to
 *       the JSON key file on disk.</li>
 * </ol>
 *
 * <p>If neither is set the application starts without Firebase (graceful
 * degradation) and logs a warning.  This keeps the test suite runnable on CI
 * without real credentials.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    /**
     * Optional path to a Firebase service-account JSON file.
     * Leave blank to fall back to Application Default Credentials (ADC)
     * or to run without Firebase during local development / CI.
     */
    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    @PostConstruct
    public void initFirebase() {
        // Skip if already initialised (e.g. hot-reload in dev)
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase already initialised – skipping.");
            return;
        }

        try {
            FirebaseOptions options;

            if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
                // Explicit path configured via property
                log.info("Initialising Firebase from service-account file: {}", serviceAccountPath);
                try (InputStream serviceAccount = new FileInputStream(serviceAccountPath)) {
                    options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                            .build();
                }
            } else {
                // Fall back to Application Default Credentials (GOOGLE_APPLICATION_CREDENTIALS, etc.)
                log.info("No firebase.service-account-path set – trying Application Default Credentials.");
                options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .build();
            }

            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialised successfully.");

        } catch (IOException e) {
            // Graceful degradation: log a warning but do not crash the application.
            // Firebase-dependent endpoints will fail at runtime when credentials are absent.
            log.warn(
                "Firebase Admin SDK could not be initialised ({}). "
                + "Set GOOGLE_APPLICATION_CREDENTIALS or firebase.service-account-path "
                + "before using Firebase-backed features.",
                e.getMessage()
            );
        }
    }
}
