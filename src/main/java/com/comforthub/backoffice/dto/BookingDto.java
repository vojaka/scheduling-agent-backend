package com.comforthub.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Booking (calendar event) payload exchanged with the React backoffice UI.
 *
 * <p>Field names mirror the former JPA {@code BookingEntity} JSON one-for-one so
 * the UI (calendar screens) needs no changes. The only difference is that
 * id-bearing fields are now {@link String} (Bubble's text ids such as
 * {@code "1699999999999x123"}) instead of UUIDs — invisible over the wire,
 * since both serialize to JSON strings.
 *
 * <p>Dates are carried as ISO-8601 strings exactly as Bubble returns them
 * (Bubble surfaces dates as epoch millis; the mapper normalises them to
 * ISO-8601 on read).
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingDto {

    /** Bubble record id (the Data API {@code _id}). */
    private String id;

    /** Same Bubble id, kept for parity with the old {@code bubbleId} field. */
    private String bubbleId;

    /** Owning company/merchant — the scoping key (Bubble merchant id). */
    private String companyId;

    /** Store reference (Bubble store id). */
    private String storeId;

    /** Worker assigned to this booking (Bubble user id). */
    private String workerId;

    private String customerEmail;

    private String customerName;

    private String title;

    /** ISO-8601 instant. */
    private String startTime;

    /** ISO-8601 instant. */
    private String endTime;

    /** ISO-8601 instant (Bubble "Created Date"). */
    private String createdAt;
}
