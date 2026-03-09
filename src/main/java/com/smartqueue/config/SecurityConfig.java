package com.smartqueue.config;

import com.smartqueue.security.CustomAccessDeniedHandler;
import com.smartqueue.security.CustomAuthenticationEntryPoint;
import com.smartqueue.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig — the central Spring Security configuration.
 *
 * Key decisions:
 *
 * 1. STATELESS session: we don't use HTTP sessions. Each request must
 *    carry its own JWT. This is essential for horizontal scaling —
 *    any server instance can validate any token without sharing session state.
 *
 * 2. CSRF disabled: CSRF attacks exploit session cookies. Since we're
 *    stateless (no cookies), CSRF is not a threat. Disabling it
 *    simplifies the API (no CSRF token needed in Postman/curl).
 *
 * 3. JwtAuthenticationFilter runs BEFORE UsernamePasswordAuthenticationFilter:
 *    Spring Security processes filters in order. Our JWT filter must run
 *    first to set up the SecurityContext before any auth checks happen.
 *
 * 4. @EnableMethodSecurity enables @PreAuthorize on controller methods —
 *    we'll use this in role-based access control.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity           // enables @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — we're stateless (JWT), no session cookies
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless: no HTTP session created or used
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .exceptionHandling(ex ->
                ex.authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            
            )

            // URL-level access rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no token required
                .requestMatchers("/auth/**").permitAll()

                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // Insert our JWT filter before Spring's default login filter
            .addFilterBefore(jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCryptPasswordEncoder — the industry standard for hashing passwords.
     *
     * BCrypt is slow by design (unlike MD5/SHA which are fast).
     * The "work factor" (default 10) means each hash takes ~100ms,
     * making brute-force attacks impractical even with leaked hashes.
     *
     * Never use MD5, SHA-1, or SHA-256 directly for passwords —
     * they're too fast (millions of hashes/second on a GPU).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}