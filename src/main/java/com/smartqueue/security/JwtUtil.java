package com.smartqueue.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * JwtUtil — responsible for generating and validating JWT tokens.
 *
 * What is a JWT?
 * A JSON Web Token has 3 parts separated by dots:
 *   Header.Payload.Signature
 *
 * Header:  { "alg": "HS256", "typ": "JWT" }
 * Payload: { "sub": "userId", "tenantId": "tenant-a", "role": "ADMIN", "iat": ..., "exp": ... }
 * Signature: HMAC-SHA256(base64(header) + "." + base64(payload), secretKey)
 *
 * The signature ensures the token hasn't been tampered with.
 * Anyone can READ the payload (it's just base64), but no one can FORGE
 * a valid signature without knowing the secret key.
 *
 * This is why you should NEVER put sensitive data (passwords, credit cards)
 * in a JWT payload.
 *
 */
@Component
@Slf4j
public class JwtUtil {

    /**
     * The secret key used to sign and verify tokens.
     * Must be at least 256 bits (32 bytes) for HMAC-SHA256.
     * Loaded from application.yml — never hardcode secrets in source code.
     */
    private final SecretKey signingKey;

    /**
     * How long the token is valid, in milliseconds.
     * Default: 24 hours = 86,400,000 ms
     */
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs) {

        // Keys.hmacShaKeyFor ensures the key is the right length for HS256
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a signed JWT token.
     *
     * Claims we embed:
     *  - sub (subject): the userId — standard JWT claim for "who is this token for"
     *  - username: the human-readable login name
     *  - tenantId: used to scope all DB queries to this tenant
     *  - role: used for RBAC (@PreAuthorize checks)
     *  - iat (issued at): auto-set by JJWT
     *  - exp (expiration): when the token stops being valid
     */
    public String generateToken(UUID userId, String username, String tenantId, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userId.toString())         // who the token is for
                .claim("username", username)         // custom claim
                .claim("tenantId", tenantId)         // custom claim — used for tenant isolation
                .claim("role", role)                 // custom claim — used for RBAC
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)        
                .compact();
    }

    /**
     * Parses and validates a token.
     * Returns the Claims (payload) if the token is valid.
     * Throws a JwtException if the token is expired, malformed, or tampered with.
     */
    public Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Convenience extractors — pull individual fields from a validated token.
     */
    public String extractUserId(String token) {
        return validateAndExtractClaims(token).getSubject();
    }

    public String extractUsername(String token) {
        return (String) validateAndExtractClaims(token).get("username");
    }

    public String extractTenantId(String token) {
        return (String) validateAndExtractClaims(token).get("tenantId");
    }

    public String extractRole(String token) {
        return (String) validateAndExtractClaims(token).get("role");
    }

    /**
     * Returns true if the token is valid (not expired, not tampered).
     * Catches all JWT exceptions and returns false instead of propagating.
     */
    public boolean isTokenValid(String token) {
        try {
            validateAndExtractClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty or null: {}", e.getMessage());
        }
        return false;
    }
}