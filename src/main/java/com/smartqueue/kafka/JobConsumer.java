package com.smartqueue.kafka;

import com.smartqueue.service.JobProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to the job-events Kafka topic.
 * Kept thin — just receives the message and delegates to JobProcessingService.
 *
 * Key Day 17 addition: extract correlationId from the event and set it in MDC.
 * This means every log line in the consumer thread — including all calls to
 * JobProcessingService — will automatically include correlationId=<uuid>.
 * This lets you grep one ID to trace a job from HTTP → Kafka → processing.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JobConsumer {

    private final JobProcessingService jobProcessingService;

    @KafkaListener(
            topics = "${kafka.topics.job-events}",
            groupId = "job-processor",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(JobEvent event) {
        // Restore the correlationId from the event into MDC.
        // The HTTP thread set this when the job was created — now we carry it
        // into the consumer thread so all downstream logs share the same ID.
        String correlationId = event.getCorrelationId();
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }

        try {
            log.info("Received job event: jobId={}, eventType={}, status={}",
                    event.getJobId(), event.getEventType(), event.getStatus());

        // Process JOB_CREATED (normal flow) and JOB_REPLAYED (admin replay from DLQ)
            if (!"JOB_CREATED".equals(event.getEventType()) &&
                !"JOB_REPLAYED".equals(event.getEventType())) {
                log.info("Skipping event type: {}", event.getEventType());
                return;
            }

            jobProcessingService.processJob(event);

        } finally {
            // Always clean up MDC — Kafka listener threads are reused
            MDC.remove("correlationId");
        }
    }
}