package com.comforthub.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Inventory payload exchanged with the React backoffice UI — the
 * product/service catalog (NOT stock quantities).
 *
 * <p>Field names mirror the former JPA {@code InventoryEntity} JSON one-for-one
 * so the UI needs no changes. The only difference is that id-bearing fields are
 * now {@link String} (Bubble's text ids such as {@code "1699999999999x123"})
 * instead of UUIDs — invisible over the wire, since both serialize to JSON
 * strings.
 *
 * <p>Dates are carried as ISO-8601 strings exactly as Bubble returns them.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InventoryDto {

    /** Bubble record id (the Data API {@code _id}). */
    private String id;

    /** Same Bubble id, kept for parity with the old {@code bubbleId} field. */
    private String bubbleId;

    /** Owning company/merchant — the scoping key (Bubble merchant id). */
    private String companyId;

    private String name;

    /** 'Service' | 'Good' | etc. */
    private String type;

    /** Category ref at the Main Product level (Bubble category id). */
    private String mainProductId;

    /** Category ref at the Sub Category level (Bubble category id). */
    private String categoryId;

    private Boolean isDeleted;

    /** ISO-8601 instant (Bubble "Created Date"). */
    private String createdAt;
}
