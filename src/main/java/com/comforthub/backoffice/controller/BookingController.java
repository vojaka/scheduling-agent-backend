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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bookings CRUD — stored as Bubble {@code events}.
 *
 * <p>Phase 5: Bubble is the source of truth; this controller proxies the Bubble
 * Data API and never touches PostgreSQL.
 *
 * <p><b>Company scoping is indirect.</b> The {@code events} type has no company
 * field; each event links to a {@code Service} (an Inventory), and inventory
 * carries the confirmed {@code Company} field. So every call resolves the
 * caller's company's inventory ids and constrains events to {@code Service in
 * [...]}. Bookings with no Service are not visible. See {@link BookingBubbleMapper}
 * for the structural mismatches (no store / customer-name / customer-email
 * fields, and create can't set the scope) that still need a product decision.
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
     * {@code from}/{@code to} overlap window.
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

        List<BookingDto> content = result.getResults().stream()
                .map(r -> {
                    BookingDto dto = mapper.toDto(r);
                    dto.setCompanyId(companyId); // events has no company field of its own
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

    /**
     * True if the booking exists and its Service inventory belongs to the company.
     */
    private boolean ownedByCompany(String id, String companyId) {
        Map<String, Object> record = bubbleClient.get(BookingBubbleMapper.TYPE, id);
        if (record == null) {
            return false;
        }
        String service = mapper.serviceOf(record);
        return service != null && companyInventoryIds(companyId).contains(service);
    }

    /**
     * Re-fetch the booking so the response reflects persisted state; falls back to
     * the request body (with id + company set) if the read-back fails.
     */
    private BookingDto reload(String id, String companyId, BookingDto fallback) {
        if (id != null) {
            Map<String, Object> record = bubbleClient.get(BookingBubbleMapper.TYPE, id);
            if (record != null) {
                BookingDto dto = mapper.toDto(record);
                dto.setCompanyId(companyId);
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
