package com.smartqueue.entity;

/**
 * Three roles in the system:
 *
 * VIEWER   — can only read jobs (GET /jobs)
 * OPERATOR — can create and read jobs (POST /jobs, GET /jobs)
 * ADMIN    — full access including /admin/** endpoints (replay, audit logs)
 *
 * These will be enforced via @PreAuthorize in Day 9 (RBAC).
 * Today we just store the role in the JWT.
 */
public enum UserRole {
    VIEWER,
    OPERATOR,
    ADMIN
}