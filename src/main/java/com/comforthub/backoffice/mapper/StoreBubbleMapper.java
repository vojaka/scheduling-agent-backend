package com.comforthub.backoffice.mapper;

import com.comforthub.backoffice.dto.StoreDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between the backoffice {@link StoreDto} (the UI contract) and the
 * Bubble {@code store} object. This is the single place that knows Bubble's
 * field keys for stores, so adapting to schema changes touches only this file.
 *
 * <p><b>Field keys are VERIFIED — not inferred.</b> Unlike the {@code inventory}
 * / {@code offerings} mappers (whose keys are display-name guesses), the
 * {@code store} keys below are the exact <em>raw</em> Data API keys the live ETL
 * reads: see {@code sync.py#sync_stores} and {@link com.comforthub.backoffice.model.BubbleStore}
 * (both actively syncing live production data). The {@code store} type therefore
 * exposes raw suffixed keys ({@code store_name_text}, ...), not the title-case
 * display names the newer {@code inventory}/{@code offerings} types use.
 *
 * <p>The live Bubble schema/swagger could not be re-confirmed from CI (egress
 * policy blocks {@code comforthub.ee}); these keys rest on the ETL, which is the
 * strongest in-repo evidence and is exercised against live data hourly.
 */
@Component
public class StoreBubbleMapper {

    /** Bubble Data API object type (singular, per the ETL {@code /store} endpoint). */
    public static final String TYPE = "store";

    // ===== VERIFIED raw Bubble field keys (from sync.py / BubbleStore ETL) =====

    /** Store name (text). VERIFIED: {@code store_name_text}. */
    static final String F_NAME = "store_name_text";

    /** Owning company/merchant (custom Merchant ref). VERIFIED: {@code company__single__custom____merchant}. */
    static final String F_COMPANY = "company__single__custom____merchant";

    /** Linked availability profile (custom Worker Availability ref). VERIFIED: {@code availability_custom_worker_availability}. */
    static final String F_AVAILABILITY = "availability_custom_worker_availability";

    /** Soft-delete flag (boolean). VERIFIED: {@code isdeleted_boolean}. */
    static final String F_IS_DELETED = "isdeleted_boolean";

    /** Built-in Bubble created-date field. */
    public static final String SORT_CREATED_DATE = "Created Date";

    /** Store-name sort key (raw field key), used to order the store list. */
    public static final String SORT_NAME = F_NAME;

    private final ObjectMapper objectMapper;

    public StoreBubbleMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- reads

    /** Map one Bubble {@code store} record to the UI DTO. */
    public StoreDto toDto(Map<String, Object> r) {
        StoreDto dto = new StoreDto();
        String id = readString(r, "_id");
        dto.setId(id);
        dto.setBubbleId(id);
        dto.setCompanyId(readString(r, F_COMPANY, "Company", "company"));
        dto.setName(readString(r, F_NAME, "Store Name", "name"));
        dto.setAvailabilityId(readString(r, F_AVAILABILITY, "Availability", "availability"));
        dto.setIsDeleted(readBoolean(r, F_IS_DELETED, "isDeleted", "is_deleted"));
        dto.setCreatedAt(readInstant(r, "Created Date", "created_at"));
        return dto;
    }

    // --------------------------------------------------------------- writes

    /**
     * Bubble constraints JSON scoping to {@code companyId} (the merchant) plus the
     * active filter (is_deleted != true). {@code "not equal" true} keeps records
     * where the flag is explicitly false as well as where it is absent/null.
     */
    public String buildConstraints(String companyId) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_COMPANY, "equals", companyId));
        constraints.add(constraint(F_IS_DELETED, "not equal", true));
        return writeConstraints(constraints);
    }

    /**
     * Bubble constraints JSON scoping to active (non-deleted) stores across all merchants.
     */
    public String activeStoresConstraints() {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_IS_DELETED, "not equal", true));
        return writeConstraints(constraints);
    }

    /** Body for POST /obj/store — company-scoped; only mapped, non-null fields. */
    public Map<String, Object> toCreateBody(StoreDto dto, String companyId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_COMPANY, companyId);
        putIfPresent(body, F_NAME, dto.getName());
        putIfPresent(body, F_AVAILABILITY, dto.getAvailabilityId());
        // New stores are active.
        body.put(F_IS_DELETED, false);
        return body;
    }

    /** Partial body for PATCH /obj/store/{id} — only the editable, non-null fields. */
    public Map<String, Object> toUpdateBody(StoreDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_NAME, dto.getName());
        putIfPresent(body, F_AVAILABILITY, dto.getAvailabilityId());
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

    private static void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value != null && !(value instanceof String s && s.isBlank())) {
            body.put(key, value);
        }
    }

    private static String readString(Map<String, Object> r, String... keys) {
        if (r == null) {
            return null;
        }
        for (String key : keys) {
            Object v = r.get(key);
            if (v != null) {
                String s = String.valueOf(v);
                if (!s.isBlank()) return s;
            }
        }
        return null;
    }

    private static Boolean readBoolean(Map<String, Object> r, String... keys) {
        if (r == null) {
            return null;
        }
        for (String key : keys) {
            Object v = r.get(key);
            if (v != null) {
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
            }
        }
        return null;
    }

    /** Bubble returns dates as epoch milliseconds; surface them as ISO-8601. */
    private static String readInstant(Map<String, Object> r, String... keys) {
        if (r == null) {
            return null;
        }
        for (String key : keys) {
            Object v = r.get(key);
            if (v != null) {
                if (v instanceof Number n) {
                    return Instant.ofEpochMilli(n.longValue()).toString();
                }
                String s = String.valueOf(v);
                if (!s.isBlank()) return s;
            }
        }
        return null;
    }
}
