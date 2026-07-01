package com.comforthub.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Store payload exchanged with the React backoffice UI.
 *
 * <p>Field names mirror the former JPA {@code BubbleStoreEntity} JSON so the UI
 * needs no changes, except ids are Bubble text ids (e.g. {@code "1699999999999x123"})
 * instead of UUIDs — invisible over the wire, since both serialize to JSON strings.
 *
 * <p>Dates are carried as ISO-8601 strings exactly as Bubble returns them.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoreDto {

    /** Bubble record id (the Data API {@code _id}). */
    private String id;

    /** Same Bubble id, kept for parity with the old {@code bubbleId} field. */
    private String bubbleId;

    /** Owning company/merchant — the scoping key (Bubble merchant id). */
    private String companyId;

    private String name;

    /** Bubble id of the linked availability profile (opening hours/days). */
    private String availabilityId;

    private Boolean isDeleted;

    /** ISO-8601 instant (Bubble "Created Date"). */
    private String createdAt;
}
