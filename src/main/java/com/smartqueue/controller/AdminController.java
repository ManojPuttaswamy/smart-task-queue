package com.smartqueue.controller;

import com.smartqueue.security.AuthenticatedUser;
import com.smartqueue.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin endpoints — operations that go beyond normal job management.
 *
 * Day 6: DLQ replay
 * Day 10: audit log retrieval (coming later)
 * Day 9:  will be protected by ADMIN role via Spring Security
 */
@RestController
@RequestMapping("/admin")
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    /**
     * POST /admin/jobs/{jobId}/replay
     *
     * Takes a permanently failed job (sitting in DLQ) and re-submits it
     * to the main job-events topic so it gets processed again from scratch.
     *
     * Flow:
     *   1. Look up job in DB — verify it exists and is FAILED
     *   2. Reset its status back to PENDING and clear retryCount
     *   3. Clear the Redis idempotency key so the event isn't skipped
     *   4. Re-publish a fresh JobEvent to job-events topic
     */
    @PostMapping("/jobs/{jobId}/replay")
    public ResponseEntity<Map<String, String>> replayJob(@PathVariable UUID jobId, @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Replay requested for jobId={} by admin: {} (tenant: {})",jobId, user.username(), user.tenantId());
        adminService.replayJob(jobId);
        return ResponseEntity.ok(Map.of(
                "status", "replayed",
                "jobId", jobId.toString(),
                "message", "Job re-queued to job-events topic"
        ));
    }
}
