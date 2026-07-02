package com.comforthub.backoffice.consumer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code PATCH /api/consumer/profile} — the self-editable fields.
 * Changing the phone number flips Bubble's "PHONE CHANGE PENDING" DB trigger,
 * which flags the phone for re-verification (intended behaviour).
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateProfileRequest {

    private String firstName;

    private String lastName;

    private String phonePrefix;

    private String phoneNumber;

    private String language;
}
