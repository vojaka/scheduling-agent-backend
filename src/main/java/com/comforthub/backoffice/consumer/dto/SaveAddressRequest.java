package com.comforthub.backoffice.consumer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code POST /api/consumer/addresses} (and {@code PUT .../{id}}) —
 * maps onto the parameters of Bubble's {@code save_address} workflow. The
 * owning user is always the authenticated user.
 *
 * <p>Note {@code shortString}: Bubble does not compute the display string —
 * the caller must send it pre-formatted (documented quirk of
 * {@code save_address}).
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SaveAddressRequest {

    private String street;

    private String houseNumber;

    private String apartment;

    private String floor;

    private String city;

    private String postCode;

    private String province;

    private String country;

    private String latitude;

    private String longitude;

    /** Must match an existing Bubble Property Type option name. */
    private String propertyType;

    /** Make this the primary address (clears the previous primary). */
    private Boolean primary;

    /** Pre-formatted display string — computed by the caller. */
    private String shortString;
}
