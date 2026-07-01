package com.comforthub.backoffice.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Offering payload exchanged with the React backoffice UI.
 *
 * <p>Field names mirror the former JPA {@code OfferingEntity} JSON one-for-one so
 * the UI needs no changes. The only difference is that id-bearing fields are now
 * {@link String} (Bubble's text ids such as {@code "1699999999999x123"}) instead
 * of UUIDs — invisible over the wire, since both serialize to JSON strings.
 *
 * <p>Dates are carried as ISO-8601 strings exactly as Bubble returns them.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OfferingDto {

    /** Bubble record id (the Data API {@code _id}). */
    private String id;

    /** Same Bubble id, kept for parity with the old {@code bubbleId} field. */
    private String bubbleId;

    /** Owning company/merchant — the scoping key (Bubble merchant id). */
    private String companyId;

    private String name;

    private String type;

    /** 'Active' | 'Inactive' */
    private String status;

    private Boolean limitedVisibility;

    private Boolean unlimitedQuantity;

    private Boolean quantityRequired;

    private String deliveryType;

    /** Former PostgreSQL {@code text[]} — a list of pay-option codes. */
    private String[] payOptions;

    private String priceSource;

    private String defaultType;

    private BigDecimal price;

    private Boolean defaultOffering;

    private Integer minQuantity;

    private Integer maxQuantity;

    /** ISO-8601 instant (Bubble "Created Date"). */
    private String createdAt;
}
