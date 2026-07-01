package com.comforthub.backoffice.mapper;

import com.comforthub.backoffice.dto.InventoryDto;
import com.comforthub.backoffice.dto.InventoryExtensionDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between the backoffice {@link InventoryDto} (the UI contract) and
 * the Bubble {@code inventory} object.
 */
@Component
public class InventoryBubbleMapper {

    /** Bubble Data API object type. */
    public static final String TYPE = "inventory";

    static final String F_COMPANY = "Company";
    static final String F_NAME = "Name";
    static final String F_TYPE = "Category_Type";
    static final String F_MAIN_PRODUCT = "Main Product";
    static final String F_CATEGORY = "Category";
    static final String F_IS_DELETED = "Is Deleted";
    static final String F_OFFERINGS = "Offerings";

    // ===== Verified Bubble-parity CRUD fields =====
    static final String F_DESCRIPTION = "Description";
    static final String F_VAT = "VAT";
    static final String F_PRICE = "Price Base (w VAT)";
    static final String F_PREP_TIME = "Time(minutes) - Preparation time for Products";
    static final String F_DURATION = "Time(minutes) - Duration for Events";
    static final String F_WORKERS = "Workers";
    static final String F_IMAGE = "Main Image";
    static final String F_SECONDARY_IMAGES = "Images";

    /**
     * Forward list-of-links field on {@code inventory}, pointing at the
     * child {@code inventoryextensions} records. Verified against
     * comforthub_schema.md ("Inventory" type, "Extensions | -> inventoryextensions [list]").
     */
    static final String F_EXTENSIONS = "Extensions";

    /**
     * Bubble Data API object type for the "Inventory Extensions" child
     * records (icon/title/body rows on the "Modify Inventory" editor).
     * Verified against comforthub_schema.md, live 2026-07-01.
     */
    public static final String EXTENSION_TYPE = "inventoryextensions";

    // Verified 2026-07-01 against comforthub_schema.md "Inventory Extensions"
    // (`inventoryextensions`, 10 fields).
    static final String EXT_F_INVENTORY = "Inventory";
    static final String EXT_F_ICON = "Icon";
    static final String EXT_F_NAME = "Extension Name";
    static final String EXT_F_DESCRIPTION = "Extension Description";
    static final String EXT_F_POSITION = "List Position";

    /** Built-in Bubble created-date field, used as the default sort key. */
    public static final String SORT_CREATED_DATE = "Created Date";

