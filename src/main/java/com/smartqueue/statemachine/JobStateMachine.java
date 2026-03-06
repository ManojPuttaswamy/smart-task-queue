package com.smartqueue.statemachine;

import com.smartqueue.entity.JobStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Enforces valid job state transitions.
 *
 * Why a state machine?
 * Without this, any code anywhere could set status to anything.
 * The state machine is the single source of truth for what transitions are allowed.
 *
 * Valid transitions:
 *   PENDING    → PROCESSING
 *   PROCESSING → COMPLETED
 *   PROCESSING → FAILED
 */
@Component
@Slf4j
public class JobStateMachine {

    // Map of: current state → set of states it's allowed to transition to
    private static final Map<JobStatus, Set<JobStatus>> ALLOWED_TRANSITIONS = Map.of(
            JobStatus.PENDING,    Set.of(JobStatus.PROCESSING),
            JobStatus.PROCESSING, Set.of(JobStatus.COMPLETED, JobStatus.FAILED),
            JobStatus.COMPLETED,  Set.of(),   // terminal state — no further transitions
            JobStatus.FAILED,     Set.of()    // terminal state — no further transitions
    );

    /**
     * Checks if a transition from currentStatus to targetStatus is valid.
     */
    public boolean canTransition(JobStatus currentStatus, JobStatus targetStatus) {
        Set<JobStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        boolean canTransition = allowed.contains(targetStatus);

        if (!canTransition) {
            log.warn("Invalid state transition attempted: {} → {}", currentStatus, targetStatus);
        }

        return canTransition;
    }

    /**
     * Validates and returns the target status.
     * Throws if the transition is not allowed.
     */
    public JobStatus transition(JobStatus currentStatus, JobStatus targetStatus) {
        if (!canTransition(currentStatus, targetStatus)) {
            throw new IllegalStateException(
                    String.format("Cannot transition from %s to %s", currentStatus, targetStatus)
            );
        }

        log.info("State transition: {} → {}", currentStatus, targetStatus);
        return targetStatus;
    }
}