package com.comforthub.backoffice.mapper;

import com.comforthub.backoffice.dto.InventoryDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between the backoffice {@link InventoryDto} (the UI contract) and
 * the Bubble {@code inventory} object. This is the single place that knows
 * Bubble's field keys, so adapting to schema changes touches only this file.
 *
 * <p><b>WARNING — most of these field keys are INFERRED, not confirmed.</b>
 * Unlike {@link OrderBubbleMapper} (whose keys were verified against live
 * {@code order} records), no live {@code inventory} record was available when
 * this mapper was first written, so several constants below are best guesses
 * modelled on the Order pattern: this app's Data API exposes
 * <em>display-name</em> keys (e.g. {@code "Merchant"}, {@code "Store"},
 * {@code "S - Order Progress Status"}), and constraints use the same keys.
 * Each constant carries a comment with the reasoning. <b>Verify all of them
 * against a real Bubble {@code inventory} record before relying on this in
 * production</b> — a wrong key silently drops reads/writes (Bubble ignores
 * unknown keys rather than erroring).
 *
 * <p>{@code F_VAT}, {@code F_PRICE}, {@code F_DESCRIPTION}, {@code F_COMPANY},
 * {@code F_TYPE} keys are taken verbatim from the live meta export in
 * {@code comforthub_schema.md} (regenerated 2026-07-01), so those are
 * confirmed field <em>names</em>. {@code VAT}'s option-set <em>values</em>
 * (the {@code taxes} set) are still unverified — see backend issue #92 — but
 * that only affects what the UI should offer as choices, not this mapper: the
 * Bubble Data API accepts/returns option-set fields as plain display-text
 * strings, so no enum-decoding logic is needed here regardless.
 */
@Component
public class InventoryBubbleMapper {

    /** Bubble Data API object type. */
    public static final String TYPE = "inventory";

    // ===== Bubble field keys (see class doc for verification status) =====

    // Company/merchant scope key. VERIFIED against the Bubble `inventory` type:
    // the field is "Company" (not "Merchant" as the order type uses). Using
    // "Merchant" here caused: 404 "Field not found Merchant for type inventory".
    static final String F_COMPANY = "Company";

    // Display-name key for the catalog item name (Order uses "Type"/"Store"
    // style title-case display names; "Name" is Bubble's default for a text
    // field labelled name).
    static final String F_NAME = "Name";

    // Classification. The live inventory type has no plain "Type" field; the
    // closest is "Category_Type" (a Category Type). VERIFY — may not fit the old
    // string value; a wrong/absent key just leaves this null.
    static final String F_TYPE = "Category_Type";

    // Category ref at the Main Product level (parent category). Guessed from the
    // former JPA column main_product_id + this app's display-name convention.
    static final String F_MAIN_PRODUCT = "Main Product";

    // Category ref at the Sub Category level. Guessed from former JPA column
    // category_id + display-name convention.
    static final String F_CATEGORY = "Category";

    // VAT rate — option set "Taxes (VAT rates)" (internal name `taxes`).
    // CONFIRMED as the live field name/type in comforthub_schema.md; the set's
    // own values are still unverified (issue #92). Read/written as a plain
    // string, same as F_TYPE — Bubble's Data API doesn't require enum decoding.
    static final String F_VAT = "VAT";

    // Base price, VAT included. CONFIRMED live field name.
    static final String F_PRICE = "Price Base (w VAT)";

    // Free-text description. CONFIRMED live field name.
    static final String F_DESCRIPTION = "Description";

    // Soft-delete boolean. The ETL (BubbleSyncService) reads deletion flags from
    // "isDeleted"/"is_deleted"/"Deleted"; the display-name convention here favours
    // "Is Deleted". VERIFY: the exact key and whether the value is a real boolean.
    static final String F_IS_DELETED = "Is Deleted";

    // Time preparation (minutes) for Goods.
    static final String F_PREP_TIME = "Time(minutes) - Preparation time for Products";

    // Event duration (minutes) for Services.
    static final String F_DURATION = "Time(minutes) - Duration for Events";

    // Workers list.
    static final String F_WORKERS = "Workers";

    // Main Image.
    static final String F_MAIN_IMAGE = "Main Image";

    // Secondary Images list.
    static final String F_IMAGES = "Images";