    /** Orders extension rows by their Bubble "List Position" (nulls last). */
    public static final Comparator<InventoryExtensionDto> BY_POSITION =
            Comparator.comparing(d -> d.getPosition() == null ? Integer.MAX_VALUE : d.getPosition());

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
        dto.setDescription(readString(r, F_DESCRIPTION));
        dto.setVat(readString(r, F_VAT));
        dto.setPrice(readBigDecimal(r, F_PRICE));
        dto.setIsDeleted(readBoolean(r, F_IS_DELETED));
        dto.setPrepTimeMinutes(readInteger(r, F_PREP_TIME));
        dto.setDuration(readInteger(r, F_DURATION));
        dto.setWorkerIds(readStringListOrNull(r, F_WORKERS));
        dto.setImageUrl(readString(r, F_IMAGE));
        dto.setSecondaryImageUrls(readStringListOrNull(r, F_SECONDARY_IMAGES));
        dto.setCreatedAt(readInstant(r, "Created Date"));
        // NOTE: `extensions` is deliberately left unset here — populating it
        // requires a separate Bubble query against `inventoryextensions`
        // (this mapper method only ever sees one already-fetched record), so
        // callers attach it via toExtensionDto()/the controller's dedicated
        // GET /{id}/extensions path instead. See InventoryController.
        return dto;
    }

    /**
     * The offering ids linked to a Bubble {@code inventory} record.
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

    // ------------------------------------------------------------ extensions

    /** Map one Bubble {@code inventoryextensions} record to the UI DTO. */
    public InventoryExtensionDto toExtensionDto(Map<String, Object> r) {
        InventoryExtensionDto dto = new InventoryExtensionDto();
        dto.setId(readString(r, "_id"));
        dto.setIcon(readString(r, EXT_F_ICON));
        dto.setTitle(readString(r, EXT_F_NAME));
        dto.setBody(readString(r, EXT_F_DESCRIPTION));
        dto.setPosition(readInteger(r, EXT_F_POSITION));
        return dto;
    }

    /**
     * Bubble constraints JSON scoping {@code inventoryextensions} to the
     * owning inventory item, via the verified {@code Inventory}
     * back-reference field.
     */
    public String buildExtensionConstraints(String inventoryId) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(EXT_F_INVENTORY, "equals", inventoryId));
        try {
            return objectMapper.writeValueAsString(constraints);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build Bubble constraints", e);
        }
    }

    /** Body for POST /obj/inventoryextensions — links the row to its parent. */
    public Map<String, Object> toExtensionCreateBody(InventoryExtensionDto dto, String inventoryId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(EXT_F_INVENTORY, inventoryId);
        putIfPresent(body, EXT_F_ICON, dto.getIcon());
        putIfPresent(body, EXT_F_NAME, dto.getTitle());
        putIfPresent(body, EXT_F_DESCRIPTION, dto.getBody());
        putIfPresent(body, EXT_F_POSITION, dto.getPosition());
        return body;
    }

    /** Partial body for PATCH /obj/inventoryextensions/{id}. */
    public Map<String, Object> toExtensionUpdateBody(InventoryExtensionDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, EXT_F_ICON, dto.getIcon());
        putIfPresent(body, EXT_F_NAME, dto.getTitle());
        putIfPresent(body, EXT_F_DESCRIPTION, dto.getBody());
        putIfPresent(body, EXT_F_POSITION, dto.getPosition());
        return body;
    }

    /**
     * Body to overwrite the parent inventory's forward {@code Extensions}
     * list field with the current set of child ids (in display order), so
     * Bubble-side reads that follow the forward link (e.g. the Bubble editor
     * itself, or the frontend's {@code inventory_extension_card} reusable)
     * stay in sync with the child records this mapper writes.
     */
    public Map<String, Object> extensionsListBody(List<String> ids) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_EXTENSIONS, ids);
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
        putIfPresent(body, F_DESCRIPTION, dto.getDescription());
        putIfPresent(body, F_VAT, dto.getVat());
        putIfPresent(body, F_PRICE, dto.getPrice());
        putIfPresent(body, F_PREP_TIME, dto.getPrepTimeMinutes());
        putIfPresent(body, F_DURATION, dto.getDuration());
        putIfPresent(body, F_WORKERS, dto.getWorkerIds());
        putIfPresent(body, F_IMAGE, dto.getImageUrl());
        putIfPresent(body, F_SECONDARY_IMAGES, dto.getSecondaryImageUrls());
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
        putIfPresent(body, F_DESCRIPTION, dto.getDescription());
        putIfPresent(body, F_VAT, dto.getVat());
        putIfPresent(body, F_PRICE, dto.getPrice());
        putIfPresent(body, F_PREP_TIME, dto.getPrepTimeMinutes());
        putIfPresent(body, F_DURATION, dto.getDuration());
        putIfPresent(body, F_WORKERS, dto.getWorkerIds());
        putIfPresent(body, F_IMAGE, dto.getImageUrl());
        putIfPresent(body, F_SECONDARY_IMAGES, dto.getSecondaryImageUrls());
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
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        if (value instanceof Collection<?> c && c.isEmpty()) {
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

    private static Integer readInteger(Map<String, Object> r, String key) {
        Object v = r == null ? null : r.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(v).trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringListOrNull(Map<String, Object> r, String key) {
        Object v = r == null ? null : r.get(key);
        if (v == null) {
            return null;
        }
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    String s = String.valueOf(o);
                    if (!s.isBlank()) {
                        out.add(s);
                    }
                }
            }
        } else {
            String s = String.valueOf(v);
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return out.isEmpty() ? null : out;
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
