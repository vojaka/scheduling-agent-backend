package com.comforthub.backoffice.consumer.dto;

import com.comforthub.backoffice.dto.InventoryDto;
import com.comforthub.backoffice.dto.OfferingDto;
import com.comforthub.backoffice.dto.StockDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * A consumer catalog product: the Bubble {@code inventory} record (reusing the
 * backoffice {@link InventoryDto} mapping) enriched with its sellable
 * {@link OfferingDto offerings} and per-store {@link StockDto stock} rows, so
 * the app can render price, purchase options and availability in one call.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsumerProductDto {

    /** The catalog item itself (Bubble {@code inventory}). */
    private InventoryDto product;

    /** Sellable offerings linked to this inventory item. */
    private List<OfferingDto> offerings = new ArrayList<>();

    /** Per-store stock rows for this inventory item. */
    private List<StockDto> stock = new ArrayList<>();

    /**
     * Convenience availability flag: any stock row with quantity &gt; 0, or any
     * offering with unlimited quantity.
     */
    private Boolean available;
}
