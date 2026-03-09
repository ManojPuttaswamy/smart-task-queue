package com.smartqueue.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * CustomAuthenticationEntryPoint — controls what Spring Security sends back
 * when a request arrives with NO token (or an invalid one).
 *
 * Why do we need this?
 * By default, Spring Security returns a 401 with an EMPTY body when
 * authentication is missing. That's inconsistent with the rest of our API
 * which returns structured JSON errors via GlobalExceptionHandler.
 *
 * GlobalExceptionHandler only catches exceptions thrown INSIDE controllers.
 * Spring Security rejects unauthenticated requests BEFORE they reach any
 * controller — so GlobalExceptionHandler never gets a chance to run.
 * This class fills that gap.
 *
 * Implements AuthenticationEntryPoint — Spring Security's hook for handling
 * 401 responses. Registered in SecurityConfig via .exceptionHandling().
 */
@Component
@Slf4j
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        log.warn("Unauthenticated request to {}: {}", request.getRequestURI(), authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);  // 401
        response.setContentType("application/json");

        Map<String, Object> body = Map.of(
                "status", 401,
                "error", "Unauthorized",
                "message", "Missing or invalid JWT token. Please login at POST /auth/login",
                "path", request.getRequestURI(),
                "timestamp", LocalDateTime.now().toString()
        );

        // Write the JSON body directly to the response output stream
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}