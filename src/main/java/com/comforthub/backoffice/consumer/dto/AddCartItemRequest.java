package com.comforthub.backoffice.consumer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body of {@code POST /api/consumer/cart/items} — maps 1:1 onto the parameters
 * of Bubble's {@code adding_to_cart_attributes(be)} workflow (the customer and
 * added-by user are always the authenticated user, never taken from the body).
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddCartItemRequest {

    /** Bubble {@code inventory} id — required. */
    private String inventoryId;

    /** Bubble {@code offerings} id — required (drives pricing/delivery rules). */
    private String offeringId;

    /** Units to add; defaults to 1. */
    private Integer quantity;

    /** Bubble {@code store} id the item is bought from. */
    private String storeId;

    /** Optional selected add-on configuration ids. */
    private List<String> addonIds;

    /** Optional Bubble "Cart Item Type" option value (e.g. "One Off Purchase"). */
    private String cartItemType;

    /** Optional Bubble "Cart Item Status" option value. */
    private String cartItemStatus;
}
