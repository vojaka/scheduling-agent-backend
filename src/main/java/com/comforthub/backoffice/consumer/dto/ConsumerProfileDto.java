package com.comforthub.backoffice.consumer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The authenticated consumer's profile — the Bubble {@code user} record. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsumerProfileDto {

    /** Bubble user id (the Data API {@code _id}). */
    private String id;

    private String firstName;

    private String lastName;

    private String fullName;

    private String phonePrefix;

    private String phoneNumber;

    private String language;

    /** Bubble "Verified Profile" flag. */
    private Boolean verifiedProfile;

    /** Bubble "Roles" option values (e.g. ["Client"]). */
    private List<String> roles;

    /** The user's cart id ("Cart (single)"). */
    private String cartId;
}
