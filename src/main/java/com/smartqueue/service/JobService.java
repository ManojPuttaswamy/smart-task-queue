package com.smartqueue.service;

import com.smartqueue.dto.JobRequest;
import com.smartqueue.entity.JobInstance;
import com.smartqueue.kafka.JobEvent;
import com.smartqueue.kafka.JobProducer;
import com.smartqueue.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service layer contains all business logic.
 *
 * Day 2 change: after saving the job to DB, we now publish a Kafka event.
 * This is the "transactional outbox" pattern in simple form —
 * save first, then publish. Day 6 will make this more reliable.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobProducer jobProducer;      // injected by Spring

    /**
     * Creates a new job, saves to PostgreSQL, then publishes to Kafka.
     */
    @Transactional
    public JobInstance createJob(JobRequest request, String tenantId) {
        log.info("Creating job: title='{}', tenant='{}'", request.getTitle(), tenantId);

        JobInstance job = JobInstance.builder()
                .tenantId(tenantId)
                .title(request.getTitle())
                .description(request.getDescription())
                .build();

        JobInstance saved = jobRepository.save(job);
        log.info("Job saved to DB: jobId={}, status={}", saved.getJobId(), saved.getStatus());

        // Build the Kafka event from the saved entity
        JobEvent event = JobEvent.builder()
                .jobId(saved.getJobId())
                .tenantId(saved.getTenantId())
                .title(saved.getTitle())
                .description(saved.getDescription())
                .status(saved.getStatus())
                .eventType("JOB_CREATED")
                .occurredAt(LocalDateTime.now())
                .build();

        // Publish to Kafka — async, won't block the response
        jobProducer.publishJobEvent(event);

        return saved;
    }

    /**
     * Retrieves a job by ID.
     */
    @Transactional(readOnly = true)
    public JobInstance getJob(UUID jobId, String tenantId) {
        return jobRepository.findByJobIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
    }

    /**
     * Returns all jobs for a given tenant.
     */
    @Transactional(readOnly = true)
    public List<JobInstance> getJobsByTenant(String tenantId) {
        return jobRepository.findByTenantId(tenantId);
    }
}