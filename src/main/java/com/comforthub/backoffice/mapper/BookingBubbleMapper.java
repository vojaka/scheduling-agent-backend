package com.comforthub.backoffice.mapper;

import com.comforthub.backoffice.dto.BookingDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between the backoffice {@link BookingDto} and the Bubble
 * {@code events} object (bookings are stored as {@code events}).
 *
 * <p><b>Confirmed from the live {@code events} data type:</b> title = {@code
 * "Name"}, start = {@code "Date - Date Range Start"}, end = {@code "Date - Date
 * Range End"}, worker = {@code "Worker"} (a User ref), and {@code "Service"} is
 * an Inventory ref.
 *
 * <h2>⚠️ Structural mismatch with the booking contract — needs a product call</h2>
 * The {@code events} type has <b>no company/merchant field, no store, and no
 * customer name/email text</b> (its customer is a {@code "Customer
 * (individual)"} User ref). Consequences, all flagged:
 * <ul>
 *   <li><b>Company scoping is indirect</b> via the booking's {@code Service} (an
 *       Inventory), whose {@code "Company"} field is confirmed. We resolve the
 *       company's inventory ids, then filter events by {@code Service in [ids]}.
 *       Bookings with no Service are excluded. <b>VERIFY</b> this is the intended
 *       ownership model (vs. scoping by the Worker's company).</li>
 *   <li>{@code storeId} / {@code customerName} / {@code customerEmail} have no
 *       {@code events} field and stay {@code null}.</li>
 *   <li><b>Create can't set the scope</b> — the DTO has no service id, so a
 *       created event won't be attributable to a company. Create is limited to
 *       title/time/worker; see {@code BookingController}.</li>
 * </ul>
 */
@Component
public class BookingBubbleMapper {

    /** Bubble Data API object type — bookings live under {@code events}. */
    public static final String TYPE = "events";

    // ===== Confirmed events field keys (from the live data type) =====
    static final String F_TITLE   = "Name";
    static final String F_START   = "Date - Date Range Start";
    static final String F_END     = "Date - Date Range End";
    static final String F_WORKER  = "Worker";
    /** Inventory ref — the booking's service; used for indirect company scoping. */
    static final String F_SERVICE = "Service";

    // ===== Indirect company scope: booking -> Service (Inventory) -> Company ==
    /** Bubble object type for inventory (its {@code Company} field is confirmed). */
    public static final String INVENTORY_TYPE = "inventory";
    static final String INVENTORY_COMPANY_FIELD = "Company";

    /** Built-in Bubble created-date field, used as the default sort key. */
    public static final String SORT_CREATED_DATE = "Created Date";

    private final ObjectMapper objectMapper;

    public BookingBubbleMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- reads

    /**
     * Map one Bubble {@code events} record to the UI DTO. {@code companyId} is set
     * by the controller (events has no company field of its own). {@code storeId}
     * / {@code customerName} / {@code customerEmail} have no {@code events} field
     * and are left null.
     */
    public BookingDto toDto(Map<String, Object> r) {
        BookingDto dto = new BookingDto();
        String id = readString(r, "_id");
        dto.setId(id);
        dto.setBubbleId(id);
        dto.setTitle(readString(r, F_TITLE));
        dto.setWorkerId(readString(r, F_WORKER));
        dto.setStartTime(readInstant(r, F_START));
        dto.setEndTime(readInstant(r, F_END));
        dto.setCreatedAt(readInstant(r, "Created Date"));
        return dto;
    }

    /** The Service (inventory id) a booking references — for company ownership checks. */
    public String serviceOf(Map<String, Object> record) {
        return readString(record, F_SERVICE);
    }

    // ------------------------------------------------------- constraints/scope

    /** Constraints selecting the inventories that belong to {@code companyId}. */
    public String inventoryCompanyConstraints(String companyId) {
        return writeConstraints(List.of(constraint(INVENTORY_COMPANY_FIELD, "equals", companyId)));
    }

    /** Extract the {@code _id}s from a page of Bubble records. */
    public List<String> idsOf(List<Map<String, Object>> records) {
        List<String> ids = new ArrayList<>();
        if (records != null) {
            for (Map<String, Object> m : records) {
                String id = readString(m, "_id");
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /**
     * Constraints scoping events to {@code Service in [serviceInventoryIds]} (the
     * company's inventories), an optional worker filter, and a date-window
     * OVERLAP against {@code [from, to]} ({@code end > from} AND {@code start < to}).
     *
     * <p>FLAG: strict {@code >}/{@code <} bounds (Bubble's documented date
     * constraint types) — exact-touch endpoints are excluded; date values sent as
     * ISO-8601. Verify against the live app if inclusive bounds / epoch millis are
     * needed.
     */
    public String buildConstraints(List<String> serviceInventoryIds, String workerId,
                                   OffsetDateTime from, OffsetDateTime to) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_SERVICE, "in", serviceInventoryIds));
        if (hasText(workerId)) {
            constraints.add(constraint(F_WORKER, "equals", workerId));
        }
        if (from != null) {
            constraints.add(constraint(F_END, "greater than", from.toInstant().toString()));
        }
        if (to != null) {
            constraints.add(constraint(F_START, "less than", to.toInstant().toString()));
        }
        return writeConstraints(constraints);
    }

    // --------------------------------------------------------------- writes

    /**
     * Body for POST /obj/events — title/time/worker only. NOTE: cannot set the
     * {@code Service}/company scope (no service id on the DTO), so a created event
     * is not attributable to a company until a Service is set elsewhere.
     */
    public Map<String, Object> toCreateBody(BookingDto dto) {
        return mutableFields(dto);
    }

    /** Partial body for PATCH /obj/events/{id} — title/time/worker only. */
    public Map<String, Object> toUpdateBody(BookingDto dto) {
        return mutableFields(dto);
    }

    private Map<String, Object> mutableFields(BookingDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_TITLE, dto.getTitle());
        putIfPresent(body, F_START, dto.getStartTime());
        putIfPresent(body, F_END, dto.getEndTime());
        putIfPresent(body, F_WORKER, dto.getWorkerId());
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
