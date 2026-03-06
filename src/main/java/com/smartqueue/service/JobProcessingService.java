package com.smartqueue.service;

import com.smartqueue.entity.JobInstance;
import com.smartqueue.entity.JobStatus;
import com.smartqueue.kafka.JobEvent;
import com.smartqueue.repository.JobRepository;
import com.smartqueue.statemachine.JobStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles job processing with full Redis-based idempotency.
 *
 * Flow:
 * 1. Check Redis — if key exists, this is a duplicate → skip
 * 2. Process the job (PENDING → PROCESSING → COMPLETED)
 * 3. Store idempotency key in Redis with 24hr TTL
 * 4. If processing fails → clear Redis key so retry is allowed
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobProcessingService {

    private final JobRepository jobRepository;
    private final JobStateMachine jobStateMachine;
    private final IdempotencyService idempotencyService;

    @Transactional
    public void processJob(JobEvent event) {

        // Step 1: Redis idempotency check — BEFORE touching the DB
        // This replaces the DB status check we used in Day 3
        if (idempotencyService.isAlreadyProcessed(event.getJobId(), event.getEventType())) {
            log.info("Skipping duplicate event: jobId={}, eventType={}",
                    event.getJobId(), event.getEventType());
            return;
        }

        // Step 2: Fetch job from DB
        JobInstance job = jobRepository.findById(event.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + event.getJobId()));

        log.info("Processing job: jobId={}, currentStatus={}", job.getJobId(), job.getStatus());

        // Safety net — if Redis key was cleared but job already completed
        if (job.getStatus() != JobStatus.PENDING) {
            log.warn("Job not in PENDING state, skipping: jobId={}, status={}",
                    job.getJobId(), job.getStatus());
            // Re-mark as processed so we don't keep retrying
            idempotencyService.markAsProcessed(event.getJobId(), event.getEventType());
            return;
        }

        try {
            // Step 3: PENDING → PROCESSING
            job.setStatus(jobStateMachine.transition(job.getStatus(), JobStatus.PROCESSING));
            jobRepository.save(job);
            log.info("Job status updated to PROCESSING: jobId={}", job.getJobId());

            // Step 4: Simulate actual work
            log.info("Simulating work for jobId={}...", job.getJobId());
            Thread.sleep(500);

            // Step 5: PROCESSING → COMPLETED
            job.setStatus(jobStateMachine.transition(job.getStatus(), JobStatus.COMPLETED));
            jobRepository.save(job);
            log.info("Job completed successfully: jobId={}", job.getJobId());

            // Step 6: Mark as processed in Redis AFTER successful completion
            // This is intentionally LAST — if we crash before this line,
            // Kafka will retry and we'll process again (acceptable — better than losing the job)
            idempotencyService.markAsProcessed(event.getJobId(), event.getEventType());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Job processing interrupted: jobId={}", job.getJobId());
            // Don't mark as processed — allow retry
        } catch (IllegalStateException e) {
            log.error("Invalid state transition for jobId={}: {}", job.getJobId(), e.getMessage());
            // Don't mark as processed — allow retry
        } catch (Exception e) {
            log.error("Unexpected error processing jobId={}: {}", job.getJobId(), e.getMessage());
            // Don't mark as processed — allow retry
        }
    }
}