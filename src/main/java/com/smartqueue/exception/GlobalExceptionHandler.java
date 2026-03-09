package com.smartqueue.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Without this:
 *   {"timestamp":"...","status":500,"error":"Internal Server Error","path":"/jobs/..."}
 *
 * With this:
 *   {"status":404,"error":"Not Found","message":"Job not found: abc-123","timestamp":"..."}
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles job-not-found and invalid login credentials (RuntimeException).
     * Returns 404 for "not found" messages, 401 for "invalid" credentials,
     * 500 for everything else.
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