package com.smartqueue.dto;

import lombok.Data;

/**
 * DTO = Data Transfer Object.
 * This is what the caller sends in the request body — NOT the entity.
 *
 * Why separate DTO from Entity?
 * - We don't want callers setting status, version, createdAt, etc.
 * - The entity is an internal concept; the DTO is the public contract.
 */
@Data
public class JobRequest {
    private String title;      // short title of the job
    private String description; // detailed description
}