package com.smartqueue.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Catches exceptions thrown by any controller and returns
 * a clean JSON response instead of a Spring HTML error page.
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 * Applies to ALL controllers automatically.
 *
 * Exception handler priority — Spring picks the MOST SPECIFIC handler:
 *   AccessDeniedException  → 403  (must be declared before RuntimeException
 *                                  because AccessDeniedException extends RuntimeException)
 *   IllegalArgumentException → 400
 *   IllegalStateException    → 400
 *   RuntimeException         → 404 / 401 / 500  (catch-all, most generic)
 *
 * Why AccessDeniedException ends up here and not in CustomAccessDeniedHandler:
 *   CustomAccessDeniedHandler handles 403s thrown by Spring Security's filter chain
 *   (i.e., before the request reaches a controller).
 *   @PreAuthorize throws AccessDeniedException INSIDE the controller layer,
 *   AFTER the request has passed the filter chain — so @ControllerAdvice catches it.
 *   Both handlers are needed for complete coverage.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles @PreAuthorize failures — when an authenticated user's role
     * doesn't permit the action (e.g., VIEWER trying to POST /jobs).
     *
     * IMPORTANT: must be declared BEFORE handleRuntimeException because
     * AccessDeniedException extends RuntimeException. Spring picks the most
     * specific handler, but only if it's registered — declaring it explicitly
     * here guarantees it's always caught correctly.
     *
     * Returns 403 Forbidden.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "status", HttpStatus.FORBIDDEN.value(),
                "error", "Forbidden",
                "message", "You don't have permission to perform this action.",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Handles job-not-found and invalid login credentials.
     * Returns 404 for "not found", 401 for bad credentials, 500 for everything else.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("RuntimeException caught: {}", ex.getMessage());

        HttpStatus status;
        if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("not found")) {
            status = HttpStatus.NOT_FOUND;
        } else if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("invalid username or password")) {
            status = HttpStatus.UNAUTHORIZED;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Handles bad input such as duplicate username on registration.
     * Returns 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException caught: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Bad Request",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Handles invalid state transitions (e.g., replay on a non-FAILED job).
     * Returns 400 Bad Request.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
        log.warn("IllegalStateException caught: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Bad Request",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}