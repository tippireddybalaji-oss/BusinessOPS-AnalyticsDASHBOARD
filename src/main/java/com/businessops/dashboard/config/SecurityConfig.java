package com.businessops.dashboard.config;

import com.businessops.dashboard.security.FirebaseAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration for the BusinessOPS Analytics Dashboard API.
 *
 * <h2>Access rules</h2>
 * <ul>
 *   <li>{@code OPTIONS /**}     – permitted for browser CORS pre-flight checks.</li>
 *   <li>{@code GET /api/health} – publicly accessible (load-balancer probe).</li>
 *   <li>{@code /actuator/**}   – publicly accessible (metrics/info only).</li>
 *   <li>{@code /api/v1/me}     – requires a valid Firebase ID token.</li>
 *   <li>{@code /api/auth/**}   – requires a valid Firebase ID token.</li>
 *   <li>All other {@code /api/v1/**} and {@code /api/**} paths – require authentication.</li>
 * </ul>
 *
 * <h2>Design choices</h2>
 * <ul>
 *   <li>Stateless (no HTTP sessions) – each request must supply a valid token.</li>
 *   <li>CSRF disabled – this is a REST API, not a browser form application.</li>
 *   <li>CORS enabled with configurable allowed origins for dev and prod.</li>
 *   <li>No HTTP Basic or form login – replaced entirely by Firebase ID token verification.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final FirebaseAuthFilter firebaseAuthFilter;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:5500,http://127.0.0.1:5500,http://localhost:8080,http://127.0.0.1:8080}")
    private String allowedOriginsConfig;

    public SecurityConfig(FirebaseAuthFilter firebaseAuthFilter) {
        this.firebaseAuthFilter = firebaseAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Enable CORS using the configured CorsConfigurationSource
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Stateless REST API – no session management
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Disable CSRF – not needed for token-based REST APIs
            .csrf(AbstractHttpConfigurer::disable)

            // Disable default form login and HTTP Basic
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Allow CORS pre-flight requests across all endpoints
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Public health probe and actuator endpoints
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                // Protected: API v1 and legacy auth endpoints (require Firebase token)
                .requestMatchers("/api/v1/me").authenticated()
                .requestMatchers("/api/auth/**").authenticated()
                .requestMatchers("/api/v1/**").authenticated()
                .requestMatchers("/api/**").authenticated()
                // Anything else
                .anyRequest().denyAll()
            )

            // Return 401 JSON (not 302 redirect) for unauthenticated access
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write(
                        "{\"error\":\"Authentication required.\",\"status\":401}"
                    );
                })
            )

            // Insert Firebase token filter before Spring's default auth filter
            .addFilterBefore(firebaseAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Cache pre-flight response for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
