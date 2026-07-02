package com.comforthub.backoffice.consumer.mapper;

import com.comforthub.backoffice.consumer.dto.ConsumerAddressDto;
import com.comforthub.backoffice.consumer.dto.SaveAddressRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between the consumer {@link ConsumerAddressDto} and the Bubble
 * {@code address} object, and assembles the parameter maps for Bubble's
 * address <b>workflows</b> ({@code save_address} / {@code delete_address} —
 * both fully step-documented in bubble-backend-workflows.md).
 *
 * <p>Reads go through the Data API (field keys from
 * {@code comforthub_schema.md}, {@code address}, 24 fields). Creates and
 * deletes go through the workflows so Bubble's validation chain, primary-flag
 * management and audit logs keep running.
 */
@Component
public class AddressBubbleMapper {

    /** Bubble Data API object type. */
    public static final String TYPE = "address";

    // ===== Bubble workflow endpoint names (Address folder) =====
    /** Validates → creates the Address → links it to the user → audit log. */
    public static final String WF_SAVE_ADDRESS = "save_address";
    /** Soft-deletes; promotes the next address to primary if needed. */
    public static final String WF_DELETE_ADDRESS = "delete_address";

    // ===== address field keys (comforthub_schema.md) =====
    static final String F_OWNER_INDIVIDUAL = "Owner (Individual)";
    static final String F_STREET      = "Street";
    static final String F_HOUSE_NR    = "House number";
    static final String F_APARTMENT   = "Apartment";
    static final String F_FLOOR       = "Floor(string)";
    static final String F_CITY        = "City";
    static final String F_POST_CODE   = "Post Code";
    static final String F_PROVINCE    = "Province";
    static final String F_COUNTRY     = "Country(string)";
    static final String F_LATITUDE    = "Latitude";
    static final String F_LONGITUDE   = "Longitude";
    static final String F_PROPERTY_TYPE = "Property Type";
    static final String F_PRIMARY     = "Primary?";
    static final String F_SHORT_STRING = "Address Short String";
    static final String F_NOTE        = "Note";
    static final String F_IS_DELETED  = "isDeleted";

    private final ObjectMapper objectMapper;

    public AddressBubbleMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- reads

    /** Map one Bubble {@code address} record to the consumer DTO. */
    public ConsumerAddressDto toDto(Map<String, Object> r) {
        ConsumerAddressDto dto = new ConsumerAddressDto();
        dto.setId(readString(r, "_id"));
        dto.setStreet(readString(r, F_STREET));
        dto.setHouseNumber(readString(r, F_HOUSE_NR));
        dto.setApartment(readString(r, F_APARTMENT));
        dto.setFloor(readString(r, F_FLOOR));
        dto.setCity(readString(r, F_CITY));
        dto.setPostCode(readString(r, F_POST_CODE));
        dto.setProvince(readString(r, F_PROVINCE));
        dto.setCountry(readString(r, F_COUNTRY));
        dto.setLatitude(readString(r, F_LATITUDE));
        dto.setLongitude(readString(r, F_LONGITUDE));
        dto.setPropertyType(readString(r, F_PROPERTY_TYPE));
        dto.setPrimary(readBoolean(r, F_PRIMARY));
        dto.setShortString(readString(r, F_SHORT_STRING));
        dto.setNote(readString(r, F_NOTE));
        dto.setCreatedAt(readInstant(r, "Created Date"));
        return dto;
    }

    /** The individual owner of a Bubble {@code address} (ownership checks). */
    public String ownerOf(Map<String, Object> record) {
        return readString(record, F_OWNER_INDIVIDUAL);
    }

    /** Whether a Bubble {@code address} is soft-deleted. */
    public boolean isDeleted(Map<String, Object> record) {
        return Boolean.TRUE.equals(readBoolean(record, F_IS_DELETED));
    }

    // ------------------------------------------------------------ constraints

    /** Constraints selecting the active addresses owned by {@code userId}. */
    public String activeAddressesOfUser(String userId) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_OWNER_INDIVIDUAL, "equals", userId));
        constraints.add(constraint(F_IS_DELETED, "not equal", true));
        return writeConstraints(constraints);
    }

    // ------------------------------------------------------ workflow payloads

    /**
     * Parameters for {@code save_address} (all 19 documented; the consumer API
     * always links as a user address). {@code address_short_string} must be
     * pre-formatted by the caller — Bubble does not compute it.
     */
    public Map<String, Object> saveAddressParams(SaveAddressRequest req, String userId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("user", userId);
        params.put("is_user_address", true);
        putIfPresent(params, "street_name", req.getStreet());
        putIfPresent(params, "house_nr", req.getHouseNumber());
        putIfPresent(params, "apt_nr", req.getApartment());
        putIfPresent(params, "floor_nr", req.getFloor());
        putIfPresent(params, "city_name", req.getCity());
        putIfPresent(params, "postal_code", req.getPostCode());
        putIfPresent(params, "province_name", req.getProvince());
        putIfPresent(params, "country_name", req.getCountry());
        putIfPresent(params, "latitude", req.getLatitude());
        putIfPresent(params, "longitude", req.getLongitude());
        putIfPresent(params, "property_type", req.getPropertyType());
        putIfPresent(params, "primary", req.getPrimary());
        putIfPresent(params, "address_short_string", req.getShortString());
        return params;
    }

    /** Parameters for {@code delete_address} (both documented). */
    public Map<String, Object> deleteAddressParams(String addressId, String userId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("address", addressId);
        params.put("user", userId);
        return params;
    }

    /**
     * Data API PATCH body for {@code PUT /addresses/{id}}. Bubble has no
     * documented <i>update</i> workflow (the Bubble front-end edits Address
     * records directly), so updates patch the record with the same field
     * mapping {@code save_address} uses on create.
     * TODO(verify vs version-test): "Property Type" is an option-set reference —
     * confirm the Data API accepts the display value on write.
     */
    public Map<String, Object> toUpdateBody(SaveAddressRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_STREET, req.getStreet());
        putIfPresent(body, F_HOUSE_NR, req.getHouseNumber());
        putIfPresent(body, F_APARTMENT, req.getApartment());
        putIfPresent(body, F_FLOOR, req.getFloor());
        putIfPresent(body, F_CITY, req.getCity());
        putIfPresent(body, F_POST_CODE, req.getPostCode());
        putIfPresent(body, F_PROVINCE, req.getProvince());
        putIfPresent(body, F_COUNTRY, req.getCountry());
        putIfPresent(body, F_LATITUDE, req.getLatitude());
        putIfPresent(body, F_LONGITUDE, req.getLongitude());
        putIfPresent(body, F_PROPERTY_TYPE, req.getPropertyType());
        putIfPresent(body, F_PRIMARY, req.getPrimary());
        putIfPresent(body, F_SHORT_STRING, req.getShortString());
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
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
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
        Object v = r == null ? null : r.get(key);
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
        Object v = r == null ? null : r.get(key);
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
