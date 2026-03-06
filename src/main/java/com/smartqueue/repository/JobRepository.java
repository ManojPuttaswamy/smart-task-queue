package com.smartqueue.repository;

import com.smartqueue.entity.JobInstance;
import com.smartqueue.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA gives us free CRUD methods just by extending JpaRepository.
 * No SQL needed for basic operations — Spring generates it all.
 *
 * JpaRepository<JobInstance, UUID> means:
 *   - Entity type: JobInstance
 *   - Primary key type: UUID
 *
 * We get for free: save(), findById(), findAll(), deleteById(), etc.
 */
@Repository
public interface JobRepository extends JpaRepository<JobInstance, UUID> {

    // We'll expand this with tenant filtering
    List<JobInstance> findByTenantId(String tenantId);

    List<JobInstance> findByStatus(JobStatus status);

    Optional<JobInstance> findByJobIdAndTenantId(UUID jobId, String tenantId);
}