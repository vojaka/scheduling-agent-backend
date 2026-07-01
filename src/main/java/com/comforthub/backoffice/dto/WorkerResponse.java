package com.comforthub.backoffice.dto;

import com.comforthub.backoffice.model.entity.BubbleUserEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Worker / owner shape returned by the worker-management endpoints:
 * {@code { id, name, role, maxHours, active, email }}.
 *
 * <p>{@code role} is the UI-facing title-case value ("Owner" / "Worker"),
 * mapped from the stored role via {@link RoleMapping}. {@code name} maps from
 * {@code full_name} and {@code active} from {@code is_active}; the internal
 * scoping columns ({@code bubble_id}, {@code auth0_user_id}, {@code company_id})
 * are intentionally not exposed.
 */
public record WorkerResponse(
        UUID id,
        String name,
        String role,
        BigDecimal maxHours,
        Boolean active,
        String email
) {

    public static WorkerResponse from(BubbleUserEntity u) {
        return new WorkerResponse(
                u.getId(),
                u.getFullName(),
                RoleMapping.toDisplay(u.getRole()),
                u.getMaxHours(),
                u.getIsActive(),
                u.getEmail()
        );
    }
}