    // INFERRED inventory<->offering link. Assumption: the inventory carries a
    // Bubble *list field of offerings* (list of offering ids), read directly for
    // getLinkedOfferings. The Postgres inventory_offerings join table is
    // analytics-only and does NOT exist in Bubble. The alternative model is that
    // each offering carries an inventory ref instead — if that turns out to be
    // the case, getLinkedOfferings must instead query offerings constrained by
    // this inventory id. *** FLAG: verify which direction the link lives on. ***
    static final String F_OFFERINGS = "Offerings";

    /** Built-in Bubble created-date field, used as the default sort key. */
    public static final String SORT_CREATED_DATE = "Created Date";

    private final ObjectMapper objectMapper;

    public InventoryBubbleMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- reads

    /** Map one Bubble {@code inventory} record to the UI DTO. */
    public InventoryDto toDto(Map<String, Object> r) {
        InventoryDto dto = new InventoryDto();
        String id = readString(r, "_id");
        dto.setId(id);
        dto.setBubbleId(id);
        dto.setCompanyId(readString(r, F_COMPANY));
        dto.setName(readString(r, F_NAME));
        dto.setType(readString(r, F_TYPE));
        dto.setMainProductId(readString(r, F_MAIN_PRODUCT));
        dto.setCategoryId(readString(r, F_CATEGORY));
        dto.setVat(readString(r, F_VAT));
        dto.setPriceBaseWithVat(readBigDecimal(r, F_PRICE));
        dto.setDescription(readString(r, F_DESCRIPTION));
        dto.setIsDeleted(readBoolean(r, F_IS_DELETED));
        dto.setPrepTimeMinutes(readInteger(r, F_PREP_TIME));
        dto.setDuration(readInteger(r, F_DURATION));
        dto.setWorkerIds(readStringList(r, F_WORKERS));
        dto.setImageUrl(readString(r, F_MAIN_IMAGE));
        dto.setSecondaryImageUrls(readStringList(r, F_IMAGES));
        dto.setCreatedAt(readInstant(r, "Created Date"));
        return dto;
    }

