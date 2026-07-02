package com.comforthub.backoffice.consumer.mapper;

import com.comforthub.backoffice.consumer.dto.ConsumerProfileDto;
import com.comforthub.backoffice.consumer.dto.UpdateProfileRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between the consumer {@link ConsumerProfileDto} and the Bubble
 * {@code user} object (field keys from {@code comforthub_schema.md}, 43
 * fields). Profile writes patch the user record directly — Bubble's DB
 * triggers (FULL NAME COMPOSITION, PHONE CHANGE PENDING, profile
 * complete/incomplete checks) then run server-side exactly as they do for the
 * Bubble front-end.
 */
@Component
public class ConsumerProfileBubbleMapper {

    /** Bubble Data API object type. */
    public static final String TYPE = "user";

    // ===== Bubble workflow endpoint names =====
    /** Authorisation folder — sends the Twilio SMS verification code. */
    public static final String WF_SEND_PHONE_CODE = "verification";
    /**
     * Code check counterpart. TODO(verify vs version-test): NOT in the
     * documented 198-workflow inventory — Bubble's front-end calls the Twilio
     * plugin's Check-Code action directly, so no backend endpoint exists yet.
     * Either create this workflow in Bubble (Twilio Check-Code + return) or
     * rename this constant to the real endpoint once confirmed. The call
     * fails loudly (Bubble 404) until then — no silent fallback.
     */
    public static final String WF_CONFIRM_PHONE_CODE = "verification_check";

    // ===== user field keys (comforthub_schema.md) =====
    static final String F_FIRST_NAME  = "firstName";
    static final String F_LAST_NAME   = "lastName";
    static final String F_FULL_NAME   = "FullName";
    static final String F_PHONE       = "phoneNumber";
    static final String F_PHONE_PREFIX = "phonePrefix";
    static final String F_LANGUAGE    = "language";
    static final String F_VERIFIED    = "Verified Profile";
    static final String F_ROLES       = "Roles";
    static final String F_CART        = "Cart (single)";

    // ---------------------------------------------------------------- reads

    /** Map a Bubble {@code user} record to the consumer profile DTO. */
    public ConsumerProfileDto toDto(Map<String, Object> r) {
        ConsumerProfileDto dto = new ConsumerProfileDto();
        dto.setId(readString(r, "_id"));
        dto.setFirstName(readString(r, F_FIRST_NAME));
        dto.setLastName(readString(r, F_LAST_NAME));
        dto.setFullName(readString(r, F_FULL_NAME));
        dto.setPhoneNumber(readString(r, F_PHONE));
        dto.setPhonePrefix(readString(r, F_PHONE_PREFIX));
        dto.setLanguage(readString(r, F_LANGUAGE));
        dto.setVerifiedProfile(readBoolean(r, F_VERIFIED));
        dto.setRoles(readStringListOrNull(r, F_ROLES));
        dto.setCartId(readString(r, F_CART));
        return dto;
    }

    // --------------------------------------------------------------- writes

    /** Data API PATCH body for the self-editable profile fields. */
    public Map<String, Object> toUpdateBody(UpdateProfileRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_FIRST_NAME, req.getFirstName());
        putIfPresent(body, F_LAST_NAME, req.getLastName());
        putIfPresent(body, F_PHONE, req.getPhoneNumber());
        putIfPresent(body, F_PHONE_PREFIX, req.getPhonePrefix());
        putIfPresent(body, F_LANGUAGE, req.getLanguage());
        return body;
    }

    /** Parameters for the {@code verification} workflow (documented). */
    public Map<String, Object> sendCodeParams(String phoneNumber) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("phone_number", phoneNumber);
        return params;
    }

    /**
     * Parameters for the confirm-code workflow.
     * TODO(verify vs version-test): shape assumed ({@code phone_number} +
     * {@code code}) — see {@link #WF_CONFIRM_PHONE_CODE}.
     */
    public Map<String, Object> confirmCodeParams(String phoneNumber, String code) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("phone_number", phoneNumber);
        params.put("code", code);
        return params;
    }

    // --------------------------------------------------------------- helpers

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
}
