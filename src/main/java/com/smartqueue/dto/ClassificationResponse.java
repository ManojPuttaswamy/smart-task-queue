package com.smartqueue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Maps the JSON response from the Python AI classifier service.
 *
 * Example response from POST http://localhost:8000/classify:
 * {
 *   "category": "Database",
 *   "priority": "HIGH",
 *   "confidence": 0.95,
 *   "recommended_action": "Check connection pool settings...",
 *   "source": "AI"
 * }
 *
 * @JsonProperty("recommended_action") maps the snake_case Python field
 * to the camelCase Java field. Spring's ObjectMapper handles this
 * automatically for most fields, but explicit mapping is clearer.
 */
@Data
public class ClassificationResponse {

    private String category;
    private String priority;
    private Double confidence;

    @JsonProperty("recommended_action")
    private String recommendedAction;

    private String source;  // "AI" or "FALLBACK"
}