package com.smartqueue.repository;

import com.smartqueue.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for AuditLog.
 *
 * findByTenantId: used by GET /admin/audit-logs — admins can only
 * see audit entries for their own tenant (multi-tenancy applies to
 * audit logs too).
 *
 * findByEntityId: useful for debugging — "show me everything that
 * happened to job abc-123".
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<AuditLog> findByEntityIdOrderByCreatedAtDesc(String entityId);
}