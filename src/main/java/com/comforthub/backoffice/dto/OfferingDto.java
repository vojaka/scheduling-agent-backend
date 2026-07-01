package com.comforthub.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Offering payload exchanged with the React backoffice UI.
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

    /** 'Active' | 'Draft' | 'Archive' | 'Inactive' */
    private String status;

    private Boolean limitedVisibility;

    private Boolean unlimitedQuantity;

    private Boolean quantityRequired;

    private String deliveryType;

    /** Former PostgreSQL {@code text[]} — a list of pay-option codes. */
    private String[] payOptions;

    private String priceSource;

    private String defaultType;

    /** Unit price. */
    private BigDecimal price;

    private Boolean defaultOffering;

    private Integer minQuantity;

    private Integer maxQuantity;

    /** ISO-8601 instant (Bubble "Created Date"). */
    private String createdAt;
}
