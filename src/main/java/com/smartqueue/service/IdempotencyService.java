package com.smartqueue.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages idempotency keys in Redis to prevent duplicate event processing.
 *
 * How it works:
 * - Each event gets a unique key: "idempotency:{jobId}:{eventType}"
 * - Before processing: check if key exists in Redis
 * - If yes  → duplicate, skip
 * - If no   → process, then store key with TTL
 *
 * Why Redis and not PostgreSQL?
 * - Redis is an in-memory store → microsecond lookups
 * - Built-in TTL support → keys auto-expire, no cleanup needed
 * - SET NX (set if not exists) is atomic → no race conditions
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${redis.idempotency.ttl-hours}")
    private long ttlHours;

    private static final String KEY_PREFIX = "idempotency:";

    /**
     * Builds the idempotency key for a given job event.
     * Format: "idempotency:{jobId}:{eventType}"
     * Example: "idempotency:7f3a1b2c-...:JOB_CREATED"
     */
    public String buildKey(UUID jobId, String eventType) {
        return KEY_PREFIX + jobId + ":" + eventType;
    }

    /**
     * Checks if this event has already been processed.
     */
    public boolean isAlreadyProcessed(UUID jobId, String eventType) {
        String key = buildKey(jobId, eventType);
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            log.info("Duplicate event detected in Redis: jobId={}, eventType={}", jobId, eventType);
            return true;
        }
        return false;
    }

    /**
     * Marks this event as processed by storing the key in Redis with a TTL.
     *
     * After ttlHours the key auto-expires — this is fine because:
     * - If the same event arrives after 24hrs, it's almost certainly a new
     *   legitimate event, not a retry
     * - Keeps Redis memory usage bounded
     */
    public void markAsProcessed(UUID jobId, String eventType) {
        String key = buildKey(jobId, eventType);
        redisTemplate.opsForValue().set(key, "processed", ttlHours, TimeUnit.HOURS);
        log.debug("Marked event as processed in Redis: key={}, ttl={}h", key, ttlHours);
    }

    /**
     * Removes the idempotency key — useful if processing fails
     * and we want to allow a retry.
     */
    public void clearProcessed(UUID jobId, String eventType) {
        String key = buildKey(jobId, eventType);
        redisTemplate.delete(key);
        log.debug("Cleared idempotency key from Redis: key={}", key);
    }
}