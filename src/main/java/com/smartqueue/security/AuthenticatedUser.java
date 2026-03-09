package com.smartqueue.security;

/**
 * AuthenticatedUser — our custom principal stored in the SecurityContext.
 *
 * After JWT validation, this object is stored as the "principal" in
 * the Authentication object. Any controller or service can retrieve it:
 *
 *   @AuthenticationPrincipal AuthenticatedUser user = ...
 *   String tenantId = user.tenantId();   // no DB lookup needed!
 *
 * Why a record?
 * Records are immutable value objects — perfect for security principals
 * since they shouldn't change after being set from the JWT.
 *
 * We'll use this heavily for multi-tenancy:
 * every job query will be scoped to user.tenantId().
 */
public record AuthenticatedUser(
        String userId,
        String username,
        String tenantId,
        String role
) {}