package com.comforthub.backoffice.mapper;

import com.comforthub.backoffice.dto.OfferingDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between the backoffice {@link OfferingDto} (the UI contract) and
 * the Bubble {@code offerings} object.
 */
@Component
public class OfferingBubbleMapper {

    /** Bubble Data API object type. */
    public static final String TYPE = "offerings";

    static final String F_COMPANY = "Belongs to";
    static final String F_NAME = "Offering name";
    static final String F_TYPE = "type";
    static final String F_STATUS = "Offering Activity Status";
    static final String F_DELIVERY_TYPE = "Delivery Method Precision";
    static final String F_PAY_OPTIONS = "Pay Options for this Offering";
    static final String F_PRICE_SOURCE = "Price - Price Source";
    static final String F_DEFAULT_TYPE = "Default Type";
    static final String F_LIMITED_VISIBILITY = "Limited visibility";
    static final String F_UNLIMITED_QUANTITY = "Unlimited quantity in stock";
    static final String F_QUANTITY_REQUIRED = "quantity required";

    static final String F_INVENTORY_LIST = "Inventory";

    // Price - Manual Inventory Price per single measurement.
    static final String F_PRICE = "Price - Manual Inventory Price per single measurement";

    // Default Offering boolean.
    static final String F_DEFAULT_OFFERING = "Default Offering";

    // Min Quantity.
    static final String F_MIN_QUANTITY = "Q - Minimum QNTY Order";

    // Max Quantity.
    static final String F_MAX_QUANTITY = "Q - Maximum QNTY Order";

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
        dto.setPriceSource(mapPriceSourceToUi(readString(r, F_PRICE_SOURCE)));
        dto.setDefaultType(readString(r, F_DEFAULT_TYPE));
        dto.setLimitedVisibility(readBoolean(r, F_LIMITED_VISIBILITY));
        dto.setUnlimitedQuantity(readBoolean(r, F_UNLIMITED_QUANTITY));
        dto.setQuantityRequired(readBoolean(r, F_QUANTITY_REQUIRED));
        dto.setPrice(readBigDecimal(r, F_PRICE));
        dto.setDefaultOffering(readBoolean(r, F_DEFAULT_OFFERING));
        dto.setMinQuantity(readInteger(r, F_MIN_QUANTITY));
        dto.setMaxQuantity(readInteger(r, F_MAX_QUANTITY));
        dto.setCreatedAt(readInstant(r, "Created Date"));
        return dto;
    }

    // --------------------------------------------------------------- writes

    /**
     * Bubble constraints JSON scoping to {@code companyId} (the merchant) plus an
     * optional {@code status} equals-filter (e.g. 'Active', 'Draft', 'Archive', or 'Inactive').
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
        body.put(F_STATUS, hasText(dto.getStatus()) ? dto.getStatus() : "Active");
        putIfPresent(body, F_DELIVERY_TYPE, dto.getDeliveryType());
        putIfPresent(body, F_PAY_OPTIONS, dto.getPayOptions());
        putIfPresent(body, F_PRICE_SOURCE, mapPriceSourceToBubble(dto.getPriceSource()));
        putIfPresent(body, F_DEFAULT_TYPE, dto.getDefaultType());
        putIfPresent(body, F_LIMITED_VISIBILITY, dto.getLimitedVisibility());
        putIfPresent(body, F_UNLIMITED_QUANTITY, dto.getUnlimitedQuantity());
        putIfPresent(body, F_QUANTITY_REQUIRED, dto.getQuantityRequired());
        putIfPresent(body, F_PRICE, dto.getPrice());
        putIfPresent(body, F_DEFAULT_OFFERING, dto.getDefaultOffering());
        putIfPresent(body, F_MIN_QUANTITY, dto.getMinQuantity());
        putIfPresent(body, F_MAX_QUANTITY, dto.getMaxQuantity());
        return body;
    }

    /**
     * Partial body for PATCH /obj/offerings/{id} — only the PUT-contract fields
     * that are non-null.
     */
    public Map<String, Object> toUpdateBody(OfferingDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_NAME, dto.getName());
        putIfPresent(body, F_TYPE, dto.getType());
        putIfPresent(body, F_STATUS, dto.getStatus());
        putIfPresent(body, F_DELIVERY_TYPE, dto.getDeliveryType());
        putIfPresent(body, F_PAY_OPTIONS, dto.getPayOptions());
        putIfPresent(body, F_PRICE_SOURCE, mapPriceSourceToBubble(dto.getPriceSource()));
        putIfPresent(body, F_DEFAULT_TYPE, dto.getDefaultType());
        putIfPresent(body, F_LIMITED_VISIBILITY, dto.getLimitedVisibility());
        putIfPresent(body, F_UNLIMITED_QUANTITY, dto.getUnlimitedQuantity());
        putIfPresent(body, F_QUANTITY_REQUIRED, dto.getQuantityRequired());
        putIfPresent(body, F_PRICE, dto.getPrice());
        putIfPresent(body, F_DEFAULT_OFFERING, dto.getDefaultOffering());
        putIfPresent(body, F_MIN_QUANTITY, dto.getMinQuantity());
        putIfPresent(body, F_MAX_QUANTITY, dto.getMaxQuantity());
        return body;
    }

    /** The merchant a Bubble record belongs to (for ownership checks). */
    public String companyOf(Map<String, Object> record) {
        return readString(record, F_COMPANY);
    }

    // ----------------------------------------------- inventory<->offering link

    /** Read the current inventory-id list off an offering record. */
    public List<String> inventoryIdsOf(Map<String, Object> record) {
        return readStringList(record, F_INVENTORY_LIST);
    }

    /**
     * Compute the new inventory list with {@code inventoryId} added (idempotent).
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

    private static String[] readStringArray(Map<String, Object> r, String key) {
        List<String> list = readStringList(r, key);
        return list == null ? new String[0] : list.toArray(new String[0]);
    }

    private static List<String> readStringList(Map<String, Object> r, String key) {
        if (r == null) {
            return null;
        }
        Object v = r.get(key);
        if (v == null) {
            return null;
        }
        List<String> out = new ArrayList<>();
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
        String s = readString(r, key);
        if (s == null) {
            return null;
        }
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
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

    private static String mapPriceSourceToBubble(String uiVal) {
        if (uiVal == null) return null;
        if (uiVal.equalsIgnoreCase("manual") || uiVal.equalsIgnoreCase("Offering Manual Price")) {
            return "offering_price";
        }
        if (uiVal.equalsIgnoreCase("computed") || uiVal.equalsIgnoreCase("Inventory Default Price")) {
            return "inventory_default_price";
        }
        return uiVal;
    }

    private static String mapPriceSourceToUi(String bubbleVal) {
        if (bubbleVal == null) return null;
        if (bubbleVal.equalsIgnoreCase("offering_price") || bubbleVal.equalsIgnoreCase("Offering Manual Price")) {
            return "manual";
        }
        if (bubbleVal.equalsIgnoreCase("inventory_default_price") || bubbleVal.equalsIgnoreCase("Inventory Default Price")) {
            return "computed";
        }
        return bubbleVal;
    }
}
