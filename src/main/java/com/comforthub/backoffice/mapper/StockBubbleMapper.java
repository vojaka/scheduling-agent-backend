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
 * Translates between the backoffice {@link StockDto} (the UI contract) and the
 * Bubble {@code stock} object. This is the single place that knows Bubble's
 * field keys, so adapting to schema changes touches only this file.
 *
 * <p>Built to the same conventions as {@link OrderBubbleMapper}: this app's
 * Data API exposes <b>display-name keys</b> (e.g. {@code "Merchant"},
 * {@code "Store"}) and constraints use those same keys.
 *
 * <p><b>CRITICAL — the field keys below are INFERRED, not confirmed from live
 * Bubble {@code stock} records.</b> Each is tagged {@code INFERRED} and must be
 * verified against the real Bubble schema before this ships:
 * <ul>
 *   <li>{@link #F_COMPANY} {@code "Merchant"} — the scope key, guessed to match
 *       {@code OrderBubbleMapper.F_COMPANY}. The stock object may instead scope
 *       indirectly through its store (no direct merchant field), in which case
 *       the company constraint here will silently match nothing — VERIFY.</li>
 *   <li>{@link #F_STORE} {@code "Store"} — reference to the store.</li>
 *   <li>{@link #F_INVENTORY} {@code "Inventory"} — reference to the inventory
 *       item this stock row tracks.</li>
 *   <li>{@link #F_QUANTITY} {@code "Quantity"} — the on-hand count (number).</li>
 * </ul>
 *
 * <p><b>Unsupported filter:</b> the {@code GET} {@code name} filter is by the
 * <i>linked inventory's</i> name — a cross-entity join that a single Bubble
 * constraint on the {@code stock} type cannot express. Mirroring how
 * {@link OrderBubbleMapper} handles its unmappable filters, {@code name} is
 * accepted but never applied as a constraint (see {@link #buildConstraints}).
 * Follow-up: resolve matching inventory ids by name first, then constrain
 * {@link #F_INVENTORY} {@code in} that id list.
 */
@Component
public class StockBubbleMapper {

    /** Bubble Data API object type. */
    public static final String TYPE = "stock";

    // ===== Bubble field keys — ALL INFERRED, verify against live records =====
    static final String F_COMPANY   = "Merchant";    // INFERRED — owning merchant / scope key (CRITICAL to verify)
    static final String F_STORE     = "Store";       // INFERRED — store reference
    static final String F_INVENTORY = "Inventory";   // INFERRED — inventory item reference
    static final String F_QUANTITY  = "Quantity";    // INFERRED — on-hand count (number)

    /** Built-in Bubble created-date field, used as the default sort key. */
    public static final String SORT_CREATED_DATE = "Created Date";

    private final ObjectMapper objectMapper;

    public StockBubbleMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- reads

    /** Map one Bubble {@code stock} record to the UI DTO. */
    public StockDto toDto(Map<String, Object> r) {
        StockDto dto = new StockDto();
        dto.setId(readString(r, "_id"));
        dto.setCompanyId(readString(r, F_COMPANY));
        dto.setStoreId(readString(r, F_STORE));
        dto.setInventoryId(readString(r, F_INVENTORY));
        dto.setQuantity(readInteger(r, F_QUANTITY));
        dto.setUpdatedAt(readInstant(r, "Modified Date"));
        return dto;
    }

    // --------------------------------------------------------------- writes

    /**
     * Bubble constraints JSON scoping to {@code companyId} (the merchant) plus
     * the optional store filter.
     *
     * <p>The {@code name} (inventory-name substring) filter is intentionally
     * NOT included: it targets the linked inventory record's name, a
     * cross-entity join that a single constraint on the {@code stock} type
     * cannot express (see class doc). It is accepted by the controller but
     * ignored pending the resolve-inventory-ids-by-name follow-up.
     */
    public String buildConstraints(String companyId, String storeId, String name) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_COMPANY, "equals", companyId));
        if (hasText(storeId)) {
            constraints.add(constraint(F_STORE, "equals", storeId));
        }
        // `name` deliberately not applied — see method/class doc.
        return writeConstraints(constraints);
    }

    /**
     * Constraints that locate the single stock row for a given
     * company + store + inventory combination — used by the PUT upsert to find
     * an existing row before deciding to update vs. create.
     */
    public String findByStoreAndInventory(String companyId, String storeId, String inventoryId) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_COMPANY, "equals", companyId));
        constraints.add(constraint(F_STORE, "equals", storeId));
        constraints.add(constraint(F_INVENTORY, "equals", inventoryId));
        return writeConstraints(constraints);
    }

    /**
     * Body for POST /obj/stock — company-scoped row for a store + inventory with
     * an initial quantity (defaults to 0, matching the old {@code @PrePersist}).
     */
    public Map<String, Object> toCreateBody(String companyId, String storeId,
                                            String inventoryId, Integer quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_COMPANY, companyId);
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

    /** The merchant a Bubble record belongs to (for ownership checks). */
    public String companyOf(Map<String, Object> record) {
        return readString(record, F_COMPANY);
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
            // Bubble may surface numbers as "42" or "42.0" strings.
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

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
