package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.dto.NotificationDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller exposing customer-specific notifications and reminders.
 */
@RestController
@RequestMapping("/api/consumer/notifications")
public class ConsumerNotificationController {

    private static final Logger log = LoggerFactory.getLogger(ConsumerNotificationController.class);

    private final BubbleClient bubbleClient;
    private final ConsumerUserService consumerUserService;
    private final ObjectMapper objectMapper;

    public ConsumerNotificationController(BubbleClient bubbleClient,
                                          ConsumerUserService consumerUserService,
                                          ObjectMapper objectMapper) {
        this.bubbleClient = bubbleClient;
        this.consumerUserService = consumerUserService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get unviewed notifications for the logged-in customer.
     */
    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications() {
        Optional<String> userIdOpt = consumerUserService.currentBubbleUserId();
        if (userIdOpt.isEmpty()) {
            log.warn("Unauthenticated consumer notification request.");
            return ResponseEntity.ok(Collections.emptyList());
        }
        String userId = userIdOpt.get();
        log.info("Fetching notifications for consumer {}", userId);

        try {
            // Fetch unviewed notifications from Bubble
            List<Map<String, Object>> constraints = new ArrayList<>();

            Map<String, Object> cViewed = new LinkedHashMap<>();
            cViewed.put("key", "isViewed");
            cViewed.put("constraint_type", "not equals");
            cViewed.put("value", true);
            constraints.add(cViewed);

            String constraintsJson = objectMapper.writeValueAsString(constraints);
            BubbleListResult result = bubbleClient.list("notifications", constraintsJson, 0, 100, "Created Date", true);

            List<NotificationDto> notifications = new ArrayList<>();
            for (Map<String, Object> r : result.getResults()) {
                NotificationDto dto = mapBubbleNotificationToDto(r);
                if (Boolean.TRUE.equals(dto.getIsViewed())) {
                    continue;
                }

                // Filter: Only keep if created by this user or visible for Client role
                String createdBy = readString(r, "Created By");
                boolean visibleForClient = dto.getVisibleFor() != null && dto.getVisibleFor().contains("Client");
                boolean isOwnNotification = userId.equals(createdBy) || userId.equals(dto.getThing());

                if (isOwnNotification || visibleForClient) {
                    notifications.add(dto);
                }
            }

            return ResponseEntity.ok(notifications);

        } catch (Exception e) {
            log.error("Failed to fetch notifications from Bubble for user {}", userId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Mark a notification as viewed/dismissed.
     */
    @PostMapping("/{id}/view")
    public ResponseEntity<Void> markAsViewed(@PathVariable String id) {
        // If it's a dynamic reminder (synthetic client-side ID prefix), do nothing on server
        if (id.startsWith("cart-expiry-") || id.startsWith("address-missing-")
                || id.startsWith("promo-offer-") || id.startsWith("order-status-")) {
            return ResponseEntity.ok().build();
        }

        try {
            log.info("Marking Bubble notification {} as viewed by consumer...", id);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("isViewed", true);
            body.put("IsViewed", true);
            bubbleClient.update("notifications", id, body);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to mark notification {} as viewed", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    private NotificationDto mapBubbleNotificationToDto(Map<String, Object> r) {
        String thing = readString(r, "Thing");
        Boolean viewed = readBoolean(r, "isViewed");
        if (viewed == null) {
            viewed = readBoolean(r, "IsViewed");
        }
        String companyVal = readString(r, "Company");
        if (companyVal == null) {
            companyVal = readString(r, "company");
        }

        return NotificationDto.builder()
                .id(readString(r, "_id"))
                .duration(readDouble(r, "Duration"))
                .errorCode(readInteger(r, "Error Code"))
                .externalMessage(readString(r, "External Message"))
                .internalMessage(readString(r, "Internal Message"))
                .subThings(readString(r, "SubThings"))
                .thing(thing)
                .thingType(thing != null ? "order" : null) // Default fallback or derive as needed
                .title(readString(r, "Title"))
                .triggerVerifications(readBoolean(r, "Trigger Verifications"))
                .visibleFor(readStringList(r, "Visible for"))
                .workflowCode(readString(r, "Workflow Code"))
                .isViewed(viewed != null && viewed)
                .company(companyVal)
                .createdAt(readInstant(r, "Created Date"))
                .modifiedAt(readInstant(r, "Modified Date"))
                .build();
    }

    private static String readString(Map<String, Object> r, String key) {
        if (r == null) return null;
        Object v = r.get(key);
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private static Double readDouble(Map<String, Object> r, String key) {
        if (r == null) return null;
        Object v = r.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return null;
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer readInteger(Map<String, Object> r, String key) {
        if (r == null) return null;
        Object v = r.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v == null) return null;
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Boolean readBoolean(Map<String, Object> r, String key) {
        if (r == null) return null;
        Object v = r.get(key);
        if (v instanceof Boolean b) return b;
        if (v == null) return null;
        return Boolean.parseBoolean(String.valueOf(v).trim());
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(Map<String, Object> r, String key) {
        if (r == null) return null;
        Object v = r.get(key);
        if (v instanceof List) return (List<String>) v;
        return null;
    }

    private static String readInstant(Map<String, Object> r, String key) {
        if (r == null) return null;
        Object v = r.get(key);
        if (v == null) return null;
        if (v instanceof Number n) {
            return Instant.ofEpochMilli(n.longValue()).toString();
        }
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }
}
