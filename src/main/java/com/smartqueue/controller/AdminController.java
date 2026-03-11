package com.smartqueue.controller;

import com.smartqueue.entity.AuditLog;
import com.smartqueue.security.AuthenticatedUser;
import com.smartqueue.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only endpoints.
 * @PreAuthorize at class level applies to every method — no need to repeat it.
 * VIEWER and OPERATOR get 403 on any /admin/** route.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * POST /admin/jobs/{jobId}/replay
     * Resets a FAILED job back to PENDING and re-publishes it to Kafka.
     * Full audit trail: who triggered the replay and when.
     */
    @PostMapping("/jobs/{jobId}/replay")
    public ResponseEntity<Void> replayJob(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal AuthenticatedUser admin) {

        log.info("Admin replay triggered: jobId={}, by={}", jobId, admin.username());
        adminService.replayJob(jobId, admin);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /admin/audit-logs
     * Returns all audit log entries for the admin's tenant, newest first.
     * Tenant-scoped — ADMIN can only see their own tenant's audit trail.
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @AuthenticationPrincipal AuthenticatedUser admin) {

        List<AuditLog> logs = adminService.getAuditLogs(admin.tenantId());
        return ResponseEntity.ok(logs);
    }

    /**
     * GET /admin/audit-logs/{jobId}
     * Returns audit log entries for a specific job — useful for debugging
     * a single job's full lifecycle: created → processing → failed → replayed → completed.
     */
    @GetMapping("/audit-logs/{jobId}")
    public ResponseEntity<List<AuditLog>> getAuditLogsForJob(
            @PathVariable UUID jobId) {

        List<AuditLog> logs = adminService.getAuditLogsForJob(jobId);
        return ResponseEntity.ok(logs);
    }
}