package com.smartqueue.service;

import com.smartqueue.entity.JobInstance;
import com.smartqueue.entity.JobStatus;
import com.smartqueue.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages retry logic with exponential backoff.
 *
 * Exponential backoff means each retry waits LONGER than the previous:
 *   Attempt 1 → wait 2s  (2^1)
 *   Attempt 2 → wait 4s  (2^2)
 *   Attempt 3 → wait 8s  (2^3)
 *
 * Why exponential and not fixed delay?
 * - Fixed delay (retry every 2s) hammers the system if it's struggling
 * - Exponential backoff gives the system increasing time to recover
 * - Standard pattern used by AWS, GCP, and every major distributed system
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RetryService {

    private final JobRepository jobRepository;

    public static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 2000;  // 2 seconds base

    /**
     * Calculates how long to wait before the next retry.
     * Formula: BASE_DELAY * 2^attempt
     *
     * attempt=1 → 2000 * 2^1 = 4000ms (4s)  -- wait, let's use attempt 0-indexed
     * retryCount=0 → 2000 * 2^0 = 2000ms (2s)
     * retryCount=1 → 2000 * 2^1 = 4000ms (4s)
     * retryCount=2 → 2000 * 2^2 = 8000ms (8s)
     */
    public long calculateBackoffMs(int retryCount) {
        return BASE_DELAY_MS * (long) Math.pow(2, retryCount);
    }

    /**
     * Returns true if the job has more retries remaining.
     */
    public boolean canRetry(int retryCount) {
        return retryCount < MAX_RETRIES;
    }

    /**
     * Handles a failed job attempt:
     * - If retries remain → increment counter, wait, return true (caller should retry)
     * - If max retries hit → mark job as FAILED, return false
     */
    @Transactional
    public boolean handleFailure(JobInstance job, Exception cause) {
        int currentRetryCount = job.getRetryCount();

        log.warn("Job processing failed: jobId={}, attempt={}/{}, reason={}",
                job.getJobId(),
                currentRetryCount + 1,
                MAX_RETRIES,
                cause.getMessage());

        if (canRetry(currentRetryCount)) {
            // Increment retry count and save back to DB
            job.setRetryCount(currentRetryCount + 1);
            job.setStatus(JobStatus.PENDING);   // reset to PENDING so it can be retried
            jobRepository.save(job);

            long backoffMs = calculateBackoffMs(currentRetryCount);
            log.info("Retrying job: jobId={}, attempt={}/{}, backoff={}ms",
                    job.getJobId(),
                    job.getRetryCount(),
                    MAX_RETRIES,
                    backoffMs);

            // Apply the backoff delay
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return true;  // caller should retry

        } else {
            // Max retries exhausted — mark as FAILED
            job.setStatus(JobStatus.FAILED);
            job.setRetryCount(currentRetryCount + 1);
            jobRepository.save(job);

            log.error("Job permanently failed after {} attempts: jobId={}",
                    MAX_RETRIES, job.getJobId());

            return false;  // caller should NOT retry, send to DLQ (Day 6)
        }
    }
}