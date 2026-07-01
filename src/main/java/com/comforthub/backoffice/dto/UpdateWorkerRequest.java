package com.comforthub.backoffice.dto;

import java.math.BigDecimal;

/**
 * Partial-update payload for {@code PUT /api/users/{id}}. Every field is
 * optional; only non-null fields are applied. {@code role} uses the UI's
 * title-case vocabulary ("Worker" / "Owner"). {@code active = false} soft
 * deactivates the worker. Email is intentionally absent — it is not editable.
 */
public record UpdateWorkerRequest(
        String name,
        String role,
        BigDecimal maxHours,
        Boolean active
) {
}
