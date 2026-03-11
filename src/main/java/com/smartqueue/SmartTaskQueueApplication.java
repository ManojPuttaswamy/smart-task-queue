package com.smartqueue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @EnableAsync activates Spring's async task execution.
 * Required for @Async to work in AuditService.
 * Without this annotation, @Async methods run synchronously
 * and no warning is shown — easy bug to miss.
 */
@SpringBootApplication
@EnableAsync
public class SmartTaskQueueApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartTaskQueueApplication.class, args);
    }
}