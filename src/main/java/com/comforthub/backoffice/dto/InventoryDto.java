package com.comforthub.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inventory payload exchanged with the React backoffice UI — the
 * product/service catalog (NOT stock quantities).
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

    /** Free-text description shown on the catalog item. */
    private String description;

    /**
     * VAT / tax rate. Backed by Bubble's {@code taxes} option set, so the value
     * is one of that set's options (e.g. a rate label).
     */
    private String vat;

    /** Unit price. */
    private BigDecimal price;

    /** Preparation time in minutes. */
    private Integer prepTimeMinutes;

    /** Duration of service in minutes. */
    private Integer duration;

    /** Bubble user ids of the workers who can deliver this item. */
    private List<String> workerIds;

    /** Primary image URL. */
    private String imageUrl;

    /** Additional image URLs. */
    private List<String> secondaryImageUrls;

    /**
     * Repeatable "Service Description Extension" rows (icon/title/body),
     * shown in the Bubble "Modify Inventory" editor as an icon picker + title
     * + body + Add repeater. Backed by the separate Bubble
     * {@code inventoryextensions} type, linked from {@code inventory} via its
     * {@code Extensions [list]} field — see
     * {@link com.comforthub.backoffice.mapper.InventoryBubbleMapper}.
     *
     * <p>Left {@code null} on list responses (not eager-loaded, to avoid an
     * N+1 Bubble call per row); populated on single-item reads via
     * {@code GET /api/inventory/{id}/extensions} and on create/update
     * responses. Sent as a (possibly empty) list on create/update so the
     * backend can reconcile the child records — a {@code null} field leaves
     * existing extensions untouched, an empty list clears them.
     */
    private List<InventoryExtensionDto> extensions;

    private Boolean isDeleted;

    /** ISO-8601 instant (Bubble "Created Date"). */
    private String createdAt;
}
