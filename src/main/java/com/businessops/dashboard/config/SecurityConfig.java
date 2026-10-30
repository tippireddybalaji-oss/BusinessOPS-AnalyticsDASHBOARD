package com.businessops.dashboard.config;

import com.businessops.dashboard.security.FirebaseAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security configuration for the BusinessOPS Analytics Dashboard API.
 *
 * <h2>Access rules</h2>
 * <ul>
 *   <li>{@code GET /api/health} – publicly accessible (load-balancer probe).</li>
 *   <li>{@code /actuator/**}   – publicly accessible (metrics/info only).</li>
 *   <li>{@code /api/auth/**}   – requires a valid Firebase ID token.</li>
 *   <li>All other {@code /api/**} paths – require authentication (future endpoints).</li>
 * </ul>
 *
 * <h2>Design choices</h2>
 * <ul>
 *   <li>Stateless (no HTTP sessions) – each request must supply a valid token.</li>
 *   <li>CSRF disabled – this is a REST API, not a browser form application.</li>
 *   <li>No HTTP Basic auth – replaced entirely by Firebase ID token verification.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final FirebaseAuthFilter firebaseAuthFilter;

    public SecurityConfig(FirebaseAuthFilter firebaseAuthFilter) {
        this.firebaseAuthFilter = firebaseAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
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
                // Public endpoints
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                // Protected: authentication endpoints (require Firebase token)
                .requestMatchers("/api/auth/**").authenticated()
                // Protected: all other API paths (future business endpoints)
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
}
