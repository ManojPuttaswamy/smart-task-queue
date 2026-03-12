package com.smartqueue.service;

import com.smartqueue.client.ClassifierClient;
import com.smartqueue.dto.ClassificationResponse;
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
    private final ClassifierClient classifierClient;

    /**
     * Creates a job, saves to DB, calls AI classifier, then publishes to Kafka.
     *
     * Flow:
     *   1. Save job to DB (status=PENDING, no classification yet)
     *   2. Call Python classifier — synchronous, max 3s timeout
     *   3. If classifier responds → update job with category/priority
     *   4. If classifier fails → job saved without classification (null fields)
     *   5. Publish Kafka event
     *   6. Write audit log
     *
     * Why call classifier AFTER saving?
     *   The job must exist in DB before we do anything else.
     *   If the classifier call fails, we still have the job saved —
     *   we don't lose the submission. Classification is best-effort.
     *
     * Why call classifier BEFORE Kafka publish?
     *   So the Kafka event (and anything that consumes it) already
     *   has the classification metadata on the job in DB.
     */
    @Transactional
    public JobInstance createJob(JobRequest request, String tenantId, AuthenticatedUser user) {
        log.info("Creating job: title='{}', tenant='{}'", request.getTitle(), tenantId);

        // Step 1: Save job to DB — no classification yet
        JobInstance job = JobInstance.builder()
                .tenantId(tenantId)
                .title(request.getTitle())
                .description(request.getDescription())
                .build();

        JobInstance saved = jobRepository.save(job);
        log.info("Job saved to DB: jobId={}, status={}", saved.getJobId(), saved.getStatus());

        // Step 2 & 3: Call AI classifier and store result
        ClassificationResponse classification = classifierClient.classify(
                saved.getTitle(),
                saved.getDescription()
        );

        if (classification != null) {
            saved.setCategory(classification.getCategory());
            saved.setPriority(classification.getPriority());
            saved.setConfidenceScore(classification.getConfidence());
            saved.setClassificationSource(classification.getSource());
            jobRepository.save(saved);

            log.info("Job classified: jobId={}, category={}, priority={}, source={}",
                    saved.getJobId(),
                    saved.getCategory(),
                    saved.getPriority(),
                    saved.getClassificationSource());
        } else {
            // Classifier was down or timed out — job still created, just unclassified
            log.warn("Job created without classification (classifier unavailable): jobId={}", saved.getJobId());
        }

        // Step 4: Publish Kafka event
        JobEvent event = JobEvent.builder()
                .jobId(saved.getJobId())
                .tenantId(saved.getTenantId())
                .title(saved.getTitle())
                .description(saved.getDescription())
                .status(saved.getStatus())
                .eventType("JOB_CREATED")
                .occurredAt(LocalDateTime.now())
                .build();

        jobProducer.publishJobEvent(event);

        // Step 5: Audit log
        auditService.logUserAction(
                user,
                "JOB_CREATED",
                saved.getJobId().toString(),
                null,
                saved.getStatus().name(),
                "Job created: " + saved.getTitle() +
                        (classification != null ? " | classified as: " + saved.getCategory() + "/" + saved.getPriority() : " | unclassified")
        );

        return saved;
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