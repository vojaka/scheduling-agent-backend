package com.comforthub.backoffice.mapper;

import com.comforthub.backoffice.dto.OfferingDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between the backoffice {@link OfferingDto} (the UI contract) and the
 * Bubble {@code offerings} object. This is the single place that knows Bubble's
 * field keys, so adapting to schema changes touches only this file.
 *
 * <p><b>IMPORTANT — INFERRED, UNVERIFIED FIELD ALIASES.</b> Unlike
 * {@code OrderBubbleMapper} (whose keys are confirmed from live records), the
 * Bubble {@code offerings} schema was NOT inspected for this phase. Every field
 * key below is an <b>inferred display-name</b> guess, following this app's
 * convention that the Data API exposes human display names (e.g. {@code
 * "Merchant"}, {@code "Store"}) and that constraints use those same keys. Each
 * constant is annotated with the reasoning and an {@code UNVERIFIED} flag. They
 * MUST be checked against a live Bubble {@code offerings} record (and the option
 * sets behind {@code status} / {@code type} / {@code deliveryType}) before this
 * goes to production. A wrong key fails silently: reads come back null, writes
 * are dropped by Bubble.
 */
@Component
public class OfferingBubbleMapper {

    /** Bubble Data API object type — PLURAL, confirmed from the ETL sync list. */
    public static final String TYPE = "offerings";

    // ===== INFERRED Bubble field keys (display-name guesses — UNVERIFIED) =====

    /**
     * CRITICAL-to-verify. Owning merchant — the scope key. Inferred to be
     * literally {@code "Merchant"} because that is the confirmed scope field on
     * the Bubble {@code order} object (see {@code OrderBubbleMapper.F_COMPANY}),
     * and scoping is consistent across this app's objects. UNVERIFIED for
     * offerings: could also be {@code "Company"}.
     */
    static final String F_COMPANY = "Merchant";

    /** Inferred from the entity {@code name} column. UNVERIFIED. */
    static final String F_NAME = "Name";

    /** Inferred from the entity {@code type} column. UNVERIFIED — likely an option set. */
    static final String F_TYPE = "Type";

    /**
     * Inferred from the entity {@code status} column ('Active' | 'Inactive').
     * UNVERIFIED — likely backed by an option set whose values are passed
     * through verbatim (the GET ?status= param is already 'Active'/'Inactive').
     */
    static final String F_STATUS = "Status";

    /** Inferred from {@code delivery_type}. UNVERIFIED. */
    static final String F_DELIVERY_TYPE = "Delivery Type";

    /**
     * Inferred from {@code pay_options} (a TEXT[] in Postgres). UNVERIFIED —
     * assumed to be a Bubble LIST field of texts/option values.
     */
    static final String F_PAY_OPTIONS = "Pay Options";

    /** Inferred from {@code price_source}. UNVERIFIED. */
    static final String F_PRICE_SOURCE = "Price Source";

    /** Inferred from {@code default_type}. UNVERIFIED. */
    static final String F_DEFAULT_TYPE = "Default Type";

    /** Inferred from {@code limited_visibility} (Boolean). UNVERIFIED. */
    static final String F_LIMITED_VISIBILITY = "Limited Visibility";

    /** Inferred from {@code unlimited_quantity} (Boolean). UNVERIFIED. */
    static final String F_UNLIMITED_QUANTITY = "Unlimited Quantity";

    /** Inferred from {@code quantity_required} (Boolean). UNVERIFIED. */
    static final String F_QUANTITY_REQUIRED = "Quantity Required";

    /**
     * INFERRED list field that models the inventory<->offering link (the Postgres
     * {@code inventory_offerings} join table). Assumed to live ON THE OFFERING as
     * a Bubble LIST OF INVENTORY ids. UNVERIFIED — see {@link #addInventoryToList}
     * / {@link #removeInventoryFromList} for the full set of assumptions.
     */
    static final String F_INVENTORY_LIST = "Inventory";

