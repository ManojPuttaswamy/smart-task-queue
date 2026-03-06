package com.smartqueue.service;

import com.smartqueue.dto.JobRequest;
import com.smartqueue.entity.JobInstance;
import com.smartqueue.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service layer contains all business logic.
 * Controllers should be thin — they just delegate here.
 *
 * @Slf4j        → Lombok gives us a 'log' variable for free
 * @Transactional → wraps DB operations in a transaction automatically
 * @RequiredArgsConstructor → Lombok generates constructor for all final fields
 *                            (this is how Spring injects the repository)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;

    /**
     * Creates a new job and saves it to PostgreSQL.
     * Status defaults to PENDING (set in the entity builder default).
     */
    @Transactional
    public JobInstance createJob(JobRequest request) {
        log.info("Creating job: title='{}', tenant='{}'", request.getTitle(), request.getTenantId());

        JobInstance job = JobInstance.builder()
                .tenantId(request.getTenantId())
                .title(request.getTitle())
                .description(request.getDescription())
                .build();

        JobInstance saved = jobRepository.save(job);

        log.info("Job saved successfully: jobId={}, status={}", saved.getJobId(), saved.getStatus());

        //we'll publish a Kafka event right here after saving
        return saved;
    }

    /**
     * Retrieves a job by ID.
     * Throws if not found — controller will handle the 404.
     */
    @Transactional(readOnly = true)
    public JobInstance getJob(UUID jobId) {
        return jobRepository.findById(jobId)
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