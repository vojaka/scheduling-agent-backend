package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.dto.BookingDto;
import com.comforthub.backoffice.mapper.BookingBubbleMapper;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Bookings CRUD — stored as Bubble {@code events}.
 *
 * <p>Phase 5: Bubble is the source of truth; this controller proxies the Bubble
 * Data API and never touches PostgreSQL.
 *
 * <p><b>The {@code events} type is thin — related data lives on other records,
 * resolved via batched second hops:</b>
 * <ul>
 *   <li><b>company scope</b> ← each event's {@code Service} (an Inventory), whose
 *       {@code Company} is confirmed: resolve the company's inventory ids and
 *       constrain events to {@code Service in [...]}. Bookings with no Service
 *       are not visible.</li>
 *   <li><b>store</b> ← each event's {@code Cart Item} → {@code Store (single)}</li>
 *   <li><b>customer name/email</b> ← each event's {@code Customer (individual)}
 *       (a User) → {@code FullName} / {@code email}</li>
 * </ul>
 * The store/customer hops are batched (one Data API call per related type per
 * page — no N+1) using an {@code _id in [...]} constraint; if that constraint or
 * a field key is off, those columns simply stay null (no crash). Create still
 * can't set the company scope (no service id on the DTO) — flagged.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    /** Bubble caps a single Data API page at 100 records. */
    private static final int BUBBLE_MAX_LIMIT = 100;

    private final BubbleClient bubbleClient;
    private final BookingBubbleMapper mapper;
    private final CurrentUserService currentUserService;

    public BookingController(BubbleClient bubbleClient,
                             BookingBubbleMapper mapper,
                             CurrentUserService currentUserService) {
        this.bubbleClient = bubbleClient;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    /**
     * List bookings for the company (scoped through the company's inventories via
     * each event's Service). Optional {@code workerId}, and an ISO-8601
     * {@code from}/{@code to} overlap window. Store and customer name/email are
     * enriched via batched second hops.
     */
    @GetMapping
    public Page<BookingDto> getBookings(
            @RequestParam(required = false) String workerId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Pageable pageable) {
        Optional<String> companyOpt = currentUserService.currentCompanyId();
        if (companyOpt.isEmpty()) {
            return Page.empty(pageable);
        }
        String companyId = companyOpt.get();

        List<String> inventoryIds = companyInventoryIds(companyId);
        if (inventoryIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        String constraints = mapper.buildConstraints(inventoryIds, workerId, from, to);
        int limit = Math.min(pageable.getPageSize(), BUBBLE_MAX_LIMIT);
        int cursor = (int) pageable.getOffset();

        BubbleListResult result = bubbleClient.list(
                BookingBubbleMapper.TYPE, constraints, cursor, limit,
                BookingBubbleMapper.SORT_CREATED_DATE, true);

        List<Map<String, Object>> events = result.getResults();
        Map<String, String> storeByCartItem = resolveStores(events);
        Map<String, Map<String, Object>> usersById = resolveCustomers(events);

        List<BookingDto> content = events.stream()
                .map(e -> {
                    BookingDto dto = mapper.toDto(e);
                    dto.setCompanyId(companyId); // events has no company field of its own
                    enrich(dto, e, storeByCartItem, usersById);
                    return dto;
                })
                .toList();

        long total = (long) cursor + result.getCount() + result.getRemaining();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Create a booking (title/time/worker only). NOTE: cannot set the company
     * scope (events has no company field and the DTO has no service id), so the
     * created event isn't attributable to a company until a Service is set — a
     * structural limitation flagged for a product decision.
     */
    @PostMapping
    public ResponseEntity<BookingDto> createBooking(@RequestBody BookingDto body) {
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    String newId = bubbleClient.create(
                            BookingBubbleMapper.TYPE, mapper.toCreateBody(body));
                    return ResponseEntity.ok(reload(newId, companyId, body));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<BookingDto> updateBooking(@PathVariable String id,
                                                    @RequestBody BookingDto body) {
        return currentUserService.currentCompanyId()
                .filter(companyId -> ownedByCompany(id, companyId))
                .map(companyId -> {
                    bubbleClient.update(BookingBubbleMapper.TYPE, id, mapper.toUpdateBody(body));
                    return ResponseEntity.ok(reload(id, companyId, body));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBooking(@PathVariable String id) {
        boolean owned = currentUserService.currentCompanyId()
                .filter(companyId -> ownedByCompany(id, companyId))
                .isPresent();
        if (!owned) {
            return ResponseEntity.notFound().build();
        }
        bubbleClient.delete(BookingBubbleMapper.TYPE, id);
        return ResponseEntity.ok().build();
    }

    // ------------------------------------------------------------- helpers

    /** The Bubble inventory ids that belong to {@code companyId}. */
    private List<String> companyInventoryIds(String companyId) {
        BubbleListResult inventories = bubbleClient.list(
                BookingBubbleMapper.INVENTORY_TYPE,
                mapper.inventoryCompanyConstraints(companyId), 0, BUBBLE_MAX_LIMIT);
        return mapper.idsOf(inventories.getResults());
    }

    /** True if the booking exists and its Service inventory belongs to the company. */
    private boolean ownedByCompany(String id, String companyId) {
        Map<String, Object> record = bubbleClient.get(BookingBubbleMapper.TYPE, id);
        if (record == null) {
            return false;
        }
        String service = mapper.serviceOf(record);
        return service != null && companyInventoryIds(companyId).contains(service);
    }

    /** Cart-item-id → store id, for the page's events (one batched Data API call). */
    private Map<String, String> resolveStores(List<Map<String, Object>> events) {
        Set<String> cartItemIds = new LinkedHashSet<>();
        for (Map<String, Object> e : events) {
            String ci = mapper.cartItemIdOf(e);
            if (ci != null) {
                cartItemIds.add(ci);
            }
        }
        Map<String, String> byCartItem = new HashMap<>();
        if (cartItemIds.isEmpty()) {
            return byCartItem;
        }
        BubbleListResult cartItems = bubbleClient.list(
                BookingBubbleMapper.CARTITEM_TYPE,
                mapper.idInConstraints(cartItemIds), 0, cartItemIds.size());
        for (Map<String, Object> ci : cartItems.getResults()) {
            String id = idOf(ci);
            if (id != null) {
                byCartItem.put(id, mapper.storeOfCartItem(ci));
            }
        }
        return byCartItem;
    }

    /** Customer-user-id → user record, for the page's events (one batched call). */
    private Map<String, Map<String, Object>> resolveCustomers(List<Map<String, Object>> events) {
        Set<String> customerIds = new LinkedHashSet<>();
        for (Map<String, Object> e : events) {
            String cu = mapper.customerIdOf(e);
            if (cu != null) {
                customerIds.add(cu);
            }
        }
        Map<String, Map<String, Object>> byId = new HashMap<>();
        if (customerIds.isEmpty()) {
            return byId;
        }
        BubbleListResult users = bubbleClient.list(
                BookingBubbleMapper.USER_TYPE,
                mapper.idInConstraints(customerIds), 0, customerIds.size());
        for (Map<String, Object> u : users.getResults()) {
            String id = idOf(u);
            if (id != null) {
                byId.put(id, u);
            }
        }
        return byId;
    }

    /** Fill store + customer name/email onto a DTO from the pre-resolved maps. */
    private void enrich(BookingDto dto, Map<String, Object> event,
                        Map<String, String> storeByCartItem,
                        Map<String, Map<String, Object>> usersById) {
        String ci = mapper.cartItemIdOf(event);
        if (ci != null) {
            dto.setStoreId(storeByCartItem.get(ci));
        }
        String cu = mapper.customerIdOf(event);
        if (cu != null) {
            Map<String, Object> user = usersById.get(cu);
            if (user != null) {
                dto.setCustomerName(mapper.nameOfUser(user));
                dto.setCustomerEmail(mapper.emailOfUser(user));
            }
        }
    }

    private static String idOf(Map<String, Object> record) {
        Object v = record.get("_id");
        return v == null ? null : String.valueOf(v);
    }

    /**
     * Re-fetch the booking so the response reflects persisted state (enriching a
     * single record via direct gets); falls back to the request body if the
     * read-back fails.
     */
    private BookingDto reload(String id, String companyId, BookingDto fallback) {
        if (id != null) {
            Map<String, Object> event = bubbleClient.get(BookingBubbleMapper.TYPE, id);
            if (event != null) {
                BookingDto dto = mapper.toDto(event);
                dto.setCompanyId(companyId);
                String ci = mapper.cartItemIdOf(event);
                if (ci != null) {
                    Map<String, Object> cartItem = bubbleClient.get(BookingBubbleMapper.CARTITEM_TYPE, ci);
                    if (cartItem != null) {
                        dto.setStoreId(mapper.storeOfCartItem(cartItem));
                    }
                }
                String cu = mapper.customerIdOf(event);
                if (cu != null) {
                    Map<String, Object> user = bubbleClient.get(BookingBubbleMapper.USER_TYPE, cu);
                    if (user != null) {
                        dto.setCustomerName(mapper.nameOfUser(user));
                        dto.setCustomerEmail(mapper.emailOfUser(user));
                    }
                }
                return dto;
            }
        }
        if (fallback != null) {
            fallback.setId(id);
            fallback.setBubbleId(id);
            fallback.setCompanyId(companyId);
        }
        return fallback;
    }
}
