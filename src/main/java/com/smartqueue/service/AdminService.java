package com.smartqueue.service;

import com.smartqueue.entity.AuditLog;
import com.smartqueue.entity.JobInstance;
import com.smartqueue.entity.JobStatus;
import com.smartqueue.kafka.JobEvent;
import com.smartqueue.kafka.JobProducer;
import com.smartqueue.repository.AuditLogRepository;
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
public class AdminService {

    private final JobRepository jobRepository;
    private final JobProducer jobProducer;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void replayJob(UUID jobId, AuthenticatedUser admin) {
        JobInstance job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        log.info("Replaying job: jobId={}, currentStatus={}, retryCount={}",
                job.getJobId(), job.getStatus(), job.getRetryCount());

        if (job.getStatus() != JobStatus.FAILED) {
            throw new IllegalStateException(
                    "Only FAILED jobs can be replayed. Current status: " + job.getStatus());
        }

        JobStatus oldStatus = job.getStatus();

        job.setStatus(JobStatus.PENDING);
        job.setRetryCount(0);
        jobRepository.save(job);

        idempotencyService.clearProcessed(jobId, "JOB_CREATED");

        JobEvent replayEvent = JobEvent.builder()
                .jobId(job.getJobId())
                .tenantId(job.getTenantId())
                .title(job.getTitle())
                .description(job.getDescription())
                .status(JobStatus.PENDING)
                .eventType("JOB_REPLAYED")
                .occurredAt(LocalDateTime.now())
                .build();

        jobProducer.publishJobEvent(replayEvent);

        // Audit log — who triggered the replay and from what state
        auditService.logUserAction(
                admin,
                "JOB_REPLAYED",
                jobId.toString(),
                oldStatus.name(),
                JobStatus.PENDING.name(),
                "Admin replay triggered by: " + admin.username()
        );

        log.info("Job replayed: jobId={}, triggeredBy={}", jobId, admin.username());
    }

    /**
     * Returns all audit logs for the admin's tenant, newest first.
     * Tenant-scoped — admins can only see their own tenant's audit trail.
     */
    public List<AuditLog> getAuditLogs(String tenantId) {
        return auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    /**
     * Returns audit logs for a specific job — useful for debugging.
     * Shows every state transition and action for a single job.
     */
    public List<AuditLog> getAuditLogsForJob(UUID jobId) {
        return auditLogRepository.findByEntityIdOrderByCreatedAtDesc(jobId.toString());
    }
}