package com.smartqueue.controller;

import com.smartqueue.dto.JobRequest;
import com.smartqueue.entity.JobInstance;
import com.smartqueue.security.AuthenticatedUser;
import com.smartqueue.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * JobController — handles HTTP requests only. No business logic here.
 *
 *
 * 1. @AuthenticationPrincipal AuthenticatedUser user
 *    Spring injects the authenticated user from the SecurityContext.
 *    This is the AuthenticatedUser record we populated in JwtAuthenticationFilter.
 *    We use user.tenantId() to scope every query — no manual extraction needed.
 *
 * 2. @PreAuthorize annotations enforce role-based access:
 *    - VIEWER  → can only GET (read)
 *    - OPERATOR → can POST (create) and GET (read)
 *    - ADMIN   → full access including /admin/**
 *
 *    hasAnyRole() checks the GrantedAuthority set in JwtAuthenticationFilter:
 *    new SimpleGrantedAuthority("ROLE_" + role)
 *
 * 3. tenantId no longer comes from the request body or @RequestParam —
 *    it always comes from the JWT via the AuthenticatedUser principal.
 */
@RestController
@RequestMapping("/jobs")
@Slf4j
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    /**
     * POST /jobs
     * Body: { "title": "Fix DB", "description": "..." }
     * tenantId comes from JWT — callers can't forge it.
     *
     * Only OPERATOR and ADMIN can create jobs.
     * VIEWER gets 403 Forbidden.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<JobInstance> createJob(
            @RequestBody JobRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        log.info("POST /jobs by user: {} (tenant: {})", user.username(), user.tenantId());
        JobInstance created = jobService.createJob(request, user.tenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /jobs/{jobId}
     * Returns a single job — but only if it belongs to the caller's tenant.
     * Prevents tenant-a from reading tenant-b's jobs even if they know the UUID.
     *
     * VIEWER, OPERATOR, and ADMIN can all read.
     */
    @GetMapping("/{jobId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<JobInstance> getJob(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        JobInstance job = jobService.getJob(jobId, user.tenantId());
        return ResponseEntity.ok(job);
    }

    /**
     * GET /jobs
     * Returns all jobs for the caller's tenant only.
     * No tenantId query param needed — it comes from the JWT.
     *
     * VIEWER, OPERATOR, and ADMIN can all read.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<List<JobInstance>> getJobs(
            @AuthenticationPrincipal AuthenticatedUser user) {

        List<JobInstance> jobs = jobService.getJobsByTenant(user.tenantId());
        return ResponseEntity.ok(jobs);
    }
}