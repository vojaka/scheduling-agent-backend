package com.comforthub.backoffice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Owner-submitted payload for {@code POST /api/users/invite}.
 *
 * <p>Only {@code email} is required. {@code role} is accepted in the UI's
 * title-case vocabulary ("Worker" / "Owner") and defaults to "Worker" when
 * omitted (see {@link RoleMapping}). {@code maxHours} is optional.
 */
public record InviteWorkerRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email,
        String name,
        String role,
        BigDecimal maxHours
) {
}
