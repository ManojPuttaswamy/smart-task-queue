package com.smartqueue.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Responsible for publishing job events to Kafka.
 *
 * KafkaTemplate<String, JobEvent> means:
 *   - Key type: String (we use jobId as the key)
 *   - Value type: JobEvent (serialized to JSON)
 *
 * Using jobId as the key ensures all events for the same job
 * always go to the same Kafka partition — preserving order.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JobProducer {

    private final KafkaTemplate<String, JobEvent> kafkaTemplate;

    // Reads the topic name from application.yml: kafka.topics.job-events
    @Value("${kafka.topics.job-events}")
    private String jobEventsTopic;

    /**
     * Publishes a JobEvent to the job-events Kafka topic.
     * The send is async — we register callbacks to log success or failure.
     */
    public void publishJobEvent(JobEvent event) {
        log.info("Publishing job event: jobId={}, eventType={}", event.getJobId(), event.getEventType());

        // Use jobId as the message key → guarantees ordering per job
        CompletableFuture<SendResult<String, JobEvent>> future =
                kafkaTemplate.send(jobEventsTopic, event.getJobId().toString(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish job event: jobId={}, error={}", event.getJobId(), ex.getMessage());
            } else {
                log.info("Job event published successfully: jobId={}, topic={}, partition={}, offset={}",
                        event.getJobId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}