    /** Built-in Bubble created-date field, used as the default sort key. */
    public static final String SORT_CREATED_DATE = "Created Date";

    private final ObjectMapper objectMapper;

    public OfferingBubbleMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- reads

    /** Map one Bubble {@code offerings} record to the UI DTO. */
    public OfferingDto toDto(Map<String, Object> r) {
        OfferingDto dto = new OfferingDto();
        String id = readString(r, "_id");
        dto.setId(id);
        dto.setBubbleId(id);
        dto.setCompanyId(readString(r, F_COMPANY));
        dto.setName(readString(r, F_NAME));
        dto.setType(readString(r, F_TYPE));
        dto.setStatus(readString(r, F_STATUS));
        dto.setDeliveryType(readString(r, F_DELIVERY_TYPE));
        dto.setPayOptions(readStringArray(r, F_PAY_OPTIONS));
        dto.setPriceSource(readString(r, F_PRICE_SOURCE));
        dto.setDefaultType(readString(r, F_DEFAULT_TYPE));
        dto.setLimitedVisibility(readBoolean(r, F_LIMITED_VISIBILITY));
        dto.setUnlimitedQuantity(readBoolean(r, F_UNLIMITED_QUANTITY));
        dto.setQuantityRequired(readBoolean(r, F_QUANTITY_REQUIRED));
        dto.setCreatedAt(readInstant(r, "Created Date"));
        return dto;
    }

    // --------------------------------------------------------------- writes

