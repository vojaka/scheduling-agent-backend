package com.comforthub.backoffice.consumer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code POST /api/consumer/profile/verify-phone} and
 * {@code .../confirm-phone}. {@code code} is only used by confirm.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhoneVerificationRequest {

    /** Full phone number (prefix + number) the Twilio code is sent to. */
    private String phoneNumber;

    /** The SMS code the user typed — confirm-phone only. */
    private String code;
}
