package com.comforthub.backoffice.mapper;

import com.comforthub.backoffice.dto.OrderDto;
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
 * Translates between the backoffice {@link OrderDto} (the UI contract) and the
 * Bubble {@code order} object. This is the single place that knows Bubble's
 * field keys, so adapting to schema changes touches only this file.
 *
 * <p>Field keys below are the <b>real keys confirmed from live Bubble {@code
 * order} records</b> — this app's Data API exposes display-name keys (e.g.
 * {@code "Merchant"}, {@code "S - Order Progress Status"}), and constraints use
 * the same keys. The six status values come from the {@code Order Progress
 * Status} option set.
 *
 * <p><b>Customer name (read-side):</b> the order carries a {@code customer_name}
 * text field (comforthub_schema.md § Order; mirrored by {@code orders.customer_name}
 * in V3__promote_schema.sql). It is mapped read-side in {@link #toDto} so the
 * Orders list shows the client name. NOTE: the exact live Bubble key for this
 * field could not be re-confirmed against the swagger from CI (egress policy
 * blocks {@code comforthub.ee}); {@code customer_name} rests on the schema doc +
 * the mirror column and should be spot-checked live. It is read-only — create /
 * update bodies still set the customer via the {@code Customer (Individual)}
 * reference, so this change cannot affect writes.
 *
 * <p><b>Schema gaps:</b> the Bubble {@code order} has no equivalent of several
 * former {@code OrderEntity} fields, so they are intentionally unmapped (left
 * null in the DTO) and never sent in filters or writes:
 * <ul>
 *   <li>order number — no field</li>
 *   <li>assigned worker — no field on the order</li>
 *   <li>ready-by date — no field</li>
 *   <li>notes — no field</li>
 * </ul>
 * The {@code GET} filters for worker / order-number / customer-name are
 * therefore accepted but ignored (they cannot be expressed against this schema).
 */
@Component
public class OrderBubbleMapper {

    /** Bubble Data API object type. */
    public static final String TYPE = "order";

    // ===== Real Bubble field keys (confirmed from live order records) =====
    static final String F_COMPANY        = "Merchant";                  // owning merchant — scope key
    static final String F_STORE          = "Store";
    static final String F_CUSTOMER       = "Customer (Individual)";
    // Read-only display name of the customer. See class doc: verify the live key.
    static final String F_CUSTOMER_NAME  = "customer_name";
    static final String F_TYPE           = "Type";
    static final String F_AMOUNT         = "Total W VAT Order Amount";
    static final String F_PAYMENT_STATUS = "S - Order Payment Status";
    static final String F_STATUS         = "S - Order Progress Status";

    /** Built-in Bubble created-date field, used as the default sort key. */
    public static final String SORT_CREATED_DATE = "Created Date";

    // ===== Status: kanban key (UI) <-> "Order Progress Status" option value.
    // All six confirmed from the Bubble option set (exact casing matters for
    // writes; reads are normalised, see statusFromBubble). =====
    private static final Map<String, String> STATUS_TO_BUBBLE = new LinkedHashMap<>();
    static {
        STATUS_TO_BUBBLE.put("not_started", "Not started");
        STATUS_TO_BUBBLE.put("planned", "Planned");
        STATUS_TO_BUBBLE.put("preparation_in_progress", "Preparation In progress");
        STATUS_TO_BUBBLE.put("ready_for_pickup", "Ready for PickUp");
        STATUS_TO_BUBBLE.put("courier_assigned", "Courier Assigned");
        STATUS_TO_BUBBLE.put("completed", "Completed");
    }

    private final ObjectMapper objectMapper;

    public OrderBubbleMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- reads

    /** Map one Bubble {@code order} record to the UI DTO. */
    public OrderDto toDto(Map<String, Object> r) {
        OrderDto dto = new OrderDto();
        String id = readString(r, "_id");
        dto.setId(id);
        dto.setBubbleId(id);
        dto.setCompanyId(readString(r, F_COMPANY));
        dto.setStoreId(readString(r, F_STORE));
        dto.setCustomerId(readString(r, F_CUSTOMER));
        dto.setCustomerName(readString(r, F_CUSTOMER_NAME));
        dto.setType(readString(r, F_TYPE));
        dto.setAmount(readBigDecimal(r, F_AMOUNT));
        dto.setPaymentStatus(readString(r, F_PAYMENT_STATUS));
        dto.setStatus(statusFromBubble(readString(r, F_STATUS)));
        dto.setCreatedAt(readInstant(r, "Created Date"));
        dto.setUpdatedAt(readInstant(r, "Modified Date"));
        // Unmapped on the Bubble order (see class doc): orderNr, assignedTo,
        // readyBy, notes -> left null.
        return dto;
    }

    /** Bubble status display value → kanban key (lenient on case/spacing). */
    public String statusFromBubble(String bubbleValue) {
        if (bubbleValue == null) {
            return null;
        }
        String norm = normalize(bubbleValue);
        for (Map.Entry<String, String> e : STATUS_TO_BUBBLE.entrySet()) {
            if (normalize(e.getValue()).equals(norm)) {
                return e.getKey();
            }
        }
        // No match — pass the value through unchanged (may already be a kanban key).
        return bubbleValue;
    }

    // --------------------------------------------------------------- writes

    /** kanban key → Bubble status display value (null if unknown). */
    public String statusToBubble(String kanbanStatus) {
        return STATUS_TO_BUBBLE.get(kanbanStatus);
    }

    /**
     * Bubble constraints JSON scoping to {@code companyId} (the merchant) plus
     * the optional store filter. Worker / order-number / customer-name filters
     * are not expressible against the Bubble order schema and are ignored.
     */
    public String buildConstraints(String companyId, String storeId, String assignedTo,
                                   String orderNr, String customer) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_COMPANY, "equals", companyId));
        if (hasText(storeId)) {
            constraints.add(constraint(F_STORE, "equals", storeId));
        }
        try {
            return objectMapper.writeValueAsString(constraints);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build Bubble constraints", e);
        }
    }

    /** Body for POST /obj/order — company-scoped; only mapped, non-null fields. */
    public Map<String, Object> toCreateBody(OrderDto dto, String companyId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_COMPANY, companyId);
        putIfPresent(body, F_STORE, dto.getStoreId());
        putIfPresent(body, F_CUSTOMER, dto.getCustomerId());
        putIfPresent(body, F_TYPE, dto.getType());
        putIfPresent(body, F_AMOUNT, dto.getAmount());
        
        String paymentStatus = dto.getPaymentStatus();
        if (paymentStatus != null && !"Unpaid".equalsIgnoreCase(paymentStatus) && !paymentStatus.isBlank()) {
            body.put(F_PAYMENT_STATUS, paymentStatus);
        } else {
            body.put(F_PAYMENT_STATUS, null);
        }
        
        String status = dto.getStatus() != null ? dto.getStatus() : "not_started";
        body.put(F_STATUS, statusToBubble(status));
        return body;
    }

    /** Partial body for PATCH /obj/order/{id} — only mapped, non-null fields. */
    public Map<String, Object> toUpdateBody(OrderDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_STORE, dto.getStoreId());
        putIfPresent(body, F_CUSTOMER, dto.getCustomerId());
        putIfPresent(body, F_TYPE, dto.getType());
        putIfPresent(body, F_AMOUNT, dto.getAmount());
        
        String paymentStatus = dto.getPaymentStatus();
        if (paymentStatus != null && !"Unpaid".equalsIgnoreCase(paymentStatus) && !paymentStatus.isBlank()) {
            body.put(F_PAYMENT_STATUS, paymentStatus);
        } else {
            body.put(F_PAYMENT_STATUS, null);
        }
        
        return body;
    }

    /** Single-field body for the kanban status transition. */
    public Map<String, Object> statusUpdateBody(String kanbanStatus) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_STATUS, statusToBubble(kanbanStatus));
        return body;
    }

    /** The merchant a Bubble record belongs to (for ownership checks). */
    public String companyOf(Map<String, Object> record) {
        return readString(record, F_COMPANY);
    }

    /** The individual customer an order belongs to (consumer-API ownership checks). */
    public String customerOf(Map<String, Object> record) {
        return readString(record, F_CUSTOMER);
    }

    /**
     * Bubble constraints JSON scoping to the individual customer — used by the
     * consumer API, where the data boundary is the authenticated Bubble user
     * rather than a company.
     */
    public String customerConstraints(String customerUserId) {
        try {
            return objectMapper.writeValueAsString(
                    List.of(constraint(F_CUSTOMER, "equals", customerUserId)));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build Bubble constraints", e);
        }
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

    private static String normalize(String s) {
        return s == null ? null : s.trim().toLowerCase().replaceAll("[\\s_]+", " ");
    }
}
