package com.smartqueue.controller;

import com.smartqueue.dto.JobRequest;
import com.smartqueue.entity.JobInstance;
import com.smartqueue.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller — handles HTTP requests only.
 * No business logic here. It just calls JobService.
 *
 * @RestController → combines @Controller + @ResponseBody (auto-serializes to JSON)
 * @RequestMapping → all routes in this class start with /jobs
 */
@RestController
@RequestMapping("/jobs")
@Slf4j
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    /**
     * POST /jobs
     * Body: { "tenantId": "tenant-a", "title": "Fix DB", "description": "..." }
     * Returns: the saved job with its generated jobId, status=PENDING, timestamps
     */
    @PostMapping
    public ResponseEntity<JobInstance> createJob(@RequestBody JobRequest request) {
        log.info("POST /jobs received for tenant: {}", request.getTenantId());
        JobInstance created = jobService.createJob(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /jobs/{jobId}
     * Returns a single job by its UUID
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobInstance> getJob(@PathVariable UUID jobId) {
        JobInstance job = jobService.getJob(jobId);
        return ResponseEntity.ok(job);
    }

    /**
     * GET /jobs?tenantId=tenant-a
     * Returns all jobs for a given tenant
     */
    @GetMapping
    public ResponseEntity<List<JobInstance>> getJobs(@RequestParam String tenantId) {
        List<JobInstance> jobs = jobService.getJobsByTenant(tenantId);
        return ResponseEntity.ok(jobs);
    }
}