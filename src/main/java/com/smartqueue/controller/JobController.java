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

@RestController
@RequestMapping("/jobs")
@Slf4j
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<JobInstance> createJob(
            @RequestBody JobRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        log.info("POST /jobs by user: {} (tenant: {})", user.username(), user.tenantId());
        // Pass user through so JobService can write the audit log
        JobInstance created = jobService.createJob(request, user.tenantId(), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<JobInstance> getJob(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        JobInstance job = jobService.getJob(jobId, user.tenantId());
        return ResponseEntity.ok(job);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<List<JobInstance>> getJobs(
            @AuthenticationPrincipal AuthenticatedUser user) {

        List<JobInstance> jobs = jobService.getJobsByTenant(user.tenantId());
        return ResponseEntity.ok(jobs);
    }
}