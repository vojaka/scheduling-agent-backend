package com.comforthub.backoffice.dto;

import com.comforthub.backoffice.model.entity.BubbleUserEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Worker / owner shape returned by the worker-management endpoints:
 * {@code { id, bubbleId, name, role, maxHours, active, email }}.
 *
 * <p>{@code role} is the UI-facing title-case value ("Owner" / "Worker"),
 * mapped from the stored role via {@link RoleMapping}. {@code name} maps from
 * {@code full_name} and {@code active} from {@code is_active}.
 *
 * <p>{@code bubbleId} IS exposed (unlike {@code auth0_user_id} / {@code company_id},
 * which stay internal-only): the frontend's Company page resolves
 * {@code company.owners[]} / {@code company.workers[]} — which hold Bubble user
 * ids — to display names by matching against a worker's {@code bubbleId}. Newly
 * invited workers (not yet synced from Bubble) have a null {@code bubbleId}.
 */
public record WorkerResponse(
        UUID id,
        String bubbleId,
        String name,
        String role,
        BigDecimal maxHours,
        Boolean active,
        String email
) {

    public static WorkerResponse from(BubbleUserEntity u) {
        return new WorkerResponse(
                u.getId(),
                u.getBubbleId(),
                u.getFullName(),
                RoleMapping.toDisplay(u.getRole()),
                u.getMaxHours(),
                u.getIsActive(),
                u.getEmail()
        );
    }
}
