package com.smartqueue.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JwtAuthenticationFilter — runs ONCE per request, before Spring Security's
 * standard authentication checks.
 *
 * What it does:
 *  1. Reads the Authorization header: "Bearer <token>"
 *  2. Validates the JWT
 *  3. If valid: sets the authenticated user in the SecurityContext
 *  4. If missing/invalid: does nothing (SecurityContext stays empty → 401)
 *
 * Why extend OncePerRequestFilter?
 * Some frameworks call filters multiple times per request (e.g., on forwards/includes).
 * OncePerRequestFilter guarantees this runs exactly once per HTTP request.
 *
 * Why not throw an exception on invalid token?
 * We let the request continue with an empty SecurityContext.
 * Spring Security's AuthorizationFilter will then reject it with 401/403.
 * This is cleaner — the filter's only job is to set up authentication, not
 * to reject requests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // If no Authorization header or doesn't start with "Bearer ", skip
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Strip "Bearer " prefix to get the raw token
        String token = authHeader.substring(7);

        try {
            if (jwtUtil.isTokenValid(token)) {
                String username = jwtUtil.extractUsername(token);
                String role     = jwtUtil.extractRole(token);
                String tenantId = jwtUtil.extractTenantId(token);
                String userId   = jwtUtil.extractUserId(token);

                /*
                 * Build a Spring Security Authentication object.
                 *
                 * ROLE_ prefix is Spring Security convention.
                 * When you later use @PreAuthorize("hasRole('ADMIN')"),
                 * Spring looks for an authority called "ROLE_ADMIN".
                 */
                List<SimpleGrantedAuthority> authorities =
                        List.of(new SimpleGrantedAuthority("ROLE_" + role));

                /*
                 * We store the full principal as an AuthenticatedUser record
                 * so downstream code (services, controllers) can easily
                 * access tenantId and userId from the security context.
                 */
                AuthenticatedUser principal = new AuthenticatedUser(userId, username, tenantId, role);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);

                // Store in SecurityContext — this is what @PreAuthorize reads
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Authenticated user: {} (tenant: {}, role: {})", username, tenantId, role);
            }
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            // Don't set authentication — request will be rejected by Spring Security
        }

        filterChain.doFilter(request, response);
    }
}