package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Maps to the app_user table in PostgreSQL.
 *
 * Why "app_user" and not "user"?
 * "user" is a reserved keyword in PostgreSQL — using it as a table name
 * causes SQL errors. Always name user tables something else.
 *
 * Fields:
 *  - userId     : UUID primary key (same pattern as JobInstance)
 *  - username   : unique login name
 *  - password   : BCrypt hashed — NEVER store plain text passwords
 *  - tenantId   : which tenant this user belongs to (used in JWT claims)
 *  - role       : VIEWER / OPERATOR / ADMIN
 *  - createdAt  : auto-set by Hibernate on insert
 */
@Entity
@Table(name = "app_user")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    /**
     * Always stored as a BCrypt hash.
     * BCrypt adds a salt automatically, so the same password hashes
     * differently every time — which prevents rainbow table attacks.
     */
    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * Stored as a string in the DB (e.g., "ADMIN").
     * EnumType.STRING is always preferred over ORDINAL —
     * if you reorder the enum, ORDINAL values silently break.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private UserRole role = UserRole.OPERATOR;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}