package com.smartqueue.entity;

/**
 * Represents the lifecycle states of a job.
 * Day 1: We start with PENDING only.
 * Day 3: We'll add PROCESSING and COMPLETED via state machine.
 */
public enum JobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}