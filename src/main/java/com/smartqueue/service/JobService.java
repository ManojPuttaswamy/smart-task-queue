package com.smartqueue.service;

import com.smartqueue.dto.JobRequest;
import com.smartqueue.entity.JobInstance;
import com.smartqueue.kafka.JobEvent;
import com.smartqueue.kafka.JobProducer;
import com.smartqueue.repository.JobRepository;
import com.smartqueue.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobProducer jobProducer;
    private final AuditService auditService;

    /**
     * Creates a new job, saves to PostgreSQL, publishes to Kafka,
     * and writes an audit log entry.
     *
     * Audit log is written @Async — it doesn't block the HTTP response.
     */
    @Transactional
    public JobInstance createJob(JobRequest request, String tenantId, AuthenticatedUser user) {
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

        // Audit log — async, doesn't block response
        auditService.logUserAction(
                user,
                "JOB_CREATED",
                saved.getJobId().toString(),
                null,                           // no old state — this is a creation
                saved.getStatus().name(),
                "Job created: " + saved.getTitle()
        );

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