package com.smartqueue.service;

import com.smartqueue.dto.ClassificationResponse;
import com.smartqueue.entity.JobInstance;
import com.smartqueue.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Runs AI classification on a background thread so POST /jobs returns instantly.
 *
 * Why @Async here and not in JobService?
 *
 * Self-invocation problem: if JobService called this.classifyAsync(), Spring's
 * proxy would be bypassed and @Async would be silently ignored — the method
 * would run on the same thread. By putting @Async in a SEPARATE bean
 * (AsyncClassificationService), the call goes through the Spring proxy and
 * @Async works correctly.
 *
 * This is the same self-invocation problem as @Transactional — both use
 * CGLIB proxies and both fail silently when called via this.method().
 *
 * Thread pool: Spring creates a default thread pool for @Async tasks.
 * For production, configure a custom executor in a @Configuration class
 * to control pool size, queue capacity, and thread naming.
 *
 * Flow with @Async:
 *   HTTP thread: save job → start async task → publish Kafka → return response (fast!)
 *   Background thread: call classifier → update DB with classification
 *
 * Tradeoff: the job response returned to the caller won't have classification
 * fields yet (they're null). The client would need to GET /jobs/{id} after
 * a moment to see the classification. This is acceptable for a non-critical
 * enrichment field.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncClassificationService {

    private final ClassificationCacheService classificationCacheService;
    private final JobRepository jobRepository;

    /**
     * Called from JobService after job is saved.
     * Runs on a Spring-managed background thread (not the HTTP request thread).
     *
     * @Async means: "put this on a background thread and return immediately"
     * The HTTP request doesn't wait for this to finish.
     */
    @Async
    @Transactional
    public void classifyAndUpdate(UUID jobId, String title, String description, String tenantId, String correlationId) {
        // Restore correlationId in MDC for this async thread.
        // Since @Async runs on a new thread, MDC is empty — we pass correlationId
        // explicitly from JobService so all async logs share the same trace ID.
        if (correlationId != null) {
            org.slf4j.MDC.put("correlationId", correlationId);
        }

        try {
            log.info("[ASYNC] Starting classification for jobId={}, title='{}'", jobId, title);

            // Call classifier (with Redis cache)
            ClassificationResponse classification = classificationCacheService.classify(title, description);

            if (classification == null) {
                log.warn("[ASYNC] Classification returned null for jobId={} — job stays unclassified", jobId);
                return;
            }

            // Fetch the job fresh from DB (it may have changed since we started)
            JobInstance job = jobRepository.findById(jobId).orElse(null);
            if (job == null) {
                log.error("[ASYNC] Job not found in DB: jobId={}", jobId);
                return;
            }

            // Update classification fields
            job.setCategory(classification.getCategory());
            job.setPriority(classification.getPriority());
            job.setConfidenceScore(classification.getConfidence());
            job.setClassificationSource(classification.getSource());
            jobRepository.save(job);

            log.info("[ASYNC] Job classified: jobId={}, category={}, priority={}, source={}",
                    jobId,
                    job.getCategory(),
                    job.getPriority(),
                    job.getClassificationSource());

        } catch (Exception e) {
            // Never let async classification crash — it's best-effort enrichment
            log.error("[ASYNC] Classification failed for jobId={}: {}", jobId, e.getMessage());
        } finally {
            // Always clean up MDC — async thread pool reuses threads
            org.slf4j.MDC.remove("correlationId");
        }
    }
}