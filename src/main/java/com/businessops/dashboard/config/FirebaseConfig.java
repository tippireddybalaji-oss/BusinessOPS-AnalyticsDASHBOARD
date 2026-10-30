package com.businessops.dashboard.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Firebase Admin SDK initialisation.
 *
 * <p>Credentials are <strong>never</strong> hardcoded or committed.
 * The SDK reads them from the {@code GOOGLE_APPLICATION_CREDENTIALS} environment
 * variable (Application Default Credentials).  No credential path is stored in
 * {@code application.properties} — the property was removed to avoid any
 * temptation to commit a real path.
 *
 * <p>If credentials are absent (e.g. local dev or CI without Firebase) the
 * application context still loads with a warning; only Firebase-backed endpoints
 * will fail at runtime.
 *
 * <h2>Exposed beans</h2>
 * <ul>
 *   <li>{@link FirebaseAuth} – used by the security filter to verify ID tokens</li>
 *   <li>{@link Firestore}    – reusable Firestore client for future repository
 *       classes; no collections are created here</li>
 * </ul>
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    /**
     * Initialises the default {@link FirebaseApp} once using Application Default
     * Credentials.  Idempotent – skips re-initialisation on hot-reload.
     *
     * @throws IOException if credential loading fails (caught internally;
     *                     app degrades gracefully with a WARN log)
     */
    @Bean
    public FirebaseApp firebaseApp() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase already initialised – reusing existing FirebaseApp.");
            return FirebaseApp.getInstance();
        }

        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();
            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialised successfully via Application Default Credentials.");
            return app;
        } catch (IOException e) {
            /*
             * Graceful degradation: log a warning but do not prevent context startup.
             * Endpoints that depend on Firebase (auth filter, /api/auth/me) will throw
             * at runtime.  The public /api/health endpoint remains unaffected.
             */
            log.warn(
                "Firebase Admin SDK could not be initialised – {}. "
                + "Set GOOGLE_APPLICATION_CREDENTIALS before using Firebase-backed features.",
                e.getMessage()
            );
            return null;   // null bean; consumers must guard with @Autowired(required=false)
        }
    }

    /**
     * Exposes {@link FirebaseAuth} as a Spring bean so it can be injected into
     * the security filter and other components.
     *
     * <p>Returns {@code null} (optional bean) when Firebase could not be
     * initialised.  Consumers that depend on it will fail at runtime with a clear
     * message rather than at startup.
     *
     * @param firebaseApp the initialised {@link FirebaseApp} (may be {@code null})
     * @return a {@link FirebaseAuth} instance, or {@code null} if unavailable
     */
    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        if (firebaseApp == null) {
            log.warn("FirebaseAuth bean is null because FirebaseApp failed to initialise.");
            return null;
        }
        return FirebaseAuth.getInstance(firebaseApp);
    }

    /**
     * Exposes a reusable {@link Firestore} client bean.
     *
     * <p>No collections are created here.  Business repository classes will
     * inject this bean in future phases.
     *
     * @param firebaseApp the initialised {@link FirebaseApp} (may be {@code null})
     * @return a {@link Firestore} client, or {@code null} if unavailable
     */
    @Bean
    public Firestore firestore(FirebaseApp firebaseApp) {
        if (firebaseApp == null) {
            log.warn("Firestore bean is null because FirebaseApp failed to initialise.");
            return null;
        }
        return FirestoreClient.getFirestore(firebaseApp);
    }
}
