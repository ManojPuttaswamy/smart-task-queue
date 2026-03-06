package com.smartqueue.kafka;

import com.smartqueue.service.JobProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to the job-events Kafka topic.
 * Kept thin — just receives the message and delegates to JobProcessingService.
 *
 * Processing logic lives in JobProcessingService so @Transactional works
 * correctly via Spring's proxy mechanism.
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
        log.info("Received job event: jobId={}, eventType={}, status={}",
                event.getJobId(), event.getEventType(), event.getStatus());

        // Process JOB_CREATED (normal flow) and JOB_REPLAYED (admin replay from DLQ)
        if (!"JOB_CREATED".equals(event.getEventType()) &&
            !"JOB_REPLAYED".equals(event.getEventType())) {
            log.info("Skipping event type: {}", event.getEventType());
            return;
        }

        jobProcessingService.processJob(event);
    }
}