    /**
     * Bubble constraints JSON scoping to {@code companyId} (the merchant) plus an
     * optional {@code status} equals-filter ('Active' / 'Inactive').
     */
    public String buildConstraints(String companyId, String status) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_COMPANY, "equals", companyId));
        if (hasText(status)) {
            constraints.add(constraint(F_STATUS, "equals", status));
        }
        try {
            return objectMapper.writeValueAsString(constraints);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build Bubble constraints", e);
        }
    }

    /** Body for POST /obj/offerings — company-scoped; only mapped, non-null fields. */
    public Map<String, Object> toCreateBody(OfferingDto dto, String companyId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_COMPANY, companyId);
        putIfPresent(body, F_NAME, dto.getName());
        putIfPresent(body, F_TYPE, dto.getType());
        // Parity with the old @PrePersist: default new offerings to 'Active'.
        body.put(F_STATUS, hasText(dto.getStatus()) ? dto.getStatus() : "Active");
        putIfPresent(body, F_DELIVERY_TYPE, dto.getDeliveryType());
        putIfPresent(body, F_PAY_OPTIONS, dto.getPayOptions());
        putIfPresent(body, F_PRICE_SOURCE, dto.getPriceSource());
        putIfPresent(body, F_DEFAULT_TYPE, dto.getDefaultType());
        putIfPresent(body, F_LIMITED_VISIBILITY, dto.getLimitedVisibility());
        putIfPresent(body, F_UNLIMITED_QUANTITY, dto.getUnlimitedQuantity());
        putIfPresent(body, F_QUANTITY_REQUIRED, dto.getQuantityRequired());
        return body;
    }

    /**
     * Partial body for PATCH /obj/offerings/{id} — only the PUT-contract fields
     * that are non-null: name, type, status, deliveryType, payOptions,
     * priceSource, defaultType, limitedVisibility, unlimitedQuantity,
     * quantityRequired.
     */
    public Map<String, Object> toUpdateBody(OfferingDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_NAME, dto.getName());
        putIfPresent(body, F_TYPE, dto.getType());
        putIfPresent(body, F_STATUS, dto.getStatus());
        putIfPresent(body, F_DELIVERY_TYPE, dto.getDeliveryType());
        putIfPresent(body, F_PAY_OPTIONS, dto.getPayOptions());
        putIfPresent(body, F_PRICE_SOURCE, dto.getPriceSource());
        putIfPresent(body, F_DEFAULT_TYPE, dto.getDefaultType());
        putIfPresent(body, F_LIMITED_VISIBILITY, dto.getLimitedVisibility());
        putIfPresent(body, F_UNLIMITED_QUANTITY, dto.getUnlimitedQuantity());
        putIfPresent(body, F_QUANTITY_REQUIRED, dto.getQuantityRequired());
        return body;
    }

    /** The merchant a Bubble record belongs to (for ownership checks). */
    public String companyOf(Map<String, Object> record) {
        return readString(record, F_COMPANY);
    }

    // ----------------------------------------------- inventory<->offering link
    //
    // INFERRED, UNVERIFIED MODEL of the assign/unassign endpoints.
    //
    // The Postgres `inventory_offerings` join table is analytics-only. In Bubble
    // the link is modelled here as a LIST FIELD ON THE OFFERING (F_INVENTORY_LIST,
    // "Inventory") holding the linked inventory ids. ASSUMPTIONS, ALL UNVERIFIED:
    //   1. The list lives on the OFFERING side (could instead be a list of
    //      offerings on the inventory record — would flip these helpers).
    //   2. Bubble's PATCH cannot add/remove a single list element, so we
    //      read the current list via get(), modify it in memory, and write the
    //      WHOLE list back. (If Bubble does support element add/remove ops, this
    //      could be simplified and made less race-prone.)
    //   3. List elements are plain inventory id strings.
    // Both operations are IDEMPOTENT: assign skips an already-present id, unassign
    // is a no-op when the id is absent (returns null to signal "no write needed").

    /** Read the current inventory-id list off an offering record. */
    public List<String> inventoryIdsOf(Map<String, Object> record) {
        return readStringList(record, F_INVENTORY_LIST);
    }

    /**
     * Compute the new inventory list with {@code inventoryId} added (idempotent).
     * Returns {@code null} when the id is already present (no write needed).
     */
    public Map<String, Object> addInventoryToList(Map<String, Object> record, String inventoryId) {
        List<String> current = inventoryIdsOf(record);
        if (current.contains(inventoryId)) {
            return null;
        }
        List<String> updated = new ArrayList<>(current);
        updated.add(inventoryId);
        return inventoryListBody(updated);
    }

    /**
     * Compute the new inventory list with {@code inventoryId} removed (idempotent).
     * Returns {@code null} when the id is absent (no write needed).
     */
    public Map<String, Object> removeInventoryFromList(Map<String, Object> record, String inventoryId) {
        List<String> current = inventoryIdsOf(record);
        if (!current.contains(inventoryId)) {
            return null;
        }
        List<String> updated = new ArrayList<>(current);
        updated.removeIf(inventoryId::equals);
        return inventoryListBody(updated);
    }

    private Map<String, Object> inventoryListBody(List<String> ids) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_INVENTORY_LIST, ids);
        return body;
    }

    // --------------------------------------------------------------- helpers

    private static Map<String, Object> constraint(String key, String type, Object value) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("key", key);
        c.put("constraint_type", type);
        c.put("value", value);
        return c;
    }

    private static void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        if (value instanceof String[] arr && arr.length == 0) {
            return;
        }
        body.put(key, value);
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

    private static Boolean readBoolean(Map<String, Object> r, String key) {
        if (r == null) {
            return null;
        }
        Object v = r.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(v).trim();
        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes")) {
            return Boolean.TRUE;
        }
        if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no")) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** A Bubble list field surfaced as a {@code String[]} for the DTO. */
    private static String[] readStringArray(Map<String, Object> r, String key) {
        List<String> list = readStringList(r, key);
        return list.isEmpty() ? null : list.toArray(new String[0]);
    }

    /** A Bubble list field read as a {@code List<String>} (never null). */
    @SuppressWarnings("unchecked")
    private static List<String> readStringList(Map<String, Object> r, String key) {
        List<String> out = new ArrayList<>();
        if (r == null) {
            return out;
        }
        Object v = r.get(key);
        if (v == null) {
            return out;
        }
        if (v instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        } else {
            String s = String.valueOf(v);
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return out;
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
