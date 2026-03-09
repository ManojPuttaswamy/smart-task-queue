package com.smartqueue.controller;

import com.smartqueue.dto.AuthDtos.*;
import com.smartqueue.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — public endpoints, no JWT required.
 *
 * POST /auth/register  — create a new user account
 * POST /auth/login     — get a JWT token with valid credentials
 *
 * These are permitted in SecurityConfig via .requestMatchers("/auth/**").permitAll()
 */
@RestController
@RequestMapping("/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /auth/register
     * Body: { "username": "alice", "password": "secret", "tenantId": "tenant-a", "role": "OPERATOR" }
     * Returns: JWT token + user details
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        log.info("POST /auth/register for username: {}", request.getUsername());
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /auth/login
     * Body: { "username": "alice", "password": "secret" }
     * Returns: JWT token + user details
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        log.info("POST /auth/login for username: {}", request.getUsername());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}