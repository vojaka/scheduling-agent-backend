package com.comforthub.backoffice.mapper;

import com.comforthub.backoffice.dto.AvailabilityDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between the backoffice {@link AvailabilityDto} (the UI contract) and
 * the Bubble {@code availability} object. Single place that knows Bubble's field
 * keys for availability, so schema changes touch only this file.
 *
 * <p><b>Field keys are VERIFIED — not inferred.</b> They are the exact <em>raw</em>
 * Data API keys the live ETL reads: see {@code sync.py#sync_availability} and
 * {@link com.comforthub.backoffice.model.BubbleAvailability} (both syncing live
 * production data). The {@code availability} type exposes raw suffixed keys
 * ({@code thing_option_things}, ...), matching the {@code store} type and unlike
 * the display-name {@code inventory}/{@code offerings} types.
 *
 * <p>The live Bubble schema/swagger could not be re-confirmed from CI (egress
 * policy blocks {@code comforthub.ee}); these keys rest on the ETL, the strongest
 * in-repo evidence, exercised against live data hourly.
 *
 * <p><b>Option-set note:</b> {@code thingType} is Bubble's {@code things} option
 * set and {@code availableDays} its {@code calendar days} option set. Their values
 * ({@code "Store"}/{@code "Worker"}/{@code "User"}, {@code "Monday"}…) are passed
 * through verbatim — the caller must send the exact option values so Bubble's
 * option-set constraint/write matches.
 */
@Component
public class AvailabilityBubbleMapper {

    /** Bubble Data API object type (singular, per the ETL {@code /availability} endpoint). */
    public static final String TYPE = "availability";

    // ===== VERIFIED raw Bubble field keys (from sync.py / BubbleAvailability ETL) =====

    /** What the profile is attached to — {@code things} option set. VERIFIED: {@code thing_option_things}. */
    static final String F_THING_TYPE = "thing_option_things";

    /** Linked store/worker id (text). VERIFIED: {@code thing_id_text}. */
    static final String F_THING_ID = "thing_id_text";

    /** Opening days — list of {@code calendar days} options. VERIFIED: {@code usual_available_days_list_option_calendar_days}. */
    static final String F_AVAILABLE_DAYS = "usual_available_days_list_option_calendar_days";

    /** Workday open hour. VERIFIED: {@code workday_availability___start_number}. */
    static final String F_WORKDAY_START = "workday_availability___start_number";

    /** Workday close hour. VERIFIED: {@code workday_availability___end_number}. */
    static final String F_WORKDAY_END = "workday_availability___end_number";

    /** Weekend open hour. VERIFIED: {@code weekend_availability___start_number}. */
    static final String F_WEEKEND_START = "weekend_availability___start_number";

    /** Weekend close hour. VERIFIED: {@code weekend_availability___end_number}. */
    static final String F_WEEKEND_END = "weekend_availability___end_number";

    private final ObjectMapper objectMapper;

    public AvailabilityBubbleMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- reads

    /** Map one Bubble {@code availability} record to the UI DTO. */
    public AvailabilityDto toDto(Map<String, Object> r) {
        AvailabilityDto dto = new AvailabilityDto();
        String id = readString(r, "_id");
        dto.setId(id);
        dto.setBubbleId(id);
        dto.setThingType(readString(r, F_THING_TYPE));
        dto.setThingId(readString(r, F_THING_ID));
        dto.setAvailableDays(readStringListOrNull(r, F_AVAILABLE_DAYS));
        dto.setWorkdayStartHour(readInteger(r, F_WORKDAY_START));
        dto.setWorkdayEndHour(readInteger(r, F_WORKDAY_END));
        dto.setWeekendStartHour(readInteger(r, F_WEEKEND_START));
        dto.setWeekendEndHour(readInteger(r, F_WEEKEND_END));
        dto.setCreatedAt(readInstant(r, "Created Date"));
        return dto;
    }

    /** The thing (store/worker) a Bubble availability record is attached to. */
    public String thingTypeOf(Map<String, Object> record) {
        return readString(record, F_THING_TYPE);
    }

    /** The linked store/worker id on a Bubble availability record. */
    public String thingIdOf(Map<String, Object> record) {
        return readString(record, F_THING_ID);
    }

    // ------------------------------------------------------- constraints/scope

    /** Constraints selecting the availability profile for one {@code (thingType, thingId)}. */
    public String byThing(String thingType, String thingId) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_THING_TYPE, "equals", thingType));
        constraints.add(constraint(F_THING_ID, "equals", thingId));
        return writeConstraints(constraints);
    }

    // --------------------------------------------------------------- writes

    /** Body for POST /obj/availability — the thing identity plus the schedule fields. */
    public Map<String, Object> toCreateBody(AvailabilityDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_THING_TYPE, dto.getThingType());
        putIfPresent(body, F_THING_ID, dto.getThingId());
        putSchedule(body, dto);
        return body;
    }

    /**
     * Partial body for PATCH /obj/availability/{id} — the schedule fields only.
     * The thing identity ({@code thingType}/{@code thingId}) is immutable, so it
     * is not re-written on update.
     */
    public Map<String, Object> toUpdateBody(AvailabilityDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putSchedule(body, dto);
        return body;
    }

    private void putSchedule(Map<String, Object> body, AvailabilityDto dto) {
        putIfPresent(body, F_AVAILABLE_DAYS, dto.getAvailableDays());
        putIfPresent(body, F_WORKDAY_START, dto.getWorkdayStartHour());
        putIfPresent(body, F_WORKDAY_END, dto.getWorkdayEndHour());
        putIfPresent(body, F_WEEKEND_START, dto.getWeekendStartHour());
        putIfPresent(body, F_WEEKEND_END, dto.getWeekendEndHour());
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
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        // An empty list means "not provided"; skip so a partial update never clears the field.
        if (value instanceof List<?> l && l.isEmpty()) {
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

    /** A Bubble list field as a {@code List<String>}, or {@code null} when absent/empty. */
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
