package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persists a record of every significant action in the system.
 *
 * Why audit logging matters:
 *   - Security: know who did what and when
 *   - Debugging: trace the exact sequence of events for a job
 *   - Compliance: some industries (finance, healthcare) legally require it
 *
 * Design decisions:
 *   - oldState / newState: stored as Strings (not enums) so we can audit
 *     any entity, not just jobs with JobStatus enums
 *   - userId / username: stored directly (denormalized) so audit records
 *     remain meaningful even if the user is later deleted
 *   - No @UpdateTimestamp: audit records are immutable — never updated,
 *     only created
 *   - updatable=false on all columns: enforces immutability at DB level
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_audit_entity_id", columnList = "entity_id"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false)
    private UUID id;

    /**
     * Which tenant this audit entry belongs to.
     * Allows admins to query audit logs scoped to their tenant.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    /**
     * The user who performed the action.
     * Stored as both ID and username for clarity — username avoids
     * needing a JOIN to the user table just to display audit logs.
     */
    @Column(name = "user_id", updatable = false)
    private String userId;

    @Column(name = "username", updatable = false)
    private String username;

    /**
     * What happened — e.g., "JOB_CREATED", "JOB_STATUS_CHANGED", "JOB_REPLAYED"
     */
    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    /**
     * The ID of the entity that was acted on (e.g., the jobId).
     * Stored as String to support any entity type in the future.
     */
    @Column(name = "entity_id", updatable = false)
    private String entityId;

    /**
     * State before the action — null for creation events.
     * e.g., "PENDING"
     */
    @Column(name = "old_state", updatable = false)
    private String oldState;

    /**
     * State after the action.
     * e.g., "PROCESSING"
     */
    @Column(name = "new_state", updatable = false)
    private String newState;

    /**
     * Optional human-readable context.
     * e.g., "Retry attempt 2 of 3", "Replayed after DB connection fix"
     */
    @Column(name = "details", columnDefinition = "TEXT", updatable = false)
    private String details;

    /**
     * When this audit entry was created.
     * updatable=false ensures it's set once and never modified.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}