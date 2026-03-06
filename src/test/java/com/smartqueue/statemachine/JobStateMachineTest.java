package com.smartqueue.statemachine;

import com.smartqueue.entity.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JobStateMachine.
 *
 * These tests are pure unit tests — no Spring context, no DB, no Kafka.
 * They run in milliseconds and validate our core business logic.
 *
 * Testing philosophy:
 * - Test every valid transition succeeds
 * - Test every invalid transition throws
 * - Test terminal states can't transition anywhere
 */
class JobStateMachineTest {

    private JobStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new JobStateMachine();
    }

    // ── Valid Transitions ────────────────────────────────────────────

    @Test
    @DisplayName("PENDING → PROCESSING should succeed")
    void pendingToProcessing_shouldSucceed() {
        JobStatus result = stateMachine.transition(JobStatus.PENDING, JobStatus.PROCESSING);
        assertThat(result).isEqualTo(JobStatus.PROCESSING);
    }

    @Test
    @DisplayName("PROCESSING → COMPLETED should succeed")
    void processingToCompleted_shouldSucceed() {
        JobStatus result = stateMachine.transition(JobStatus.PROCESSING, JobStatus.COMPLETED);
        assertThat(result).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    @DisplayName("PROCESSING → FAILED should succeed")
    void processingToFailed_shouldSucceed() {
        JobStatus result = stateMachine.transition(JobStatus.PROCESSING, JobStatus.FAILED);
        assertThat(result).isEqualTo(JobStatus.FAILED);
    }

    // ── Invalid Transitions ──────────────────────────────────────────

    @Test
    @DisplayName("PENDING → COMPLETED should throw (must go through PROCESSING)")
    void pendingToCompleted_shouldThrow() {
        assertThatThrownBy(() ->
                stateMachine.transition(JobStatus.PENDING, JobStatus.COMPLETED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("COMPLETED");
    }

    @Test
    @DisplayName("PENDING → FAILED should throw (must go through PROCESSING)")
    void pendingToFailed_shouldThrow() {
        assertThatThrownBy(() ->
                stateMachine.transition(JobStatus.PENDING, JobStatus.FAILED))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Terminal States ──────────────────────────────────────────────

    @Test
    @DisplayName("COMPLETED → anything should throw (terminal state)")
    void completedIsTerminal_shouldThrow() {
        assertThatThrownBy(() ->
                stateMachine.transition(JobStatus.COMPLETED, JobStatus.PENDING))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() ->
                stateMachine.transition(JobStatus.COMPLETED, JobStatus.PROCESSING))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() ->
                stateMachine.transition(JobStatus.COMPLETED, JobStatus.FAILED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("FAILED → anything should throw (terminal state)")
    void failedIsTerminal_shouldThrow() {
        assertThatThrownBy(() ->
                stateMachine.transition(JobStatus.FAILED, JobStatus.PENDING))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() ->
                stateMachine.transition(JobStatus.FAILED, JobStatus.PROCESSING))
                .isInstanceOf(IllegalStateException.class);
    }
}
