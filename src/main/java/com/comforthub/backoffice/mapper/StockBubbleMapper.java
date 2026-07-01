package com.comforthub.backoffice.mapper;

import com.comforthub.backoffice.dto.StockDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between the backoffice {@link StockDto} and the Bubble
 * {@code stock} object. Single home of stock's Bubble field keys.
 *
 * <p><b>Scoping (confirmed from live data):</b> the Bubble {@code stock} type has
 * <b>no merchant/company field</b> — it links only to a {@code Store}. So a
 * company is scoped <i>indirectly</i>: resolve the company's stores (Bubble
 * {@code store} where {@code Company = companyId}), then constrain stock to
 * {@code Store in [those store ids]}. See {@code StockController}.
 *
 * <p>Confirmed stock field display-keys (from the App-data view): {@code Store},
 * {@code Inventory} (references), {@code "Qnty in stock"} (the quantity),
 * {@code Created Date} / {@code Modified Date}. The store-side company key
 * ({@code "Company"}) comes from the existing {@code BubbleStore} mapping and
 * should still be verified.
 *
 * <p>The {@code GET name} filter targets the <i>linked inventory's</i> name — a
 * cross-entity join a single stock constraint can't express — so it is accepted
 * but ignored (follow-up: resolve inventory ids by name first).
 */
@Component
public class StockBubbleMapper {

    /** Bubble Data API object type. */
    public static final String TYPE = "stock";

    // ===== Confirmed stock field keys (from the live App-data view) =====
    static final String F_STORE     = "Store";           // store reference
    static final String F_INVENTORY = "Inventory";       // inventory item reference
    static final String F_QUANTITY  = "Qnty in stock";   // on-hand count (number)

    // ===== Store type, used to resolve which stores belong to the company =====
    /** Bubble object type for stores. */
    public static final String STORE_TYPE = "store";
    /** Store's company/merchant field — from the BubbleStore mapping (verify). */
    static final String STORE_COMPANY_FIELD = "Company";

    /** Built-in Bubble created-date field, used as the default sort key. */
    public static final String SORT_CREATED_DATE = "Created Date";

    private final ObjectMapper objectMapper;

    public StockBubbleMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- reads

    /**
     * Map one Bubble {@code stock} record to the UI DTO. {@code companyId} is set
     * by the controller (stock carries no merchant field of its own).
     */
    public StockDto toDto(Map<String, Object> r) {
        StockDto dto = new StockDto();
        dto.setId(readString(r, "_id"));
        dto.setStoreId(readString(r, F_STORE));
        dto.setInventoryId(readString(r, F_INVENTORY));
        dto.setQuantity(readInteger(r, F_QUANTITY));
        dto.setUpdatedAt(readInstant(r, "Modified Date"));
        return dto;
    }

    /** Extract the {@code _id}s from a page of Bubble {@code store} records. */
    public List<String> storeIdsOf(List<Map<String, Object>> storeRecords) {
        List<String> ids = new ArrayList<>();
        if (storeRecords != null) {
            for (Map<String, Object> s : storeRecords) {
                String id = readString(s, "_id");
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    // ------------------------------------------------------- constraints/writes

    /** Constraints selecting the stores that belong to {@code companyId}. */
    public String storeCompanyConstraints(String companyId) {
        return writeConstraints(List.of(constraint(STORE_COMPANY_FIELD, "equals", companyId)));
    }

    /** Constraints selecting stock rows whose store is one of {@code storeIds}. */
    public String stockByStoresConstraints(List<String> storeIds) {
        return writeConstraints(List.of(constraint(F_STORE, "in", storeIds)));
    }

    /** Constraints locating the single stock row for a store + inventory pair. */
    public String findByStoreAndInventory(String storeId, String inventoryId) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_STORE, "equals", storeId));
        constraints.add(constraint(F_INVENTORY, "equals", inventoryId));
        return writeConstraints(constraints);
    }

    /** Body for POST /obj/stock — a row for a store + inventory with a quantity. */
    public Map<String, Object> toCreateBody(String storeId, String inventoryId, Integer quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_STORE, storeId);
        body.put(F_INVENTORY, inventoryId);
        body.put(F_QUANTITY, quantity != null ? quantity : 0);
        return body;
    }

    /** Single-field body for the quantity update (PATCH). */
    public Map<String, Object> quantityUpdateBody(Integer quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_QUANTITY, quantity != null ? quantity : 0);
        return body;
    }

    // --------------------------------------------------------------- helpers

    private String writeConstraints(List<Map<String, Object>> constraints) {
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

    private static String readString(Map<String, Object> r, String key) {
        if (r == null) {
            return null;
        }
        Object v = r.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private static Integer readInteger(Map<String, Object> r, String key) {
        if (r == null) {
            return null;
        }
        Object v = r.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Bubble returns dates as epoch milliseconds; surface them as ISO-8601. */
    private static String readInstant(Map<String, Object> r, String key) {
        if (r == null) {
            return null;
        }
        Object v = r.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return Instant.ofEpochMilli(n.longValue()).toString();
        }
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }
}
