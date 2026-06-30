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
 * Translates between the backoffice {@link BookingDto} (the UI contract) and the
 * Bubble calendar-event object. This is the single place that knows Bubble's
 * field keys for bookings, so adapting to schema changes touches only this file.
 *
 * <h2>#1 THING TO VERIFY — the Bubble object type</h2>
 * There is <b>no {@code booking} type</b> in the ETL {@code TABLES_TO_SYNC} list
 * (see {@code sync_all.py}). The closest match for "calendar bookings" is
 * {@code events}, so {@link #TYPE} is set to <b>{@code "events"}</b> as a best
 * guess. <b>This MUST be verified against the live Bubble app before shipping</b>
 * — if calendar bookings are actually stored under a different type (e.g. an
 * appointment / reservation object), change {@link #TYPE} accordingly.
 *
 * <h2>INFERRED field aliases — VERIFY EACH</h2>
 * Unlike {@link OrderBubbleMapper} (whose keys are confirmed from live records),
 * <b>every Bubble field key below is INFERRED</b>. This app's Data API exposes
 * display-name keys (e.g. {@code "Merchant"}, {@code "Assigned User"},
 * {@code "Time - Start Time"} — confirmed pattern from the {@code shift} type),
 * and constraints use the same display-name keys. The guesses lean on the
 * conventions seen in {@code BubbleShift} (references like {@code "Assigned
 * User"}/{@code "Assigned Store"}, dates like {@code "Time - Start Time"}) and
 * the {@code order} type (scope key {@code "Merchant"}). Each constant is marked
 * INFERRED and must be checked against a live {@code events} record.
 */
@Component
public class BookingBubbleMapper {

    /**
     * Bubble Data API object type.
     * INFERRED / #1 TO VERIFY: no "booking" type exists in the ETL list; "events"
     * is the most likely calendar object. Confirm against the live Bubble app.
     */
    public static final String TYPE = "events";

    // ===== INFERRED Bubble field keys (NONE confirmed — verify each one) =====

    /** INFERRED: owning merchant — scope key. Matches the order type's "Merchant". */
    static final String F_COMPANY        = "Merchant";

    /** INFERRED: booking title/subject. Plain display-name guess. */
    static final String F_TITLE          = "Title";

    /** INFERRED: start instant. Follows BubbleShift's "Time - Start Time". */
    static final String F_START_TIME     = "Time - Start Time";

    /** INFERRED: end instant. Follows BubbleShift's "Time - End Time". */
    static final String F_END_TIME       = "Time - End Time";

    /** INFERRED: assigned worker (user ref). Follows BubbleShift's "Assigned User". */
    static final String F_WORKER         = "Assigned User";

    /** INFERRED: store ref. Follows BubbleShift's "Assigned Store". */
    static final String F_STORE          = "Assigned Store";

    /** INFERRED: customer display name (text). */
    static final String F_CUSTOMER_NAME  = "Customer Name";

    /** INFERRED: customer email (text). */
    static final String F_CUSTOMER_EMAIL = "Customer Email";

    /** Built-in Bubble created-date field, used as the default sort key. */
    public static final String SORT_CREATED_DATE = "Created Date";

    private final ObjectMapper objectMapper;

    public BookingBubbleMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- reads

    /** Map one Bubble {@code events} record to the UI DTO. */
    public BookingDto toDto(Map<String, Object> r) {
        BookingDto dto = new BookingDto();
        String id = readString(r, "_id");
        dto.setId(id);
        dto.setBubbleId(id);
        dto.setCompanyId(readString(r, F_COMPANY));
        dto.setStoreId(readString(r, F_STORE));
        dto.setWorkerId(readString(r, F_WORKER));
        dto.setTitle(readString(r, F_TITLE));
        dto.setCustomerName(readString(r, F_CUSTOMER_NAME));
        dto.setCustomerEmail(readString(r, F_CUSTOMER_EMAIL));
        dto.setStartTime(readInstant(r, F_START_TIME));
        dto.setEndTime(readInstant(r, F_END_TIME));
        dto.setCreatedAt(readInstant(r, "Created Date"));
        return dto;
    }

    // --------------------------------------------------------------- filters

    /**
     * Bubble constraints JSON scoping to {@code companyId} (the merchant), an
     * optional worker filter, and a date-window OVERLAP filter against
     * {@code [from, to]}.
     *
     * <p><b>Overlap modelling.</b> A booking {@code [startTime, endTime]} overlaps
     * the query window {@code [from, to]} iff {@code endTime >= from} AND
     * {@code startTime <= to} (the same predicate the old JPA query used).
     * Expressed as Bubble constraints:
     * <ul>
     *   <li>{@code endTime} <b>"greater than or equal to"</b> {@code from}</li>
     *   <li>{@code startTime} <b>"less than or equal to"</b> {@code to}</li>
     * </ul>
     *
     * <p><b>FLAG — constraint type names.</b> Bubble's documented date
     * constraint types are {@code "greater than"} / {@code "less than"} (strict).
     * Inclusive variants are not universally available, so the strict forms are
     * used here. Using strict {@code >}/{@code <} on the overlap bounds can miss
     * bookings that exactly touch an endpoint (endTime == from, or
     * startTime == to); if exact-touch inclusion matters, verify whether this
     * Bubble app accepts {@code "greater than or equal to"} /
     * {@code "less than or equal to"} and switch the constants below.
     *
     * <p><b>FLAG — date value format.</b> {@code from}/{@code to} are serialised
     * to ISO-8601 strings (Bubble's expected input for date constraints, same
     * assumption as date writes). Verify Bubble accepts ISO-8601 here and does
     * not require epoch millis.
     */
    public String buildConstraints(String companyId, String workerId,
                                   OffsetDateTime from, OffsetDateTime to) {
        // FLAG: strict bounds. Swap to "greater than or equal to" /
        // "less than or equal to" if this app supports inclusive date constraints.
        final String GTE = "greater than";
        final String LTE = "less than";

        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_COMPANY, "equals", companyId));
        if (hasText(workerId)) {
            constraints.add(constraint(F_WORKER, "equals", workerId));
        }
        // Overlap: endTime >= from AND startTime <= to.
        if (from != null) {
            constraints.add(constraint(F_END_TIME, GTE, from.toInstant().toString()));
        }
        if (to != null) {
            constraints.add(constraint(F_START_TIME, LTE, to.toInstant().toString()));
        }
        try {
            return objectMapper.writeValueAsString(constraints);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build Bubble constraints", e);
        }
    }

    // --------------------------------------------------------------- writes

    /**
     * Body for POST /obj/events — company-scoped; only mapped, non-null fields.
     * Date fields are sent as the incoming ISO-8601 strings from the DTO
     * (FLAG: assumes Bubble accepts ISO-8601 for date writes, same as elsewhere).
     */
    public Map<String, Object> toCreateBody(BookingDto dto, String companyId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_COMPANY, companyId);
        putIfPresent(body, F_TITLE, dto.getTitle());
        putIfPresent(body, F_START_TIME, dto.getStartTime());
        putIfPresent(body, F_END_TIME, dto.getEndTime());
        putIfPresent(body, F_WORKER, dto.getWorkerId());
        putIfPresent(body, F_STORE, dto.getStoreId());
        putIfPresent(body, F_CUSTOMER_NAME, dto.getCustomerName());
        putIfPresent(body, F_CUSTOMER_EMAIL, dto.getCustomerEmail());
        return body;
    }

    /**
     * Partial body for PATCH /obj/events/{id} — only the mutable, non-null
     * fields from the PUT contract (title, startTime, endTime, workerId,
     * storeId, customerName, customerEmail). Company scope is never changed.
     */
    public Map<String, Object> toUpdateBody(BookingDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_TITLE, dto.getTitle());
        putIfPresent(body, F_START_TIME, dto.getStartTime());
        putIfPresent(body, F_END_TIME, dto.getEndTime());
        putIfPresent(body, F_WORKER, dto.getWorkerId());
        putIfPresent(body, F_STORE, dto.getStoreId());
        putIfPresent(body, F_CUSTOMER_NAME, dto.getCustomerName());
        putIfPresent(body, F_CUSTOMER_EMAIL, dto.getCustomerEmail());
        return body;
    }

    /** The merchant a Bubble record belongs to (for ownership checks). */
    public String companyOf(Map<String, Object> record) {
        return readString(record, F_COMPANY);
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
