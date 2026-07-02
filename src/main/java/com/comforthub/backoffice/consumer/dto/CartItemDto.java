package com.comforthub.backoffice.consumer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * One line of the consumer's cart — the Bubble {@code cartitem} surfaced with
 * the cost fields the app needs (Bubble's cart machinery computes them; this
 * API never recalculates prices itself).
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartItemDto {

    /** Bubble record id (the Data API {@code _id}). */
    private String id;

    /** The cart this line belongs to (Bubble {@code cart} id). */
    private String cartId;

    private String inventoryId;

    private String offeringId;

    /** The order Bubble attached this line to (created during add-to-cart). */
    private String orderId;

    private String storeId;

    private Integer quantity;

    /** Bubble "Cart Item Status" option value. */
    private String status;

    /** Bubble "Cart Item Type" option value (e.g. "One Off Purchase"). */
    private String type;

    /** Selected add-on configuration ids. */
    private List<String> addonIds;

    /** Per-unit gross total (Bubble "1 pcs - Total Cost - W VAT"). */
    private BigDecimal unitPriceWithVat;

    /** Line gross total (Bubble "Total Cart Item Cost W VAT"). */
    private BigDecimal totalWithVat;

    /** Line VAT amount (Bubble "Total Cart Item VAT"). */
    private BigDecimal totalVat;

    /** Reservation expiry (Bubble "Date - Expiration Date"), ISO-8601. */
    private String expiresAt;
}
