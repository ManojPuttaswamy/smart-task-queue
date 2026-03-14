package com.smartqueue.client;

import com.smartqueue.dto.ClassificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * HTTP client that calls the Python AI classifier service.
 *
 * Design decisions:
 *
 * 1. RestClient (Spring 6.1+) over RestTemplate:
 * RestClient is the modern replacement for RestTemplate — fluent API,
 * better error handling, and cleaner syntax. RestTemplate is in
 * maintenance mode as of Spring 6.
 *
 * 2. Timeout of 3 seconds:
 * If the AI service takes longer than 3s, we don't wait.
 * Job creation should be fast — we never block the main flow for AI.
 * The timeout is configured on the RestClient via a custom factory.
 *
 * 3. Returns null on any failure:
 * The caller (JobService) checks for null and skips classification
 * storage. The job is still created — classification is best-effort.
 *
 * 4. @Value("${classifier.url}"):
 * URL comes from application.yml — easy to change per environment
 * without recompiling. Local dev = localhost:8000, Docker = ai-classifier:8000.
 */
@Component
@Slf4j
public class ClassifierClient {

    private final RestClient restClient;

    public ClassifierClient(
            @Value("${classifier.url}") String classifierUrl,
            @Value("${classifier.timeout-ms:3000}") int timeoutMs) {

        // SimpleClientHttpRequestFactory lets us configure connect + read timeouts
        // This is the standard way to add timeouts to Spring's RestClient
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(classifierUrl)
                .requestFactory(factory)
                .build();

        log.info("ClassifierClient initialized: url={}, timeout={}ms", classifierUrl, timeoutMs);
    }

    /**
     * Calls POST /classify on the Python service.
     * Passes correlationId as X-Correlation-ID header so the Python service
     * can log it and tie its logs to the Java logs for the same job.
     * Returns null on ANY failure — timeout, network error, bad response.
     * Caller must handle null gracefully.
     */
    public ClassificationResponse classify(String title, String description) {
        // Get correlationId from MDC — it was set by the async classification thread
        String correlationId = org.slf4j.MDC.get("correlationId");

        try {
            log.info("Calling AI classifier: title='{}'", title);

            long start = System.currentTimeMillis();

            var requestSpec = restClient.post()
                    .uri("/classify")
                    .header("Content-Type", "application/json");

            // Forward correlationId to Python service so its logs are traceable
            if (correlationId != null) {
                requestSpec = requestSpec.header("X-Correlation-ID", correlationId);
            }

            ClassificationResponse response = requestSpec
                    .body(Map.of(
                            "title", title,
                            "description", description != null ? description : ""))
                    .retrieve()
                    .body(ClassificationResponse.class);

            long elapsed = System.currentTimeMillis() - start;

            if (response != null) {
                log.info("Classification result ({}ms): category={}, priority={}, confidence={}, source={}",
                        elapsed,
                        response.getCategory(),
                        response.getPriority(),
                        response.getConfidence(),
                        response.getSource());
            }

            return response;

        } catch (Exception e) {
            // Never let classifier failure break job creation
            // Log the error and return null — caller handles gracefully
            log.error("AI classifier call failed: {}. Job will be created without classification.", e.getMessage());
            return null;
        }
    }
}