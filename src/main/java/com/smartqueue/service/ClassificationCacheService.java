package com.smartqueue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.client.ClassifierClient;
import com.smartqueue.dto.ClassificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Wraps ClassifierClient with Redis caching.
 *
 * Why cache classifications?
 *
 * 1. Cost: OpenAI charges per token. If 100 jobs all say "Database connection
 *    timeout", we call OpenAI 100 times for the same answer. With caching,
 *    we call it once and serve the rest from Redis.
 *
 * 2. Speed: Redis lookup = ~1ms. OpenAI call = ~1-2s. Cached responses
 *    are 1000x faster.
 *
 * 3. Resilience: if OpenAI is down temporarily, cached results still work
 *    for jobs with titles we've seen before.
 *
 * Cache key: "classification:{title_lowercase}"
 *   - Lowercased so "Database Timeout" and "database timeout" hit the same cache
 *   - Title only (not description) — titles are short and descriptive,
 *     descriptions vary too much to be good cache keys
 *
 * TTL: 1 hour
 *   - Classifications don't change — "Database timeout" will always be
 *     category=Database, priority=HIGH
 *   - 1 hour is conservative; could be 24 hours in production
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClassificationCacheService {

    private final ClassifierClient classifierClient;
    private final MetricsService metricsService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "classification:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    /**
     * Returns classification for the given title+description.
     * Checks Redis first — calls Python service only on cache miss.
     */
    public ClassificationResponse classify(String title, String description) {
        String cacheKey = CACHE_PREFIX + title.toLowerCase().trim();

        // Step 1: Check Redis cache
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("Classification cache HIT: title='{}', key={}", title, cacheKey);
                metricsService.incrementCacheHits();
                ClassificationResponse response = objectMapper.readValue(cached, ClassificationResponse.class);
                // Mark it clearly as coming from cache
                response.setSource(response.getSource() + "_CACHED");
                return response;
            }
        } catch (Exception e) {
            // Redis down? Just fall through to the real classifier
            log.warn("Redis cache read failed: {}. Calling classifier directly.", e.getMessage());
        }

        // Step 2: Cache MISS — call the Python classifier
        log.info("Classification cache MISS: title='{}'. Calling AI service.", title);
        metricsService.incrementCacheMisses();
        ClassificationResponse response = classifierClient.classify(title, description);

        // Step 3: Store result in Redis (only if classifier succeeded)
        if (response != null) {
            try {
                String json = objectMapper.writeValueAsString(response);
                redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
                log.info("Classification cached: key={}, ttl={}h", cacheKey, CACHE_TTL.toHours());
            } catch (Exception e) {
                // Failed to cache — not a problem, just means next call won't be cached
                log.warn("Failed to cache classification result: {}", e.getMessage());
            }
        }

        return response;
    }
}