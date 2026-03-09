package com.smartqueue.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * CustomAccessDeniedHandler — controls what Spring Security sends back
 * when an AUTHENTICATED user tries to access an endpoint their role
 * doesn't permit.
 *
 * The difference between 401 and 403:
 *
 *   401 Unauthorized → you are NOT authenticated (no token / bad token)
 *                       handled by CustomAuthenticationEntryPoint
 *
 *   403 Forbidden    → you ARE authenticated, but your role doesn't allow this
 *                       handled by THIS class (CustomAccessDeniedHandler)
 *
 * Example: a VIEWER tries to POST /jobs → they have a valid JWT (401 doesn't apply),
 * but their role is VIEWER which fails @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
 * → Spring Security calls this handler → returns 403 with a clear JSON message.
 *
 * Without this: Spring Security returns 403 with an empty body (same problem as 401).
 * Registered in SecurityConfig via .exceptionHandling().accessDeniedHandler(...)
 */
@Component
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        log.warn("Access denied to {} : {}", request.getRequestURI(), accessDeniedException.getMessage());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);   // 403
        response.setContentType("application/json");

        Map<String, Object> body = Map.of(
                "status", 403,
                "error", "Forbidden",
                "message", "You don't have permission to access this resource. Required role is insufficient.",
                "path", request.getRequestURI(),
                "timestamp", LocalDateTime.now().toString()
        );

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}