package com.smartqueue.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a job that has permanently failed after all retries.
 * Published to the "job-dlq" topic.
 *
 * Why a separate event class from JobEvent?
 * - DLQ events carry extra failure metadata (reason, retryCount)
 * - Consumers of DLQ need to know WHY it failed, not just what it was
 * - Keeps the schemas clean and independently evolvable
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqEvent {

    private UUID jobId;
    private String tenantId;
    private String title;
    private String description;
    private String failureReason;     // what caused the final failure
    private int retryCount;           // how many times it was retried (always MAX_RETRIES)
    private LocalDateTime failedAt;   // when it was sent to DLQ
    private String originalEventType; // the event type that triggered the original processing
}