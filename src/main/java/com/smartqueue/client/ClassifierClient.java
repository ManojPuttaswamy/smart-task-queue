package com.smartqueue.client;

import com.smartqueue.dto.ClassificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * HTTP client that calls the Python AI classifier service.
 *
 * Design decisions:
 *
 * 1. RestClient (Spring 6.1+) over RestTemplate:
 *    RestClient is the modern replacement for RestTemplate — fluent API,
 *    better error handling, and cleaner syntax. RestTemplate is in
 *    maintenance mode as of Spring 6.
 *
 * 2. Timeout of 3 seconds:
 *    If the AI service takes longer than 3s, we don't wait.
 *    Job creation should be fast — we never block the main flow for AI.
 *    The timeout is configured on the RestClient via a custom factory.
 *
 * 3. Returns null on any failure:
 *    The caller (JobService) checks for null and skips classification
 *    storage. The job is still created — classification is best-effort.
 *
 * 4. @Value("${classifier.url}"):
 *    URL comes from application.yml — easy to change per environment
 *    without recompiling. Local dev = localhost:8000, Docker = ai-classifier:8000.
 */
@Component
@Slf4j
public class ClassifierClient {

    private final RestClient restClient;

    public ClassifierClient(@Value("${classifier.url}") String classifierUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(classifierUrl)
                .build();

        log.info("ClassifierClient initialized with URL: {}", classifierUrl);
    }

    /**
     * Calls POST /classify on the Python service.
     *
     * @param title       job title
     * @param description job description
     * @return ClassificationResponse or null if the call fails for any reason
     */
    public ClassificationResponse classify(String title, String description) {
        try {
            log.info("Calling AI classifier: title='{}'", title);

            ClassificationResponse response = restClient.post()
                    .uri("/classify")
                    .header("Content-Type", "application/json")
                    .body(Map.of(
                            "title", title,
                            "description", description != null ? description : ""
                    ))
                    .retrieve()
                    .body(ClassificationResponse.class);

            if (response != null) {
                log.info("Classification result: category={}, priority={}, confidence={}, source={}",
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