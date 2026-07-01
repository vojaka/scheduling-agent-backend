package com.comforthub.backoffice.mapper;

import com.comforthub.backoffice.dto.InventoryDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the VAT / price / description fields added to close part of the
 * Bubble option-set follow-up (backend issue #93): these were previously
 * unmapped even though they're live {@code inventory} fields (see
 * {@code comforthub_schema.md}). VAT is an option-set field ({@code taxes}),
 * but since Bubble's Data API returns/accepts option-set values as plain
 * display-text strings, it's exercised here exactly like any other string
 * field — no enum-decoding path exists or is needed.
 */
class InventoryBubbleMapperTest {

    private final InventoryBubbleMapper mapper = new InventoryBubbleMapper(new ObjectMapper());

    @Test
    void toDto_readsVatPriceAndDescription() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("_id", "1699999999999x1");
        record.put("Company", "1699999999999x2");
        record.put("Name", "Standard Massage");
        record.put("VAT", "20%");
        record.put("Price Base (w VAT)", "45.00");
        record.put("Description", "60 minute full body massage");
        record.put("Is Deleted", false);

        InventoryDto dto = mapper.toDto(record);

        assertThat(dto.getVat()).isEqualTo("20%");
        assertThat(dto.getPriceBaseWithVat()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(dto.getDescription()).isEqualTo("60 minute full body massage");
    }

    @Test
    void toDto_missingVatPriceDescription_areNull() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("_id", "1699999999999x1");
        record.put("Name", "No Extra Fields Item");

        InventoryDto dto = mapper.toDto(record);

        assertThat(dto.getVat()).isNull();
        assertThat(dto.getPriceBaseWithVat()).isNull();
        assertThat(dto.getDescription()).isNull();
    }

    @Test
    void toCreateBody_includesVatPriceAndDescriptionWhenPresent() {
        InventoryDto dto = new InventoryDto();
        dto.setName("Coffee Beans");
        dto.setVat("20%");
        dto.setPriceBaseWithVat(new BigDecimal("12.50"));
        dto.setDescription("1kg bag, whole bean");

        Map<String, Object> body = mapper.toCreateBody(dto, "1699999999999x2");

        assertThat(body)
            .containsEntry("VAT", "20%")
            .containsEntry("Price Base (w VAT)", new BigDecimal("12.50"))
            .containsEntry("Description", "1kg bag, whole bean")
            .containsEntry("Company", "1699999999999x2")
            .containsEntry("Is Deleted", false);
    }

    @Test
    void toUpdateBody_omitsNullVatPriceAndDescription() {
        InventoryDto dto = new InventoryDto();
        dto.setName("Renamed Item");

        Map<String, Object> body = mapper.toUpdateBody(dto);

        assertThat(body)
            .containsEntry("Name", "Renamed Item")
            .doesNotContainKey("VAT")
            .doesNotContainKey("Price Base (w VAT)")
            .doesNotContainKey("Description");
    }
}
