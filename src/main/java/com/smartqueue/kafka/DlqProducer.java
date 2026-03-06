package com.smartqueue.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.support.SendResult;

/**
 * Publishes failed jobs to the Dead Letter Queue topic.
 *
 * The DLQ is a safety net — jobs here are not lost, they're parked
 * and can be inspected and replayed by an admin at any time.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DlqProducer {

    private final KafkaTemplate<String, DlqEvent> dlqKafkaTemplate;

    @Value("${kafka.topics.job-dlq}")
    private String dlqTopic;

    public void publishToDlq(DlqEvent event) {
        log.warn("Publishing job to DLQ: jobId={}, reason={}, retryCount={}",
                event.getJobId(), event.getFailureReason(), event.getRetryCount());

        CompletableFuture<SendResult<String, DlqEvent>> future =
                dlqKafkaTemplate.send(dlqTopic, event.getJobId().toString(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("CRITICAL: Failed to publish to DLQ: jobId={}, error={}",
                        event.getJobId(), ex.getMessage());
            } else {
                log.info("Job published to DLQ successfully: jobId={}, partition={}, offset={}",
                        event.getJobId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}