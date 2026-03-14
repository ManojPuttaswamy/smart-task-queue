package com.smartqueue.kafka;

import com.smartqueue.entity.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The event we publish to Kafka when a job is created.
 *
 * Why a separate event class and not just JobInstance?
 * - We control exactly what goes into the Kafka message
 * - Event schema can evolve independently from the DB entity
 * - Other services consuming this event don't need to know about JPA
 *
 * @NoArgsConstructor is required by Jackson for deserialization
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobEvent {

    private UUID jobId;
    private String tenantId;
    private String title;
    private String description;
    private JobStatus status;
    private String eventType;       // e.g. "JOB_CREATED" — useful for consumers to filter
    private LocalDateTime occurredAt;
     
    /**
     * Correlation ID travels with the event through the entire pipeline.
     * Consumer extracts it and puts it in MDC so all downstream logs
     * are tagged with the same ID as the original HTTP request.
     */
    private String correlationId;
}