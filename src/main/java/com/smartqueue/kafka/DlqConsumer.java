package com.smartqueue.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to the DLQ topic and logs failed jobs.
 *
 * For now this just logs — in a real system this could:
 * - Send a Slack/PagerDuty alert
 * - Write to an audit table
 * - Trigger an email notification to the tenant
 * - Feed a monitoring dashboard
 *
 * The key point is that failed jobs are VISIBLE here,
 * not silently dropped.
 */
@Component
@Slf4j
public class DlqConsumer {

    @KafkaListener(
            topics = "${kafka.topics.job-dlq}",
            groupId = "dlq-monitor",
            containerFactory = "dlqKafkaListenerContainerFactory"
    )
    public void consume(DlqEvent event) {
        log.error("=== JOB IN DLQ ===");
        log.error("jobId        : {}", event.getJobId());
        log.error("tenantId     : {}", event.getTenantId());
        log.error("title        : {}", event.getTitle());
        log.error("failureReason: {}", event.getFailureReason());
        log.error("retryCount   : {}", event.getRetryCount());
        log.error("failedAt     : {}", event.getFailedAt());
        log.error("==================");

        // Day 10: we'll also write this to the audit_log table here
    }
}