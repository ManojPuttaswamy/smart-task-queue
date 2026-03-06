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
 * Handles the actual job processing logic in a proper Spring-managed transaction.
 *
 * Why a separate class from JobConsumer?
 * Spring's @Transactional works via proxies — when method A calls method B
 * on the SAME object (this.processJob()), Spring's proxy is bypassed and
 * @Transactional is silently ignored. This is a classic Spring gotcha.
 *
 * By moving processJob into its own @Service bean, Spring wraps it in a
 * real proxy, so @Transactional works correctly.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobProcessingService {

    private final JobRepository jobRepository;
    private final JobStateMachine jobStateMachine;

    /**
     * Processes a job event — transitions it from PENDING → PROCESSING → COMPLETED.
     *
     * @Transactional here keeps the JobInstance entity "managed" throughout
     * the entire method — both saves share the same JPA session, so the
     * @Version field is tracked correctly and never goes stale.
     */
    @Transactional
    public void processJob(JobEvent event) {
        // Always fetch fresh from DB inside the transaction
        JobInstance job = jobRepository.findById(event.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + event.getJobId()));

        log.info("Processing job: jobId={}, currentStatus={}", job.getJobId(), job.getStatus());

        // Idempotency guard — skip if already processed (handles Kafka retries)
        // Full Redis-based idempotency comes in Day 4
        if (job.getStatus() != JobStatus.PENDING) {
            log.info("Job already processed, skipping: jobId={}, status={}",
                    job.getJobId(), job.getStatus());
            return;
        }

        try {
            // PENDING → PROCESSING
            job.setStatus(jobStateMachine.transition(job.getStatus(), JobStatus.PROCESSING));
            // No need to call save() explicitly here — entity is managed,
            // JPA will flush the change automatically within the transaction.
            // But we call it explicitly for clarity and to log the step.
            jobRepository.save(job);
            log.info("Job status updated to PROCESSING: jobId={}", job.getJobId());

            // Simulate actual work
            log.info("Simulating work for jobId={}...", job.getJobId());
            Thread.sleep(500);

            // PROCESSING → COMPLETED
            job.setStatus(jobStateMachine.transition(job.getStatus(), JobStatus.COMPLETED));
            jobRepository.save(job);
            log.info("Job completed successfully: jobId={}", job.getJobId());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Job processing interrupted: jobId={}", job.getJobId());
        } catch (IllegalStateException e) {
            log.error("Invalid state transition for jobId={}: {}", job.getJobId(), e.getMessage());
        }
    }
}