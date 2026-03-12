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
 * These are populated asynchronously after job creation
 * by calling the Python AI classifier service.
 *
 * nullable=true on all classification fields because:
 * - Classification happens AFTER the job is saved
 * - If the classifier is down, the job still gets created
 * - The fields are filled in a second DB update
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

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /**
     * AI-assigned category: Infrastructure / Database / Application / Network
     * Null until classifier runs. Stays null if classifier fails.
     */
    @Column(name = "category")
    private String category;

    /**
     * AI-assigned priority: HIGH / MEDIUM / LOW
     */
    @Column(name = "priority")
    private String priority;

    /**
     * How confident the AI was: 0.0 - 1.0
     * Below 0.6 = fallback was used instead of AI
     */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    /**
     * "AI" if OpenAI classified it, "FALLBACK" if keyword rules were used.
     * Tells you how reliable the classification is.
     */
    @Column(name = "classification_source")
    private String classificationSource;

    // ── Timestamps & versioning ───────────────────────────────────────────────

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;
}