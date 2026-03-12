package com.smartqueue.service;

import com.smartqueue.entity.JobStatus;
import com.smartqueue.repository.JobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central service for all custom Prometheus metrics.
 *
 * Why Micrometer?
 * Micrometer is the standard metrics facade for Java (like SLF4J for logging).
 * You write metrics code once against the Micrometer API and it works with
 * any backend: Prometheus, Datadog, CloudWatch, Graphite, etc.
 * Switching from Prometheus to Datadog = change one dependency, zero code changes.
 *
 * Metric types used:
 *
 * Counter  — monotonically increasing number. Never decreases.
 *            Use for: events that happen (jobs created, errors, retries)
 *            Prometheus query: rate(jobs_created_total[5m]) = jobs per second
 *
 * Timer    — measures duration of operations.
 *            Records: count, sum, max, and percentile buckets.
 *            Use for: latency of operations (job processing time)
 *            Prometheus query: histogram_quantile(0.95, job_processing_seconds_bucket)
 *
 * Gauge    — a value that goes up AND down.
 *            Use for: current state (active jobs, queue depth, memory usage)
 *            Prometheus query: active_jobs_count
 *
 * Naming convention: snake_case with unit suffix
 *   jobs_created_total    ← _total suffix for counters (Prometheus convention)
 *   job_processing_seconds ← _seconds suffix for timers
 *   active_jobs_count     ← descriptive name for gauges
 */
@Service
@Slf4j
public class MetricsService {

    private final MeterRegistry registry;
    private final JobRepository jobRepository;

    // Counters — increment on each event, never reset
    private Counter jobsCreatedCounter;
    private Counter jobsCompletedCounter;
    private Counter jobsFailedCounter;
    private Counter jobsRetriedCounter;
    private Counter jobsSentToDlqCounter;
    private Counter jobsReplayedCounter;
    private Counter classificationCacheHitsCounter;
    private Counter classificationCacheMissesCounter;

    // Timer — measures job processing latency
    private Timer jobProcessingTimer;

    // AtomicLong backing the active jobs Gauge
    // Gauge needs a reference to a number it can read at any time
    // AtomicLong is thread-safe — Gauge reads it from Prometheus scrape thread
    private final AtomicLong activeJobsCount = new AtomicLong(0);
    private final AtomicLong dlqSize = new AtomicLong(0);

    public MetricsService(MeterRegistry registry, JobRepository jobRepository) {
        this.registry = registry;
        this.jobRepository = jobRepository;
    }

    /**
     * @PostConstruct runs after Spring has injected all dependencies.
     * We register all metrics here so they appear in /actuator/prometheus
     * immediately at startup — even before any jobs are processed.
     *
     * Why register at startup?
     * If we only create the counter when the first job is created,
     * Prometheus won't see it until then. Dashboards show "no data"
     * instead of 0. Registering at startup gives you a clean baseline.
     */
    @PostConstruct
    public void initMetrics() {
        // ── Counters ────────────────────────────────────────────────────────
        jobsCreatedCounter = Counter.builder("jobs_created_total")
                .description("Total number of jobs submitted via POST /jobs")
                .register(registry);

        jobsCompletedCounter = Counter.builder("jobs_completed_total")
                .description("Total number of jobs successfully completed")
                .register(registry);

        jobsFailedCounter = Counter.builder("jobs_failed_total")
                .description("Total number of jobs that exhausted retries and moved to FAILED")
                .register(registry);

        jobsRetriedCounter = Counter.builder("jobs_retried_total")
                .description("Total number of retry attempts across all jobs")
                .register(registry);

        jobsSentToDlqCounter = Counter.builder("jobs_sent_to_dlq_total")
                .description("Total number of jobs sent to the Dead Letter Queue")
                .register(registry);

        jobsReplayedCounter = Counter.builder("jobs_replayed_total")
                .description("Total number of DLQ jobs replayed via admin API")
                .register(registry);

        classificationCacheHitsCounter = Counter.builder("classification_cache_hits_total")
                .description("Redis cache hits for AI classification (skipped OpenAI call)")
                .register(registry);

        classificationCacheMissesCounter = Counter.builder("classification_cache_misses_total")
                .description("Redis cache misses for AI classification (called OpenAI)")
                .register(registry);

        // ── Timer ────────────────────────────────────────────────────────────
        jobProcessingTimer = Timer.builder("job_processing_duration_seconds")
                .description("Time from job creation to completion (PENDING → COMPLETED)")
                .publishPercentiles(0.5, 0.95, 0.99)  // p50, p95, p99
                .register(registry);

        // ── Gauges ───────────────────────────────────────────────────────────
        // Gauge.builder takes a supplier — Prometheus calls this on every scrape
        Gauge.builder("active_jobs_count", activeJobsCount, AtomicLong::get)
                .description("Number of jobs currently in PROCESSING state")
                .register(registry);

        Gauge.builder("dlq_size", dlqSize, AtomicLong::get)
                .description("Number of jobs currently in the Dead Letter Queue (FAILED state)")
                .register(registry);

        // Initialize DLQ gauge from DB on startup
        refreshGaugesFromDb();

        log.info("Metrics registered: counters={}, timers={}, gauges={}",
                8, 1, 2);
    }

    /**
     * Called at startup to initialize gauge values from DB.
     * Prevents gauges from showing 0 after a restart when jobs already exist.
     */
    private void refreshGaugesFromDb() {
        try {
            long processing = jobRepository.countByStatus(JobStatus.PROCESSING);
            long failed = jobRepository.countByStatus(JobStatus.FAILED);
            activeJobsCount.set(processing);
            dlqSize.set(failed);
            log.info("Initialized gauges from DB: activeJobs={}, dlqSize={}", processing, failed);
        } catch (Exception e) {
            log.warn("Could not initialize gauges from DB: {}", e.getMessage());
        }
    }

    // ── Public increment methods ─────────────────────────────────────────────
    // Called from JobService, JobProcessingService, DlqProducer etc.

    public void incrementJobsCreated() {
        jobsCreatedCounter.increment();
    }

    public void incrementJobsCompleted() {
        jobsCompletedCounter.increment();
        activeJobsCount.decrementAndGet();
    }

    public void incrementJobsFailed() {
        jobsFailedCounter.increment();
        activeJobsCount.decrementAndGet();
    }

    public void incrementJobsRetried() {
        jobsRetriedCounter.increment();
    }

    public void incrementJobsSentToDlq() {
        jobsSentToDlqCounter.increment();
        dlqSize.incrementAndGet();
    }

    public void incrementJobsReplayed() {
        jobsReplayedCounter.increment();
        dlqSize.decrementAndGet();
    }

    public void incrementCacheHits() {
        classificationCacheHitsCounter.increment();
    }

    public void incrementCacheMisses() {
        classificationCacheMissesCounter.increment();
    }

    public void recordJobStarted() {
        activeJobsCount.incrementAndGet();
    }

    /**
     * Records how long a job took to process.
     *
     * @param durationMs milliseconds from PENDING to COMPLETED/FAILED
     */
    public void recordJobProcessingTime(long durationMs) {
        jobProcessingTimer.record(Duration.ofMillis(durationMs));
    }
}