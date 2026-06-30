package com.comforthub.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stock payload exchanged with the React backoffice UI.
 *
 * <p>Field names mirror the former JPA {@code StockEntity} JSON one-for-one so
 * the UI needs no changes. The only difference is that id-bearing fields are
 * now {@link String} (Bubble's text ids such as {@code "1699999999999x123"})
 * instead of UUIDs — invisible over the wire, since both serialize to JSON
 * strings.
 *
 * <p>{@code updatedAt} is carried as an ISO-8601 string exactly as Bubble's
 * "Modified Date" is surfaced.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StockDto {

    /** Bubble record id (the Data API {@code _id}). */
    private String id;

    /** Owning company/merchant — the scoping key (Bubble merchant id). */
    private String companyId;

    /** Store this stock row belongs to (Bubble store id). */
    private String storeId;

    /** Inventory item this stock row tracks (Bubble inventory id). */
    private String inventoryId;

    /** On-hand quantity for this store + inventory combination. */
    private Integer quantity;

    /** ISO-8601 instant (Bubble "Modified Date"). */
    private String updatedAt;
}
