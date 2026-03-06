package com.smartqueue.service;

import com.smartqueue.entity.JobInstance;
import com.smartqueue.entity.JobStatus;
import com.smartqueue.kafka.DlqEvent;
import com.smartqueue.kafka.DlqProducer;
import com.smartqueue.kafka.JobEvent;
import com.smartqueue.repository.JobRepository;
import com.smartqueue.statemachine.JobStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * Day 6 changes:
 * - Injected DlqProducer
 * - When max retries exhausted → build DlqEvent and publish to job-dlq topic
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobProcessingService {

    private final JobRepository jobRepository;
    private final JobStateMachine jobStateMachine;
    private final IdempotencyService idempotencyService;
    private final RetryService retryService;
    private final DlqProducer dlqProducer;

    private final Random random = new Random();

    @Transactional
    public void processJob(JobEvent event) {

        // Redis idempotency check — skip duplicates immediately
        if (idempotencyService.isAlreadyProcessed(event.getJobId(), event.getEventType())) {
            log.info("Skipping duplicate event: jobId={}, eventType={}",
                    event.getJobId(), event.getEventType());
            return;
        }

        // Fetch fresh from DB
        JobInstance job = jobRepository.findById(event.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + event.getJobId()));

        log.info("Processing job: jobId={}, currentStatus={}, retryCount={}",
                job.getJobId(), job.getStatus(), job.getRetryCount());

        // Safety net for non-PENDING jobs
        if (job.getStatus() != JobStatus.PENDING) {
            log.warn("Job not in PENDING state, skipping: jobId={}, status={}",
                    job.getJobId(), job.getStatus());
            idempotencyService.markAsProcessed(event.getJobId(), event.getEventType());
            return;
        }

        try {
            // PENDING → PROCESSING
            job.setStatus(jobStateMachine.transition(job.getStatus(), JobStatus.PROCESSING));
            jobRepository.save(job);
            log.info("Job status updated to PROCESSING: jobId={}", job.getJobId());

            // Simulate actual work
            Thread.sleep(500);

            // Simulate 30% random failure — mimics real-world errors
            // (DB timeout, network error, third-party API failure, etc.)
            simulateRandomFailure();

            // PROCESSING → COMPLETED
            job.setStatus(jobStateMachine.transition(job.getStatus(), JobStatus.COMPLETED));
            jobRepository.save(job);
            log.info("Job completed successfully: jobId={}, totalRetries={}",
                    job.getJobId(), job.getRetryCount());

            // Mark as processed in Redis — only on SUCCESS
            // If we marked it before processing and then crashed, the job would be lost
            idempotencyService.markAsProcessed(event.getJobId(), event.getEventType());

        } catch (SimulatedProcessingException e) {
            // Reset status back to PENDING so RetryService can handle it
            job.setStatus(JobStatus.PENDING);
            jobRepository.save(job);

            // RetryService decides: retry with backoff OR mark as FAILED
            boolean willRetry = retryService.handleFailure(job, e);

            if (willRetry) {
                // Clear idempotency key so the retry attempt is not blocked
                idempotencyService.clearProcessed(event.getJobId(), event.getEventType());
                // Re-process immediately after backoff delay
                // In Day 6 we'll send to DLQ instead for cleaner separation
                processJob(event);
            } else {
                // ── MAX RETRIES EXHAUSTED → PUBLISH TO DLQ ──────────────
                log.error("Max retries exhausted, publishing to DLQ: jobId={}", job.getJobId());

                DlqEvent dlqEvent = DlqEvent.builder()
                        .jobId(job.getJobId())
                        .tenantId(job.getTenantId())
                        .title(job.getTitle())
                        .description(job.getDescription())
                        .failureReason(e.getMessage())
                        .retryCount(job.getRetryCount())
                        .failedAt(LocalDateTime.now())
                        .originalEventType(event.getEventType())
                        .build();

                dlqProducer.publishToDlq(dlqEvent);

                // Mark idempotency so this event is never re-processed
                idempotencyService.markAsProcessed(event.getJobId(), event.getEventType());

                log.error("Job permanently failed after {} retries: jobId={}",
                        RetryService.MAX_RETRIES, job.getJobId());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Job processing interrupted: jobId={}", job.getJobId());
        }
    }

    /**
     * Simulates a 30% chance of failure.
     * Replace this with real processing logic in production.
     *
     * Real examples of what this represents:
     * - External API returning 500
     * - Database connection timeout
     * - Out of memory during file processing
     */
    private void simulateRandomFailure() {
        if (random.nextDouble() < 0.30) { //change it to 1.0 to simulate process 100% failure
            throw new SimulatedProcessingException("Simulated processing failure (30% chance)");
        }
    }

    /**
     * Custom exception to distinguish simulated failures from
     * real infrastructure errors like InterruptedException.
     */
    static class SimulatedProcessingException extends RuntimeException {
        public SimulatedProcessingException(String message) {
            super(message);
        }
    }
}