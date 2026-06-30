package com.comforthub.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Order payload exchanged with the React backoffice UI.
 *
 * <p>Field names mirror the former {@code OrderEntity} JSON one-for-one so the
 * UI ({@code lib/page.ts} / order screens) needs no changes. The only
 * difference is that id-bearing fields are now {@link String} (Bubble's text
 * ids such as {@code "1699999999999x123"}) instead of UUIDs — invisible over
 * the wire, since both serialize to JSON strings.
 *
 * <p>Dates are carried as ISO-8601 strings exactly as Bubble returns them.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDto {

    /** Bubble record id (the Data API {@code _id}). */
    private String id;

    /** Same Bubble id, kept for parity with the old {@code bubbleId} field. */
    private String bubbleId;

    /** Owning company/merchant — the scoping key (Bubble merchant id). */
    private String companyId;

    private String storeId;

    private String orderNr;

    private String customerName;

    private String customerId;

    private String type;

    private BigDecimal amount;

    /** 'Paid' | 'Unpaid' | 'Partial' */
    private String paymentStatus;

    /**
     * Kanban column status (UI vocabulary, not Bubble's):
     * not_started, planned, preparation_in_progress,
     * ready_for_pickup, courier_assigned, completed.
     */
    private String status;

    /** Worker assigned to this order (Bubble user id). */
    private String assignedTo;

    /** ISO-8601 instant. */
    private String readyBy;

    private String notes;

    /** ISO-8601 instant (Bubble "Created Date"). */
    private String createdAt;

    /** ISO-8601 instant (Bubble "Modified Date"). */
    private String updatedAt;
}
