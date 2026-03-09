package com.smartqueue.dto;

import com.smartqueue.entity.UserRole;
import lombok.Data;

/**
 * DTOs for the /auth endpoints.
 *
 * We use separate inner classes here for clarity, but they could
 * each live in their own file — it's a style choice.
 */
public class AuthDtos {

    /**
     * POST /auth/register request body.
     * The caller provides username, password, tenantId, and desired role.
     *
     * In a real system, role assignment would be restricted —
     * you wouldn't let anyone self-assign ADMIN. But for this project
     * we keep it simple.
     */
    @Data
    public static class RegisterRequest {
        private String username;
        private String password;
        private String tenantId;
        private UserRole role;
    }

    /**
     * POST /auth/login request body.
     * Just username and password — simple credential check.
     */
    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    /**
     * Response returned after successful login or register.
     * The token is a signed JWT the client must include in
     * every subsequent request as: Authorization: Bearer <token>
     */
    @Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class AuthResponse {
        private String token;
        private String username;
        private String tenantId;
        private UserRole role;
        private String message;
    }
}