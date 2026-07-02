package com.comforthub.backoffice.consumer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response of {@code POST /api/consumer/guest} — the shadow user's Bubble id. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GuestCheckoutResponse {

    /** Bubble id of the shadow user created for this guest. */
    private String userId;
}
