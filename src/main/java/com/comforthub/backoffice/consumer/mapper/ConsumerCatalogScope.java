package com.comforthub.backoffice.consumer.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Constraint builders for the consumer catalog. Field keys are the same
 * confirmed Bubble keys the backoffice mappers use; only the <i>scope</i>
 * differs — the consumer catalog is marketplace-wide (no company filter),
 * limited to active (non-deleted) records.
 */
@Component
public class ConsumerCatalogScope {

    // Same confirmed keys as InventoryBubbleMapper / StockBubbleMapper.
    static final String F_INV_NAME         = "Name";
    static final String F_INV_MAIN_PRODUCT = "Main Product";
    static final String F_INV_CATEGORY     = "Category";
    static final String F_INV_IS_DELETED   = "Is Deleted";
    static final String F_STOCK_INVENTORY  = "Inventory";

    private final ObjectMapper objectMapper;

    public ConsumerCatalogScope(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Active inventory, optionally narrowed to a Main Product, a Category
     * and/or a name substring.
     */
    public String activeProducts(String mainProductId, String categoryId, String search) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_INV_IS_DELETED, "not equal", true));
        if (hasText(mainProductId)) {
            constraints.add(constraint(F_INV_MAIN_PRODUCT, "equals", mainProductId));
        }
        if (hasText(categoryId)) {
            constraints.add(constraint(F_INV_CATEGORY, "equals", categoryId));
        }
        if (hasText(search)) {
            constraints.add(constraint(F_INV_NAME, "text contains", search));
        }
        return write(constraints);
    }

    /** Records whose {@code _id} is one of {@code ids} (offerings batch fetch). */
    public String idIn(Collection<String> ids) {
        return write(List.of(constraint("_id", "in", new ArrayList<>(ids))));
    }

    /** Stock rows for any of {@code inventoryIds} (availability batch fetch). */
    public String stockForInventories(Collection<String> inventoryIds) {
        return write(List.of(constraint(F_STOCK_INVENTORY, "in", new ArrayList<>(inventoryIds))));
    }

    // --------------------------------------------------------------- helpers

    private String write(List<Map<String, Object>> constraints) {
        try {
            return objectMapper.writeValueAsString(constraints);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build Bubble constraints", e);
        }
    }

    private static Map<String, Object> constraint(String key, String type, Object value) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("key", key);
        c.put("constraint_type", type);
        c.put("value", value);
        return c;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
