package com.comforthub.backoffice.consumer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** The consumer's cart: its Bubble id plus the active (non-deleted) lines. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartDto {

    /** Bubble {@code cart} id (the user's "Cart (single)"). */
    private String cartId;

    private List<CartItemDto> items = new ArrayList<>();

    /** Sum of the lines' gross totals (as computed by Bubble). */
    private BigDecimal totalWithVat;
}
