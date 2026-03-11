package com.smartqueue.service;

import com.smartqueue.entity.AuditLog;
import com.smartqueue.repository.AuditLogRepository;
import com.smartqueue.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Saves audit log entries for every significant system action.
 *
 * Design decisions:
 *
 * 1. REQUIRES_NEW propagation:
 *    Audit logs use a separate transaction from the calling method.
 *    If the main transaction rolls back (e.g., job creation fails),
 *    the audit log is still saved — you want a record that the attempt
 *    happened, even if it didn't succeed.
 *
 * 2. @Async:
 *    Audit logging should never slow down the main request.
 *    It runs on a background thread — the HTTP response returns
 *    immediately, audit write happens asynchronously.
 *    Trade-off: if the app crashes after the main action but before
 *    the async audit write, the log entry is lost. Acceptable for
 *    audit logs; not acceptable for financial transactions.
 *
 * 3. System events (Kafka consumer actions):
 *    These don't have an authenticated user — userId/username will be null.
 *    We use the "SYSTEM" username to make these visible in audit logs.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Logs an action performed by an authenticated HTTP user.
     * Called from controllers/services that have access to AuthenticatedUser.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUserAction(
            AuthenticatedUser user,
            String action,
            String entityId,
            String oldState,
            String newState,
            String details
    ) {
        AuditLog entry = AuditLog.builder()
                .tenantId(user.tenantId())
                .userId(user.userId())
                .username(user.username())
                .action(action)
                .entityId(entityId)
                .oldState(oldState)
                .newState(newState)
                .details(details)
                .build();

        auditLogRepository.save(entry);
        log.debug("Audit log saved: action={}, entityId={}, user={}", action, entityId, user.username());
    }

    /**
     * Logs an action performed by the system (e.g., Kafka consumer transitions).
     * No user context available — userId and username will be null.
     *
     * Not @Async because Kafka consumers already run on background threads.
     * REQUIRES_NEW ensures the audit log is saved even if the caller's
     * transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSystemAction(
            String tenantId,
            String action,
            String entityId,
            String oldState,
            String newState,
            String details
    ) {
        AuditLog entry = AuditLog.builder()
                .tenantId(tenantId)
                .userId("INETRNAL_SYSTEM")
                .username("SYSTEM")
                .action(action)
                .entityId(entityId)
                .oldState(oldState)
                .newState(newState)
                .details(details)
                .build();

        auditLogRepository.save(entry);
        log.debug("Audit log saved: action={}, entityId={}, user=SYSTEM", action, entityId);
    }
}