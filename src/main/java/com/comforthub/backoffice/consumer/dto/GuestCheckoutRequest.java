package com.comforthub.backoffice.consumer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of {@code POST /api/consumer/guest} — guest checkout, email only. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GuestCheckoutRequest {

    /** The guest's email — the only field collected at guest checkout. */
    private String email;
}
