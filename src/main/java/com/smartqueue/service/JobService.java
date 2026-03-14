package com.smartqueue.service;

import com.smartqueue.dto.JobRequest;
import com.smartqueue.entity.JobInstance;
import com.smartqueue.kafka.JobEvent;
import com.smartqueue.kafka.JobProducer;
import com.smartqueue.repository.JobRepository;
import com.smartqueue.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
    private final AsyncClassificationService asyncClassificationService;
    private final MetricsService metricsService;

    /**
     * Day 13: classification is now fully async.
     *
     * Old flow (Day 12 — synchronous):
     *   HTTP thread: save → classify (waits 1-2s) → publish Kafka → return
     *   User waits: ~2 seconds
     *
     * New flow (Day 13 — async):
     *   HTTP thread: save → fire async task → publish Kafka → return
     *   Background thread: classify → update DB
     *   User waits: ~50ms (just the DB save)
     *
     * Tradeoff: the response won't have classification fields yet.
     * They'll be populated within ~2s. Client can poll GET /jobs/{id}
     * to see the final classified result.
     */
    @Transactional
    public JobInstance createJob(JobRequest request, String tenantId, AuthenticatedUser user) {
        // Generate a unique correlationId for this job's entire lifecycle.
        // Every log line from this point on will include correlationId=<uuid>
        // so you can grep a single ID and trace the job across all threads/services.
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);

        try {
            log.info("Creating job: title='{}', tenant='{}'", request.getTitle(), tenantId);

            // Step 1: Save job to DB — include correlationId in the entity
            JobInstance job = JobInstance.builder()
                    .tenantId(tenantId)
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .correlationId(correlationId)
                    .build();

            JobInstance saved = jobRepository.save(job);
            log.info("Job saved to DB: jobId={}, status={}", saved.getJobId(), saved.getStatus());

            // Step 2: Fire async classification — pass correlationId so async thread
            // can restore it in MDC and all classification logs share the same trace ID
            asyncClassificationService.classifyAndUpdate(
                    saved.getJobId(),
                    saved.getTitle(),
                    saved.getDescription(),
                    saved.getTenantId(),
                    correlationId
            );

            // Step 3: Publish Kafka event — correlationId travels with the message
            JobEvent event = JobEvent.builder()
                    .jobId(saved.getJobId())
                    .tenantId(saved.getTenantId())
                    .title(saved.getTitle())
                    .description(saved.getDescription())
                    .status(saved.getStatus())
                    .eventType("JOB_CREATED")
                    .occurredAt(LocalDateTime.now())
                    .correlationId(correlationId)
                    .build();

            jobProducer.publishJobEvent(event);

            // Step 4: Audit log
            auditService.logUserAction(
                    user,
                    "JOB_CREATED",
                    saved.getJobId().toString(),
                    null,
                    saved.getStatus().name(),
                    "Job created: " + saved.getTitle() + " | correlationId=" + correlationId
            );

            metricsService.incrementJobsCreated();

            return saved;

        } finally {
            // Always clear MDC after the request — thread pool reuses threads
            // and stale MDC values would pollute future requests' logs
            MDC.remove("correlationId");
        }
    }

    @Transactional(readOnly = true)
    public JobInstance getJob(UUID jobId, String tenantId) {
        return jobRepository.findByJobIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
    }

    @Transactional(readOnly = true)
    public List<JobInstance> getJobsByTenant(String tenantId) {
        return jobRepository.findByTenantId(tenantId);
    }
}