package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Maps to the job_instance table in PostgreSQL.
 *
 * Key concepts used here:
 * - @Entity        → tells JPA this class is a database table
 * - @Id            → marks the primary key
 * - @GeneratedValue → auto-generates the UUID
 * - @Version       → optimistic locking (prevents race conditions)
 * - @Enumerated    → stores enum as a string in DB (not a number)
 * - Lombok @Data   → auto-generates getters, setters, equals, hashCode
 * - Lombok @Builder → lets us do: JobInstance.builder().title("x").build()
 */
@Entity
@Table(name = "job_instance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * @Version enables optimistic locking.
     * If two threads try to update the same job simultaneously,
     * the second one will get an OptimisticLockException instead of
     * silently overwriting data.
     */
    @Version
    @Column(name = "version")
    private Long version;
}