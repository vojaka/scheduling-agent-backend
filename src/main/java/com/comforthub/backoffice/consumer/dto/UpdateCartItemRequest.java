package com.comforthub.backoffice.consumer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of {@code PATCH /api/consumer/cart/items/{id}} — quantity change only. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateCartItemRequest {

    /** New absolute quantity for the line; must be &gt;= 1. */
    private Integer quantity;
}
