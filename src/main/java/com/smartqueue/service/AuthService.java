package com.smartqueue.service;

import com.smartqueue.dto.AuthDtos.*;
import com.smartqueue.entity.User;
import com.smartqueue.entity.UserRole;
import com.smartqueue.repository.UserRepository;
import com.smartqueue.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthService — handles user registration and login.
 *
 * Register flow:
 *   1. Check username is not already taken
 *   2. Hash the password with BCrypt
 *   3. Save User to DB
 *   4. Generate and return a JWT token
 *
 * Login flow:
 *   1. Look up user by username
 *   2. Verify the password against the stored BCrypt hash
 *   3. Generate and return a JWT token
 *
 * Notice: the JWT is generated immediately on register/login.
 * The client stores this token and sends it with every request.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check for duplicate username
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken: " + request.getUsername());
        }

        // Default role to OPERATOR if not specified
        UserRole role = request.getRole() != null ? request.getRole() : UserRole.OPERATOR;

        // Hash the password — NEVER store plain text
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .username(request.getUsername())
                .password(hashedPassword)
                .tenantId(request.getTenantId())
                .role(role)
                .build();

        User saved = userRepository.save(user);
        log.info("Registered new user: {} (tenant: {}, role: {})",
                saved.getUsername(), saved.getTenantId(), saved.getRole());

        String token = jwtUtil.generateToken(
                saved.getUserId(),
                saved.getUsername(),
                saved.getTenantId(),
                saved.getRole().name()
        );

        return AuthResponse.builder()
                .token(token)
                .username(saved.getUsername())
                .tenantId(saved.getTenantId())
                .role(saved.getRole())
                .message("Registration successful")
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Look up user by username
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        /*
         * passwordEncoder.matches() takes the plain-text password and the stored hash.
         * BCrypt re-hashes the plain-text with the salt embedded in the stored hash,
         * then compares. We never decrypt — BCrypt is one-way.
         */
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }

        log.info("User logged in: {} (tenant: {}, role: {})",
                user.getUsername(), user.getTenantId(), user.getRole());

        String token = jwtUtil.generateToken(
                user.getUserId(),
                user.getUsername(),
                user.getTenantId(),
                user.getRole().name()
        );

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .tenantId(user.getTenantId())
                .role(user.getRole())
                .message("Login successful")
                .build();
    }
}