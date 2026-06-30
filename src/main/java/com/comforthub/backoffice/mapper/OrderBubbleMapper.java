package com.comforthub.backoffice.mapper;

import com.comforthub.backoffice.dto.OrderDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between the backoffice {@link OrderDto} (the UI contract) and the
 * Bubble {@code order} object. <b>This is the single place that knows Bubble's
 * field aliases</b>, so adapting to schema changes — or replicating this
 * pattern to the other controllers — touches only this file.
 *
 * <h2>⚠️ PROVISIONAL — field aliases NOT yet verified</h2>
 * Bubble was unreachable when this was written (this session's egress policy
 * blocks {@code comforthub.ee}, and a Bubble incident also blocked fetching the
 * swagger from the user's side). The opaque, unguessable aliases below are left
 * as <b>labelled placeholders</b> ({@code TODO__*}) rather than guesses — the
 * comment on each gives the most likely real value, inferred from this repo's
 * established convention (see {@code BubbleShift} / {@code sync.py}):
 * <pre>
 *   &lt;field&gt;_&lt;type&gt;                e.g. notes_text, end_time_date, rate_number
 *   &lt;field&gt;_option_&lt;optionset&gt;     e.g. status_option_shift_approval_status
 *   &lt;field&gt;_custom_&lt;datatype&gt;       e.g. assigned_store_custom_store
 *   &lt;field&gt;_custom____&lt;datatype&gt;    e.g. assigned_company_custom____merchant
 * </pre>
 * To go live, replace every {@code TODO__*} value below with the exact key from:
 * <pre>
 *   curl -s -H "Authorization: Bearer $TOKEN" \
 *     https://comforthub.ee/version-test/api/1.1/meta/swagger.json \
 *     | jq '.definitions.order.properties | keys'
 * </pre>
 * and confirm the six status display strings against the {@code order} status
 * option set. Read paths already try several candidate keys (placeholder +
 * likely alias + display name), so reads degrade gracefully; the company
 * constraint and the write payloads need the exact key, so those are the
 * critical ones to verify.
 */
@Component
public class OrderBubbleMapper {

    /** Bubble Data API object type. */
    public static final String TYPE = "order";

    // ====================================================================
    // FIELD ALIASES — ⚠️ replace every TODO__ value with the verified key.
    // ====================================================================

    /** Owning merchant/company link. CRITICAL: used for the scope constraint.
     *  Likely: "company_custom____merchant" (cf. wagerate) or "..._custom____company". */
    static final String F_COMPANY = "TODO__company_merchant_link";

    /** Order number text. Likely: "order_nr_text" / "order_number_text". */
    static final String F_ORDER_NR = "TODO__order_nr_text";

    /** Customer display name. Likely: "customer_name_text" / "client_name_text". */
    static final String F_CUSTOMER_NAME = "TODO__customer_name_text";

    /** Customer record link. Likely: "customer_user" / "customer_custom_individual". */
    static final String F_CUSTOMER = "TODO__customer_link";

    /** Store link. Likely: "store_custom_store" (cf. shift assigned_store_custom_store). */
    static final String F_STORE = "TODO__store_custom_store";

    /** Order type. Likely: "type_option_order_type". */
    static final String F_TYPE = "TODO__type_option_order_type";

    /** Order amount/total. Likely: "amount_number" / "total_number" / "total_amount_number". */
    static final String F_AMOUNT = "TODO__amount_number";

    /** Payment status. Likely: "payment_status_option_payment_status" or "..._text". */
    static final String F_PAYMENT_STATUS = "TODO__payment_status_option_payment_status";

    /** Kanban/fulfilment status option set. Likely: "status_option_order_status". */
    static final String F_STATUS = "TODO__status_option_order_status";

    /** Assigned worker link. Likely: "assigned_worker_user" / "assigned_to_user". */
    static final String F_ASSIGNED_TO = "TODO__assigned_worker_user";

    /** Ready-by date. Likely: "ready_by_date" / "ready_at_date". */
    static final String F_READY_BY = "TODO__ready_by_date";

    /** Free-text notes. Likely: "notes_text". */
    static final String F_NOTES = "TODO__notes_text";

    /** Built-in Bubble created-date field, used as the default sort key. */
    public static final String SORT_CREATED_DATE = "Created Date";

    // Read-side candidates: try the (placeholder) alias, common display names and
    // alias variants. Reads still work once F_* are filled; the extra candidates
    // make display resilient to small alias differences. Order = priority.
    private static final String[] R_COMPANY      = {F_COMPANY, "Company", "company_custom____merchant", "company_custom____company"};
    private static final String[] R_ORDER_NR     = {F_ORDER_NR, "Order nr", "order_number_text"};
    private static final String[] R_CUSTOMER_NAME= {F_CUSTOMER_NAME, "Customer Name", "client_name_text"};
    private static final String[] R_CUSTOMER     = {F_CUSTOMER, "Customer", "customer_custom_individual"};
    private static final String[] R_STORE        = {F_STORE, "Store", "Assigned Store", "store_custom_store"};
    private static final String[] R_TYPE         = {F_TYPE, "Type"};
    private static final String[] R_AMOUNT       = {F_AMOUNT, "Amount", "Total", "total_number", "total_amount_number"};
    private static final String[] R_PAYMENT      = {F_PAYMENT_STATUS, "Payment Status", "payment_status_text"};
    private static final String[] R_STATUS       = {F_STATUS, "Status", "status_option_order_status"};
    private static final String[] R_ASSIGNED     = {F_ASSIGNED_TO, "Assigned Worker", "assigned_to_user"};
    private static final String[] R_READY_BY     = {F_READY_BY, "Ready By", "ready_at_date"};
    private static final String[] R_NOTES        = {F_NOTES, "notes", "Notes"};
    private static final String[] R_CREATED      = {"Created Date", "created_date"};
    private static final String[] R_MODIFIED     = {"Modified Date", "modified_date"};

    // ====================================================================
    // STATUS MAPPING — kanban key (UI) <-> Bubble status option display value.
    // ⚠️ Values derived by title-casing the kanban keys; confirm exact Bubble
    // wording (capitalisation/spacing) against the order status option set.
    // ====================================================================
    private static final Map<String, String> STATUS_TO_BUBBLE = new LinkedHashMap<>();
    static {
        STATUS_TO_BUBBLE.put("not_started", "Not started");
        STATUS_TO_BUBBLE.put("planned", "Planned");
        STATUS_TO_BUBBLE.put("preparation_in_progress", "Preparation in progress");
        STATUS_TO_BUBBLE.put("ready_for_pickup", "Ready for pickup");
        STATUS_TO_BUBBLE.put("courier_assigned", "Courier assigned");
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
        dto.setCompanyId(readString(r, R_COMPANY));
        dto.setStoreId(readString(r, R_STORE));
        dto.setOrderNr(readString(r, R_ORDER_NR));
        dto.setCustomerName(readString(r, R_CUSTOMER_NAME));
        dto.setCustomerId(readString(r, R_CUSTOMER));
        dto.setType(readString(r, R_TYPE));
        dto.setAmount(readBigDecimal(r, R_AMOUNT));
        dto.setPaymentStatus(readString(r, R_PAYMENT));
        dto.setStatus(statusFromBubble(readString(r, R_STATUS)));
        dto.setAssignedTo(readString(r, R_ASSIGNED));
        dto.setReadyBy(readString(r, R_READY_BY));
        dto.setNotes(readString(r, R_NOTES));
        dto.setCreatedAt(readString(r, R_CREATED));
        dto.setUpdatedAt(readString(r, R_MODIFIED));
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
     * Bubble constraints JSON scoping to {@code companyId} plus the optional
     * UI filters. Always company-scoped; never returns cross-company rows.
     */
    public String buildConstraints(String companyId, String storeId, String assignedTo,
                                   String orderNr, String customer) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        constraints.add(constraint(F_COMPANY, "equals", companyId));
        if (hasText(storeId)) {
            constraints.add(constraint(F_STORE, "equals", storeId));
        }
        if (hasText(assignedTo)) {
            constraints.add(constraint(F_ASSIGNED_TO, "equals", assignedTo));
        }
        if (hasText(orderNr)) {
            constraints.add(constraint(F_ORDER_NR, "text contains", orderNr));
        }
        if (hasText(customer)) {
            constraints.add(constraint(F_CUSTOMER_NAME, "text contains", customer));
        }
        try {
            return objectMapper.writeValueAsString(constraints);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build Bubble constraints", e);
        }
    }

    /** Body for POST /obj/order — company-scoped; only non-null fields are sent. */
    public Map<String, Object> toCreateBody(OrderDto dto, String companyId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_COMPANY, companyId);
        putIfPresent(body, F_ORDER_NR, dto.getOrderNr());
        putIfPresent(body, F_CUSTOMER_NAME, dto.getCustomerName());
        putIfPresent(body, F_CUSTOMER, dto.getCustomerId());
        putIfPresent(body, F_STORE, dto.getStoreId());
        putIfPresent(body, F_TYPE, dto.getType());
        putIfPresent(body, F_AMOUNT, dto.getAmount());
        putIfPresent(body, F_PAYMENT_STATUS, dto.getPaymentStatus());
        putIfPresent(body, F_ASSIGNED_TO, dto.getAssignedTo());
        putIfPresent(body, F_READY_BY, dto.getReadyBy());
        putIfPresent(body, F_NOTES, dto.getNotes());
        String status = dto.getStatus() != null ? dto.getStatus() : "not_started";
        body.put(F_STATUS, statusToBubble(status));
        return body;
    }

    /**
     * Partial body for PATCH /obj/order/{id} — mirrors the old PUT semantics
     * (customer, store, type, amount, payment, assignee, readyBy, notes;
     * order number, status and company are not changed here).
     */
    public Map<String, Object> toUpdateBody(OrderDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_CUSTOMER_NAME, dto.getCustomerName());
        putIfPresent(body, F_STORE, dto.getStoreId());
        putIfPresent(body, F_TYPE, dto.getType());
        putIfPresent(body, F_AMOUNT, dto.getAmount());
        putIfPresent(body, F_PAYMENT_STATUS, dto.getPaymentStatus());
        putIfPresent(body, F_ASSIGNED_TO, dto.getAssignedTo());
        putIfPresent(body, F_READY_BY, dto.getReadyBy());
        putIfPresent(body, F_NOTES, dto.getNotes());
        return body;
    }

    /** Single-field body for the kanban status transition. */
    public Map<String, Object> statusUpdateBody(String kanbanStatus) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_STATUS, statusToBubble(kanbanStatus));
        return body;
    }

    /** The company a Bubble record belongs to (for ownership checks). */
    public String companyOf(Map<String, Object> record) {
        return readString(record, R_COMPANY);
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

    private static String readString(Map<String, Object> r, String... keys) {
        if (r == null) {
            return null;
        }
        for (String k : keys) {
            Object v = r.get(k);
            if (v != null) {
                String s = String.valueOf(v);
                if (!s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static BigDecimal readBigDecimal(Map<String, Object> r, String... keys) {
        String s = readString(r, keys);
        if (s == null) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String normalize(String s) {
        return s == null ? null : s.trim().toLowerCase().replaceAll("[\\s_]+", " ");
    }
}
