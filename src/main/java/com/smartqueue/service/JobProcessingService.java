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

@Service
@Slf4j
@RequiredArgsConstructor
public class JobProcessingService {

    private final JobRepository jobRepository;
    private final JobStateMachine jobStateMachine;
    private final IdempotencyService idempotencyService;
    private final RetryService retryService;
    private final DlqProducer dlqProducer;
    private final AuditService auditService;  
    private final MetricsService metricService;

    private final Random random = new Random();

    @Transactional
    public void processJob(JobEvent event) {

        if (idempotencyService.isAlreadyProcessed(event.getJobId(), event.getEventType())) {
            log.info("Skipping duplicate event: jobId={}, eventType={}",
                    event.getJobId(), event.getEventType());
            return;
        }

        JobInstance job = jobRepository.findById(event.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + event.getJobId()));

        log.info("Processing job: jobId={}, currentStatus={}, retryCount={}",
                job.getJobId(), job.getStatus(), job.getRetryCount());

        if (job.getStatus() != JobStatus.PENDING) {
            log.warn("Job not in PENDING state, skipping: jobId={}, status={}",
                    job.getJobId(), job.getStatus());
            idempotencyService.markAsProcessed(event.getJobId(), event.getEventType());
            return;
        }

        //record when we started (for latency)
        long startTimeMs = System.currentTimeMillis();

        try {
            // PENDING → PROCESSING
            JobStatus oldStatus = job.getStatus();
            job.setStatus(jobStateMachine.transition(job.getStatus(), JobStatus.PROCESSING));
            jobRepository.save(job);

            // add to active jobs metrics
            metricService.recordJobStarted();

            // Audit: system moved job to PROCESSING
            auditService.logSystemAction(
                    job.getTenantId(),
                    "JOB_STATUS_CHANGED",
                    job.getJobId().toString(),
                    oldStatus.name(),
                    job.getStatus().name(),
                    "Kafka consumer picked up job (attempt " + (job.getRetryCount() + 1) + ")"
            );

            Thread.sleep(500);
            simulateRandomFailure();

            // PROCESSING → COMPLETED
            oldStatus = job.getStatus();
            job.setStatus(jobStateMachine.transition(job.getStatus(), JobStatus.COMPLETED));
            jobRepository.save(job);

            // Audit: job completed successfully
            auditService.logSystemAction(
                    job.getTenantId(),
                    "JOB_STATUS_CHANGED",
                    job.getJobId().toString(),
                    oldStatus.name(),
                    job.getStatus().name(),
                    "Job completed successfully after " + (job.getRetryCount() + 1) + " attempt(s)"
            );

            idempotencyService.markAsProcessed(event.getJobId(), event.getEventType());

            //increment jobCompleted counter and record latency.
            metricService.incrementJobsCreated();
            metricService.recordJobProcessingTime(System.currentTimeMillis() - startTimeMs);

            log.info("Job completed successfully: jobId={}", job.getJobId());

        } catch (SimulatedProcessingException e) {
            job.setStatus(JobStatus.PENDING);
            jobRepository.save(job);

            boolean willRetry = retryService.handleFailure(job, e);

            if (willRetry) {
                idempotencyService.clearProcessed(event.getJobId(), event.getEventType());

                //count each retried jobs
                metricService.incrementJobsRetried();

                // Audit: retry attempt
                auditService.logSystemAction(
                        job.getTenantId(),
                        "JOB_RETRY",
                        job.getJobId().toString(),
                        JobStatus.PROCESSING.name(),
                        JobStatus.PENDING.name(),
                        "Processing failed, retry attempt " + job.getRetryCount() +
                                " of " + RetryService.MAX_RETRIES + ": " + e.getMessage()
                );

                processJob(event);
            } else {
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
                idempotencyService.markAsProcessed(event.getJobId(), event.getEventType());

                // Audit: job sent to DLQ
                auditService.logSystemAction(
                        job.getTenantId(),
                        "JOB_SENT_TO_DLQ",
                        job.getJobId().toString(),
                        JobStatus.PROCESSING.name(),
                        JobStatus.FAILED.name(),
                        "Max retries (" + RetryService.MAX_RETRIES + ") exhausted: " + e.getMessage()
                );

                //add metrics of failure, DLQ counter and latency
                metricService.incrementJobsFailed();
                metricService.incrementJobsSentToDlq();
                metricService.recordJobProcessingTime(System.currentTimeMillis() - startTimeMs);

                log.error("Job permanently failed after {} retries: jobId={}",
                        RetryService.MAX_RETRIES, job.getJobId());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Job processing interrupted: jobId={}", job.getJobId());
        }
    }

    private void simulateRandomFailure() {
        if (random.nextDouble() < 0.30) { //change it to 1.0 to simulate process 100% failure
            throw new SimulatedProcessingException("Simulated processing failure (30% chance)");
        }
    }

    static class SimulatedProcessingException extends RuntimeException {
        public SimulatedProcessingException(String message) {
            super(message);
        }
    }
}