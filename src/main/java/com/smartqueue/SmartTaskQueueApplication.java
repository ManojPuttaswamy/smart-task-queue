package com.smartqueue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @EnableAsync activates Spring's async task execution.
 * Without this, @Async annotations are silently ignored —
 * methods run synchronously on the calling thread.
 *
 * Spring creates a default thread pool (SimpleAsyncTaskExecutor)
 * for @Async tasks. For production, define a custom ThreadPoolTaskExecutor
 * bean to control pool size and queue capacity.
 */
@SpringBootApplication
@EnableAsync
public class SmartTaskQueueApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartTaskQueueApplication.class, args);
    }
}