package com.smartqueue.service;

import com.smartqueue.entity.JobInstance;
import com.smartqueue.entity.JobStatus;
import com.smartqueue.kafka.JobEvent;
import com.smartqueue.kafka.JobProducer;
import com.smartqueue.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles admin operations — currently just DLQ replay.
 *
 * Replay flow:
 *   1. Fetch job from DB
 *   2. Validate it's in FAILED state (can't replay a PENDING or COMPLETED job)
 *   3. Reset retryCount to 0 and status back to PENDING
 *   4. Clear Redis idempotency key so event won't be skipped
 *   5. Re-publish a fresh JobEvent to job-events topic
 *
 * After this, the normal consumer picks it up and processes it
 * exactly like a brand new job — full retry budget of 3 attempts.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminService {

    private final JobRepository jobRepository;
    private final JobProducer jobProducer;
    private final IdempotencyService idempotencyService;

    @Transactional
    public void replayJob(UUID jobId) {
        // 1. Fetch job — throw if not found
        JobInstance job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        log.info("Replaying job: jobId={}, currentStatus={}, retryCount={}",
                job.getJobId(), job.getStatus(), job.getRetryCount());

        // 2. Validate — only FAILED jobs can be replayed
        if (job.getStatus() != JobStatus.FAILED) {
            throw new IllegalStateException(
                    "Only FAILED jobs can be replayed. Current status: " + job.getStatus());
        }

        // 3. Reset job state — give it a fresh slate
        job.setStatus(JobStatus.PENDING);
        job.setRetryCount(0);
        jobRepository.save(job);
        log.info("Job reset to PENDING with retryCount=0: jobId={}", jobId);

        // 4. Clear Redis idempotency key — otherwise the consumer will skip this event
        //    because the old failed processing already set it
        idempotencyService.clearProcessed(jobId, "JOB_CREATED");
        log.info("Idempotency key cleared for replay: jobId={}", jobId);

        // 5. Re-publish to job-events topic
        JobEvent replayEvent = JobEvent.builder()
                .jobId(job.getJobId())
                .tenantId(job.getTenantId())
                .title(job.getTitle())
                .description(job.getDescription())
                .status(JobStatus.PENDING)
                .eventType("JOB_REPLAYED")    // distinct event type so logs are clear
                .occurredAt(LocalDateTime.now())
                .build();

        jobProducer.publishJobEvent(replayEvent);

        log.info("Job re-published to job-events topic: jobId={}, eventType=JOB_REPLAYED", jobId);
    }
}