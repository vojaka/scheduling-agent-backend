package com.comforthub.backoffice.service;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.dto.NotificationDto;
import com.comforthub.backoffice.mapper.OrderBubbleMapper;
import com.comforthub.backoffice.mapper.StockBubbleMapper;
import com.comforthub.backoffice.model.entity.BookingEntity;
import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import com.comforthub.backoffice.model.entity.BubbleStoreEntity;
import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.model.entity.BubbleWageRateEntity;
import com.comforthub.backoffice.model.entity.InventoryEntity;
import com.comforthub.backoffice.repository.BookingRepository;
import com.comforthub.backoffice.repository.BubbleShiftRepository;
import com.comforthub.backoffice.repository.BubbleStoreRepository;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.repository.BubbleWageRateRepository;
import com.comforthub.backoffice.repository.InventoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service to manage backoffice notifications/reminders. Maps onto the Bubble.io 'notifications' schema.
 * Merges raw Bubble database notifications (filtered by company context) with smart dynamic alerts.
 */
@Service
public class SmartNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SmartNotificationService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final BubbleClient bubbleClient;
    private final StockBubbleMapper stockBubbleMapper;
    private final OrderBubbleMapper orderBubbleMapper;
    private final BubbleUserRepository userRepository;
    private final BubbleShiftRepository shiftRepository;
    private final BubbleWageRateRepository wageRateRepository;
    private final BookingRepository bookingRepository;
    private final InventoryRepository inventoryRepository;
    private final BubbleStoreRepository storeRepository;
    private final ObjectMapper objectMapper;

    public SmartNotificationService(BubbleClient bubbleClient,
                                    StockBubbleMapper stockBubbleMapper,
                                    OrderBubbleMapper orderBubbleMapper,
                                    BubbleUserRepository userRepository,
                                    BubbleShiftRepository shiftRepository,
                                    BubbleWageRateRepository wageRateRepository,
                                    BookingRepository bookingRepository,
                                    InventoryRepository inventoryRepository,
                                    BubbleStoreRepository storeRepository,
                                    ObjectMapper objectMapper) {
        this.bubbleClient = bubbleClient;
        this.stockBubbleMapper = stockBubbleMapper;
        this.orderBubbleMapper = orderBubbleMapper;
        this.userRepository = userRepository;
        this.shiftRepository = shiftRepository;
        this.wageRateRepository = wageRateRepository;
        this.bookingRepository = bookingRepository;
        this.inventoryRepository = inventoryRepository;
        this.storeRepository = storeRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Compiles all notifications/reminders for the specified company, strictly following the Bubble notifications schema.
     */
    public List<NotificationDto> getNotificationsForCompany(String companyId) {
        if (companyId == null || companyId.isBlank()) {
            return Collections.emptyList();
        }

        List<NotificationDto> notifications = new ArrayList<>();

        // Load lookup tables for names and relationship checks
        Map<String, String> storeNames = loadStoreNames(companyId);
        Map<String, String> inventoryNames = loadInventoryNames(companyId);
        Map<String, String> workerNames = loadWorkerNames(companyId);
        Set<String> companyOrderIds = loadCompanyOrderIds(companyId);

        // A list of all unique IDs related to the company's context for filtering polymorphic thing references
        Set<String> companyRelatedIds = new HashSet<>();
        companyRelatedIds.addAll(storeNames.keySet());
        companyRelatedIds.addAll(inventoryNames.keySet());
        companyRelatedIds.addAll(workerNames.keySet());
        companyRelatedIds.addAll(companyOrderIds);

        // 1. Fetch raw Bubble table notifications and filter them by company context
        try {
            fetchBubbleNotifications(companyId, companyRelatedIds, notifications);
        } catch (Exception e) {
            log.error("Failed to fetch notifications from Bubble for company {}", companyId, e);
        }

        // 2. Stock Checks (Formatted using Bubble notifications schema)
        try {
            checkLowStock(companyId, storeNames, inventoryNames, notifications);
        } catch (Exception e) {
            log.error("Failed to compile low stock notifications for company {}", companyId, e);
        }

        // 3. Shift Checks (Formatted using Bubble notifications schema)
        try {
            checkUnassignedShifts(companyId, storeNames, notifications);
        } catch (Exception e) {
            log.error("Failed to compile unassigned shift notifications for company {}", companyId, e);
        }

        // 4. Worker Wage Rate Checks (Formatted using Bubble notifications schema)
        try {
            checkMissingWageRates(companyId, notifications);
        } catch (Exception e) {
            log.error("Failed to compile missing wage rate notifications for company {}", companyId, e);
        }

        // 5. Order Checks (Formatted using Bubble notifications schema)
        try {
            checkPendingOrders(companyId, storeNames, notifications);
        } catch (Exception e) {
            log.error("Failed to compile pending order notifications for company {}", companyId, e);
        }

        // 6. Booking (Events) Checks (Formatted using Bubble notifications schema)
        try {
            checkUnassignedBookings(companyId, notifications);
        } catch (Exception e) {
            log.error("Failed to compile unassigned booking notifications for company {}", companyId, e);
        }

        // Sort by created date descending
        notifications.sort((n1, n2) -> {
            String c1 = n1.getCreatedAt() != null ? n1.getCreatedAt() : "";
            String c2 = n2.getCreatedAt() != null ? n2.getCreatedAt() : "";
            return c2.compareTo(c1);
        });

        return notifications;
    }

    /**
     * Set a notification record's viewed/read status to true in Bubble.
     */
    public void markAsViewed(String bubbleId) {
        log.info("Marking Bubble notification {} as viewed...", bubbleId);
        Map<String, Object> body = new LinkedHashMap<>();
        // Send both casings to be fully resilient to whatever they named it in Bubble
        body.put("isViewed", true);
        body.put("IsViewed", true);
        bubbleClient.update("notifications", bubbleId, body);
    }

    private void fetchBubbleNotifications(String companyId, Set<String> companyRelatedIds, List<NotificationDto> list) {
        BubbleListResult result;
        try {
            // Plan A: Query Bubble filtering directly by Company ID and isViewed != true
            List<Map<String, Object>> constraints = new ArrayList<>();
            
            // We search by Company reference or text matching companyId
            Map<String, Object> cCompany = new LinkedHashMap<>();
            cCompany.put("key", "Company");
            cCompany.put("constraint_type", "equals");
            cCompany.put("value", companyId);
            constraints.add(cCompany);

            // Filter out already viewed/read notifications
            Map<String, Object> cViewed = new LinkedHashMap<>();
            cViewed.put("key", "isViewed");
            cViewed.put("constraint_type", "not equals");
            cViewed.put("value", true);
            constraints.add(cViewed);

            String constraintsJson = objectMapper.writeValueAsString(constraints);
            result = bubbleClient.list("notifications", constraintsJson, 0, 100, "Created Date", true);
        } catch (Exception e) {
            // Plan B: Resilient Fallback if fields are not yet exposed/indexed in Bubble
            log.warn("Resilient fallback: Bubble Company/isViewed queries failed (likely schema changes not fully exposed in Bubble Data API settings). Fetching raw and filtering in-memory: {}", e.getMessage());
            result = bubbleClient.list("notifications", "[]", 0, 100, "Created Date", true);
        }

        for (Map<String, Object> r : result.getResults()) {
            NotificationDto dto = mapBubbleNotificationToDto(r);
            
            // Check if already viewed
            if (Boolean.TRUE.equals(dto.getIsViewed())) {
                continue;
            }

            // Scoping check: if a notification references a thing (like an order, store, or worker) that
            // belongs to this company, or belongs to the company directly, we keep it.
            boolean belongsToCompany = companyId.equals(dto.getCompany()) 
                    || dto.getThing() == null 
                    || companyRelatedIds.contains(dto.getThing());

            if (belongsToCompany) {
                list.add(dto);
            }
        }
    }

    private void checkLowStock(String companyId, Map<String, String> storeNames, Map<String, String> inventoryNames, List<NotificationDto> list) {
        List<String> storeIds = new ArrayList<>(storeNames.keySet());
        if (storeIds.isEmpty()) return;

        String constraints = stockBubbleMapper.stockByStoresConstraints(storeIds);
        BubbleListResult stockResult = bubbleClient.list(StockBubbleMapper.TYPE, constraints, 0, 100);

        for (Map<String, Object> r : stockResult.getResults()) {
            String id = (String) r.get("_id");
            String storeId = (String) r.get("Store");
            String inventoryId = (String) r.get("Inventory");
            Number qtyNum = (Number) r.get("Qnty in stock");
            int qty = qtyNum != null ? qtyNum.intValue() : 0;

            if (qty < 5) {
                String storeName = storeNames.getOrDefault(storeId, "Unknown Store");
                String inventoryName = inventoryNames.getOrDefault(inventoryId, "Unknown Item");

                String title = qty == 0 ? "Out of Stock" : "Low Stock Alert";
                String message = String.format("Stock level for '%s' at %s is down to %d units.", inventoryName, storeName, qty);

                // Map to Bubble Notifications schema
                list.add(NotificationDto.builder()
                        .id("stock-low-" + id)
                        .title(title)
                        .internalMessage(message)
                        .externalMessage(message)
                        .thing(inventoryId)
                        .thingType("inventory")
                        .workflowCode("STOCK_LOW")
                        .duration(0.0)
                        .errorCode(qty == 0 ? 404 : 100)
                        .triggerVerifications(false)
                        .isViewed(false)
                        .company(companyId)
                        .createdAt(OffsetDateTime.now().toString())
                        .modifiedAt(OffsetDateTime.now().toString())
                        .build());
            }
        }
    }

    private void checkUnassignedShifts(String companyId, Map<String, String> storeNames, List<NotificationDto> list) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime end = now.plusDays(7);

        List<BubbleShiftEntity> shifts = shiftRepository.findUnassignedShifts(companyId, now, end);
        for (BubbleShiftEntity shift : shifts) {
            String storeName = storeNames.getOrDefault(shift.getAssignedStore(), "Unknown Store");
            String dateStr = shift.getStartTime().format(DATE_FORMATTER);
            String timeStr = shift.getStartTime().format(TIME_FORMATTER) + " - " + shift.getEndTime().format(TIME_FORMATTER);

            String title = "Unassigned Shift";
            String message = String.format("A shift (%s, %s) at %s is unassigned.", dateStr, timeStr, storeName);

            // Map to Bubble Notifications schema
            list.add(NotificationDto.builder()
                        .id("shift-unassigned-" + shift.getId())
                        .title(title)
                        .internalMessage(message)
                        .externalMessage(message)
                        .thing(shift.getId().toString())
                        .thingType("shift")
                        .workflowCode("SHIFT_UNASSIGNED")
                        .duration(0.0)
                        .errorCode(300)
                        .triggerVerifications(false)
                        .isViewed(false)
                        .company(companyId)
                        .createdAt(shift.getStartTime().toString())
                        .modifiedAt(OffsetDateTime.now().toString())
                        .build());
        }
    }

    private void checkMissingWageRates(String companyId, List<NotificationDto> list) {
        List<BubbleUserEntity> workers = userRepository.findByCompanyId(companyId).stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .filter(u -> "Worker".equalsIgnoreCase(u.getRole()))
                .toList();

        if (workers.isEmpty()) return;

        List<BubbleWageRateEntity> rates = wageRateRepository.findByCompany(companyId);
        Set<String> usersWithRates = rates.stream()
                .map(BubbleWageRateEntity::getUserId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        for (BubbleUserEntity worker : workers) {
            if (worker.getBubbleId() != null && !usersWithRates.contains(worker.getBubbleId())) {
                String title = "Missing Wage Rate";
                String message = String.format("Worker '%s' does not have a wage rate configured.", worker.getFullName());

                // Map to Bubble Notifications schema
                list.add(NotificationDto.builder()
                        .id("worker-rate-missing-" + worker.getId())
                        .title(title)
                        .internalMessage(message)
                        .externalMessage(message)
                        .thing(worker.getBubbleId())
                        .thingType("user")
                        .workflowCode("MISSING_WAGE_RATE")
                        .duration(0.0)
                        .errorCode(200)
                        .triggerVerifications(false)
                        .isViewed(false)
                        .company(companyId)
                        .createdAt(OffsetDateTime.now().toString())
                        .modifiedAt(OffsetDateTime.now().toString())
                        .build());
            }
        }
    }

    private void checkPendingOrders(String companyId, Map<String, String> storeNames, List<NotificationDto> list) {
        String constraints = orderBubbleMapper.buildConstraints(companyId, null, null, null, null);
        BubbleListResult ordersResult = bubbleClient.list(OrderBubbleMapper.TYPE, constraints, 0, 50, "Created Date", true);

        for (Map<String, Object> r : ordersResult.getResults()) {
            String id = (String) r.get("_id");
            String status = orderBubbleMapper.statusFromBubble((String) r.get("S - Order Progress Status"));
            String orderNr = (String) r.get("order_nr_text");
            if (orderNr == null || orderNr.isBlank()) {
                orderNr = "#" + (id.length() > 6 ? id.substring(id.length() - 6) : id);
            }

            if ("not_started".equals(status) || "planned".equals(status)) {
                String storeId = (String) r.get("Store");
                String storeName = storeNames.getOrDefault(storeId, "Main Store");
                String title = "Pending Order Action";
                String message = String.format("New order %s needs preparation at %s.", orderNr, storeName);

                // Map to Bubble Notifications schema
                list.add(NotificationDto.builder()
                        .id("order-pending-" + id)
                        .title(title)
                        .internalMessage(message)
                        .externalMessage(message)
                        .thing(id)
                        .thingType("order")
                        .workflowCode("PENDING_ORDER")
                        .duration(0.0)
                        .errorCode(100)
                        .triggerVerifications(false)
                        .isViewed(false)
                        .company(companyId)
                        .createdAt(OffsetDateTime.now().toString())
                        .modifiedAt(OffsetDateTime.now().toString())
                        .build());
            }
        }
    }

    private void checkUnassignedBookings(String companyId, List<NotificationDto> list) {
        OffsetDateTime now = OffsetDateTime.now();
        List<BookingEntity> bookings = bookingRepository.findUnassignedBookings(companyId, now);

        for (BookingEntity booking : bookings) {
            String dateStr = booking.getStartTime().format(DATE_FORMATTER);
            String timeStr = booking.getStartTime().format(TIME_FORMATTER);
            String title = "Unassigned Booking";
            String message = String.format("Booking '%s' (%s at %s) has no assigned worker.", booking.getTitle(), dateStr, timeStr);

            // Map to Bubble Notifications schema
            list.add(NotificationDto.builder()
                    .id("booking-unassigned-" + booking.getId())
                    .title(title)
                    .internalMessage(message)
                    .externalMessage(message)
                    .thing(booking.getBubbleId() != null ? booking.getBubbleId() : booking.getId().toString())
                    .thingType("booking")
                    .workflowCode("BOOKING_UNASSIGNED")
                    .duration(0.0)
                    .errorCode(500)
                    .triggerVerifications(false)
                    .isViewed(false)
                    .company(companyId)
                    .createdAt(booking.getStartTime().toString())
                    .modifiedAt(OffsetDateTime.now().toString())
                    .build());
        }
    }

    // Helper to map raw Bubble data to the NotificationDto
    private NotificationDto mapBubbleNotificationToDto(Map<String, Object> r) {
        String thing = readString(r, "Thing");
        
        // Handle both casings for views/company link fields
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
                .thingType(deriveThingType(thing))
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

    private String deriveThingType(String thingId) {
        if (thingId == null || thingId.isBlank()) return null;
        return null;
    }

    // Helper lookups
    private Map<String, String> loadStoreNames(String companyId) {
        try {
            return storeRepository.findByCompany(companyId).stream()
                    .collect(Collectors.toMap(BubbleStoreEntity::getBubbleId, BubbleStoreEntity::getName, (a, b) -> a));
        } catch (Exception e) {
            log.error("Failed to load store names for company {}", companyId, e);
            return new HashMap<>();
        }
    }

    private Map<String, String> loadInventoryNames(String companyId) {
        try {
            return inventoryRepository.findAll().stream()
                    .filter(i -> companyId.equals(i.getCompanyId()))
                    .filter(i -> i.getBubbleId() != null)
                    .collect(Collectors.toMap(InventoryEntity::getBubbleId, InventoryEntity::getName, (a, b) -> a));
        } catch (Exception e) {
            log.error("Failed to load inventory names for company {}", companyId, e);
            return new HashMap<>();
        }
    }

    private Map<String, String> loadWorkerNames(String companyId) {
        try {
            return userRepository.findByCompanyId(companyId).stream()
                    .filter(u -> u.getBubbleId() != null)
                    .collect(Collectors.toMap(BubbleUserEntity::getBubbleId, BubbleUserEntity::getFullName, (a, b) -> a));
        } catch (Exception e) {
            log.error("Failed to load worker names for company {}", companyId, e);
            return new HashMap<>();
        }
    }

    private Set<String> loadCompanyOrderIds(String companyId) {
        try {
            String constraints = orderBubbleMapper.buildConstraints(companyId, null, null, null, null);
            BubbleListResult ordersResult = bubbleClient.list(OrderBubbleMapper.TYPE, constraints, 0, 100);
            return ordersResult.getResults().stream()
                    .map(o -> (String) o.get("_id"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Failed to load company order ids", e);
            return new HashSet<>();
        }
    }

    // Primitive reader methods matching Stock/Order Bubble Mappers
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
