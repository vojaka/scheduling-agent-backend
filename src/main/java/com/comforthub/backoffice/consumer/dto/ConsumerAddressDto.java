package com.comforthub.backoffice.consumer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A consumer delivery address — the Bubble {@code address} record. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsumerAddressDto {

    /** Bubble record id (the Data API {@code _id}). */
    private String id;

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

    /** Bubble "Property Type" option value. */
    private String propertyType;

    /** Whether this is the user's primary address. */
    private Boolean primary;

    /** Pre-formatted display string (Bubble "Address Short String"). */
    private String shortString;

    private String note;

    private String createdAt;
}