    /**
     * The offering ids linked to a Bubble {@code inventory} record.
     *
     * <p><b>INFERRED:</b> reads {@link #F_OFFERINGS} as a Bubble list field of
     * offering ids. Bubble serialises list fields as a JSON array; a single ref
     * may also arrive as a bare string, which is handled here. Returns an empty
     * list when the field is absent. *** VERIFY the field key and link direction
     * (see {@link #F_OFFERINGS}). ***
     */
    @SuppressWarnings("unchecked")
    public List<String> offeringsOf(Map<String, Object> record) {
        if (record == null) {
            return List.of();
        }
        Object v = record.get(F_OFFERINGS);
        if (v == null) {
            return List.of();
        }
        if (v instanceof List<?> list) {
            List<String> ids = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o != null) {
                    String s = String.valueOf(o);
                    if (!s.isBlank()) {
                        ids.add(s);
                    }
                }
            }
            return ids;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? List.of() : List.of(s);
    }

    /**
     * Compute the new offerings list with {@code offeringId} added (idempotent).
     * Returns {@code null} when the id is already present (no write needed). Used
     * to keep the inventory side of the bidirectional link in sync with the
     * offering side (see {@code OfferingController} assign/unassign).
     */
    public Map<String, Object> addOfferingToList(Map<String, Object> record, String offeringId) {
        List<String> current = offeringsOf(record);
        if (current.contains(offeringId)) {
            return null;
        }
        List<String> updated = new ArrayList<>(current);
        updated.add(offeringId);
        return offeringsListBody(updated);
    }

    /**
     * Compute the new offerings list with {@code offeringId} removed (idempotent).
     * Returns {@code null} when the id is absent (no write needed).
     */
    public Map<String, Object> removeOfferingFromList(Map<String, Object> record, String offeringId) {
        List<String> current = offeringsOf(record);
        if (!current.contains(offeringId)) {
            return null;
        }
        List<String> updated = new ArrayList<>(current);
        updated.removeIf(offeringId::equals);
        return offeringsListBody(updated);
    }

    private Map<String, Object> offeringsListBody(List<String> ids) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_OFFERINGS, ids);
        return body;
    }

    // --------------------------------------------------------------- writes

    /**
     * Bubble constraints JSON scoping to {@code companyId} (the merchant), the
     * active filter (is_deleted != true), plus an optional name substring search.
     */
    public String buildConstraints(String companyId, String search) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_COMPANY, "equals", companyId));
        // Active = non-deleted. "not equal" to true keeps records where the flag
        // is true=false as well as where it is absent/null.
        constraints.add(constraint(F_IS_DELETED, "not equal", true));
        if (hasText(search)) {
            constraints.add(constraint(F_NAME, "text contains", search));
        }
        try {
            return objectMapper.writeValueAsString(constraints);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build Bubble constraints", e);
        }
    }

    /** Body for POST /obj/inventory — company-scoped; only mapped, non-null fields. */
    public Map<String, Object> toCreateBody(InventoryDto dto, String companyId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_COMPANY, companyId);
        putIfPresent(body, F_NAME, dto.getName());
        putIfPresent(body, F_TYPE, dto.getType());
        putIfPresent(body, F_MAIN_PRODUCT, dto.getMainProductId());
        putIfPresent(body, F_CATEGORY, dto.getCategoryId());
        putIfPresent(body, F_VAT, dto.getVat());
        putIfPresent(body, F_PRICE, dto.getPriceBaseWithVat());
        putIfPresent(body, F_DESCRIPTION, dto.getDescription());
        putIfPresent(body, F_PREP_TIME, dto.getPrepTimeMinutes());
        putIfPresent(body, F_DURATION, dto.getDuration());
        putIfPresent(body, F_MAIN_IMAGE, dto.getImageUrl());
        if (dto.getWorkerIds() != null) {
            body.put(F_WORKERS, dto.getWorkerIds());
        }
        if (dto.getSecondaryImageUrls() != null) {
            body.put(F_IMAGES, dto.getSecondaryImageUrls());
        }
        // New items are active.
        body.put(F_IS_DELETED, false);
        return body;
    }

    /**
     * Partial body for PATCH /obj/inventory/{id} — only the editable, non-null
     * fields (name, type, mainProduct, category, VAT, price, description),
     * matching the PUT contract.
     */
    public Map<String, Object> toUpdateBody(InventoryDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_NAME, dto.getName());
        putIfPresent(body, F_TYPE, dto.getType());
        putIfPresent(body, F_MAIN_PRODUCT, dto.getMainProductId());
        putIfPresent(body, F_CATEGORY, dto.getCategoryId());
        putIfPresent(body, F_VAT, dto.getVat());
        putIfPresent(body, F_PRICE, dto.getPriceBaseWithVat());
        putIfPresent(body, F_DESCRIPTION, dto.getDescription());
        putIfPresent(body, F_PREP_TIME, dto.getPrepTimeMinutes());
        putIfPresent(body, F_DURATION, dto.getDuration());
        putIfPresent(body, F_MAIN_IMAGE, dto.getImageUrl());
        if (dto.getWorkerIds() != null) {
            body.put(F_WORKERS, dto.getWorkerIds());
        }
        if (dto.getSecondaryImageUrls() != null) {
            body.put(F_IMAGES, dto.getSecondaryImageUrls());
        }
        return body;
    }

    /** Single-field body for the soft-delete (is_deleted = true). */
    public Map<String, Object> softDeleteBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_IS_DELETED, true);
        return body;
    }

    /** The merchant a Bubble record belongs to (for ownership checks). */
    public String companyOf(Map<String, Object> record) {
        return readString(record, F_COMPANY);
    }

    /** Whether a Bubble record is soft-deleted (for active/ownership checks). */
    public boolean isDeleted(Map<String, Object> record) {
        return Boolean.TRUE.equals(readBoolean(record, F_IS_DELETED));
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
        if (value != null && !(value instanceof String s && s.isBlank())) {
            body.put(key, value);
        }
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

    private static BigDecimal readBigDecimal(Map<String, Object> r, String key) {
        String s = readString(r, key);
        if (s == null) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
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

    private static Integer readInteger(Map<String, Object> r, String key) {
        String s = readString(r, key);
        if (s == null) {
            return null;
        }
        try {
            // handle decimals if they come back from bubble (e.g. 60.0)
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(Map<String, Object> r, String key) {
        if (r == null) {
            return null;
        }
        Object v = r.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o != null) {
                    result.add(String.valueOf(o));
                }
            }
            return result;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? List.of() : List.of(s);
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
