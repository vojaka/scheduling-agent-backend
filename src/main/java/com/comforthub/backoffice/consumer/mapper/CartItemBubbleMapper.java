package com.comforthub.backoffice.consumer.mapper;

import com.comforthub.backoffice.consumer.dto.AddCartItemRequest;
import com.comforthub.backoffice.consumer.dto.CartItemDto;
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
 * Translates between the consumer {@link CartItemDto} and the Bubble
 * {@code cartitem} object, and assembles the parameter maps for the Bubble
 * cart <b>workflows</b>. Single home of cart-side Bubble field keys and
 * workflow names.
 *
 * <p>Field keys are from {@code comforthub_schema.md} ({@code cartitem}, 34
 * fields, verified 2026-07-01). Workflow parameter shapes are from the Bubble
 * editor inventory ({@code add-to-cart-discovery.md}) — the ones not yet
 * exercised against {@code version-test} are flagged
 * {@code TODO(verify vs version-test)} at their builders below.
 */
@Component
public class CartItemBubbleMapper {

    /** Bubble Data API object type. */
    public static final String TYPE = "cartitem";

    // ===== Bubble workflow endpoint names (Cart / Cart Processing folders) =====
    /** Add-to-cart entry point — creates/merges the line, reserves stock, attaches the order. */
    public static final String WF_ADD_TO_CART = "adding_to_cart_attributes(be)";
    /** Line removal — reverts reserved stock, cleans up the parent order. */
    public static final String WF_DELETE_CART_ITEM = "delete_cart_item";
    /** Line-total recompute after a quantity change. */
    public static final String WF_CART_ITEM_RECALC = "cart_item_recalc";

    // ===== cartitem field keys (comforthub_schema.md) =====
    static final String F_CART        = "Cart";
    static final String F_INVENTORY   = "Inventory";
    static final String F_OFFERING    = "Offering";
    static final String F_ORDER       = "Order";
    static final String F_STORE       = "Store (single)";
    static final String F_QUANTITY    = "Quantity";
    static final String F_STATUS      = "Cart Item Status";
    static final String F_TYPE        = "Type";
    static final String F_ADDONS      = "Addon (list)";
    static final String F_IS_DELETED  = "is Deleted";
    static final String F_EXPIRES     = "Date - Expiration Date";
    static final String F_UNIT_TOTAL  = "1 pcs - Total Cost - W VAT";
    static final String F_LINE_TOTAL  = "Total Cart Item Cost W VAT";
    static final String F_LINE_VAT    = "Total Cart Item VAT";

    private final ObjectMapper objectMapper;

    public CartItemBubbleMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- reads

    /** Map one Bubble {@code cartitem} record to the consumer DTO. */
    public CartItemDto toDto(Map<String, Object> r) {
        CartItemDto dto = new CartItemDto();
        dto.setId(readString(r, "_id"));
        dto.setCartId(readString(r, F_CART));
        dto.setInventoryId(readString(r, F_INVENTORY));
        dto.setOfferingId(readString(r, F_OFFERING));
        dto.setOrderId(readString(r, F_ORDER));
        dto.setStoreId(readString(r, F_STORE));
        dto.setQuantity(readInteger(r, F_QUANTITY));
        dto.setStatus(readString(r, F_STATUS));
        dto.setType(readString(r, F_TYPE));
        dto.setAddonIds(readStringListOrNull(r, F_ADDONS));
        dto.setUnitPriceWithVat(readBigDecimal(r, F_UNIT_TOTAL));
        dto.setTotalWithVat(readBigDecimal(r, F_LINE_TOTAL));
        dto.setTotalVat(readBigDecimal(r, F_LINE_VAT));
        dto.setExpiresAt(readInstant(r, F_EXPIRES));
        return dto;
    }

    /** The cart a Bubble {@code cartitem} belongs to (ownership checks). */
    public String cartOf(Map<String, Object> record) {
        return readString(record, F_CART);
    }

    /** Whether a Bubble {@code cartitem} is soft-deleted. */
    public boolean isDeleted(Map<String, Object> record) {
        Object v = record == null ? null : record.get(F_IS_DELETED);
        if (v instanceof Boolean b) {
            return b;
        }
        return v != null && ("true".equalsIgnoreCase(String.valueOf(v))
                || "yes".equalsIgnoreCase(String.valueOf(v)));
    }

    // ------------------------------------------------------------ constraints

    /** Constraints selecting the active (non-deleted) lines of {@code cartId}. */
    public String activeItemsOfCart(String cartId) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_CART, "equals", cartId));
        constraints.add(constraint(F_IS_DELETED, "not equal", true));
        return writeConstraints(constraints);
    }

    // ------------------------------------------------------ workflow payloads

    /**
     * Parameters for {@code adding_to_cart_attributes(be)}. The customer and
     * added-by are pinned to the authenticated user — the workflow's own
     * Client-role branching (auto-expiry scheduling etc.) then applies.
     *
     * <p>The core keys ({@code inventory}, {@code addon}, {@code quantity},
     * {@code offering}, {@code customer_individual}, {@code cart_item_type},
     * {@code plan}) are documented from the Bubble editor; the tail keys
     * ({@code cart_item_status}, {@code store}, {@code added_by}) were listed
     * but not expanded.
     * TODO(verify vs version-test): confirm the tail parameter names and
     * whether any additional required keys exist (e.g. {@code addon_check},
     * {@code add_to_existing_order}) before go-live.
     */
    public Map<String, Object> addToCartParams(AddCartItemRequest req, String userId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("inventory", req.getInventoryId());
        params.put("offering", req.getOfferingId());
        params.put("quantity", req.getQuantity() != null ? req.getQuantity() : 1);
        params.put("customer_individual", userId);
        params.put("added_by", userId);
        putIfPresent(params, "store", req.getStoreId());
        putIfPresent(params, "addon", req.getAddonIds());
        putIfPresent(params, "cart_item_type", req.getCartItemType());
        putIfPresent(params, "cart_item_status", req.getCartItemStatus());
        return params;
    }

    /**
     * Parameters for {@code delete_cart_item}. The workflow's 7 steps are
     * documented (stock revert, last-item order cleanup) but its parameter
     * list was not visible in the editor capture.
     * TODO(verify vs version-test): confirm the parameter key ({@code
     * cart_item} assumed) and any flags ({@code override?}, {@code
     * added_by_role}) against version-test.
     */
    public Map<String, Object> deleteCartItemParams(String cartItemId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("cart_item", cartItemId);
        return params;
    }

    /**
     * Parameters for {@code cart_item_recalc} — recompute line totals after a
     * quantity change. Only the workflow's name is documented.
     * TODO(verify vs version-test): confirm the parameter key ({@code
     * cart_item} assumed) and that the workflow rolls unit costs up to line
     * totals the way {@code CART ITEM COST RECALC} does.
     */
    public Map<String, Object> recalcParams(String cartItemId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("cart_item", cartItemId);
        return params;
    }

    /** Data API body setting the line's absolute quantity. */
    public Map<String, Object> quantityUpdateBody(int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_QUANTITY, quantity);
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
