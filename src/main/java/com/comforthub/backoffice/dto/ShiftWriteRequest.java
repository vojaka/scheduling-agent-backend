package com.comforthub.backoffice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for creating or updating a shift directly in PostgreSQL via the backoffice.
 * Timestamps are ISO-8601 strings (e.g. "2026-06-29T08:00:00Z").
 */
@Data
public class ShiftWriteRequest {

    @NotBlank
    private String assignedUser;

    @NotBlank
    private String startTime;

    @NotBlank
    private String endTime;

    private String notes;
    private String assignedCompany;
    private String type;
    private String status;
    private String assignedStore;
}
