package com.comforthub.backoffice.consumer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Response of {@code GET /api/consumer/delivery-fee?distanceKm=}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeliveryFeeDto {

    private double distanceKm;

    /** VAT-inclusive fee, per the active {@code DeliveryFeePolicy}. */
    private BigDecimal fee;

    private String currency;
}
