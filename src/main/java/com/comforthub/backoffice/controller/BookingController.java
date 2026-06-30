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
 * Bookings CRUD — calendar events tied to workers/stores.
 *
 * <p>Phase 5: Bubble is the source of truth. This controller <b>proxies the
 * Bubble Data API</b> for both reads and writes and never touches PostgreSQL
 * (which is analytics-only, fed by the hourly ETL). Every call is scoped to the
 * caller's company via a Bubble {@code constraints} filter.
 *
 * <p>The REST contract is unchanged from the former JPA implementation (same
 * routes, params and JSON shape, including the Spring Data {@code Page} envelope
 * and the ISO-8601 {@code from}/{@code to} window params), so the React UI needs
 * no changes. The Bubble field-alias mapping lives entirely in
 * {@link BookingBubbleMapper} — see that class for the (inferred) object type
 * and field keys that must be verified.
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
     * List bookings for the company.
     * Optional params: workerId, from (ISO-8601), to (ISO-8601).
     * Returns bookings whose window overlaps [from, to]. Bubble cursor
     * pagination is mapped onto the Spring Data {@link Page} envelope the UI
     * expects.
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

        String constraints = mapper.buildConstraints(companyOpt.get(), workerId, from, to);

        int limit = Math.min(pageable.getPageSize(), BUBBLE_MAX_LIMIT);
        int cursor = (int) pageable.getOffset();

        BubbleListResult result = bubbleClient.list(
                BookingBubbleMapper.TYPE, constraints, cursor, limit,
                BookingBubbleMapper.SORT_CREATED_DATE, true);

        List<BookingDto> content = result.getResults().stream()
                .map(mapper::toDto)
                .toList();

        // Bubble reports the count in this page and how many remain after it;
        // total = items before this page + this page + items remaining.
        long total = (long) cursor + result.getCount() + result.getRemaining();
        return new PageImpl<>(content, pageable, total);
    }

    @PostMapping
    public ResponseEntity<BookingDto> createBooking(@RequestBody BookingDto body) {
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    Map<String, Object> createBody = mapper.toCreateBody(body, companyId);
                    String newId = bubbleClient.create(BookingBubbleMapper.TYPE, createBody);
                    return ResponseEntity.ok(reload(newId, body));
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
                    return ResponseEntity.ok(reload(id, body));
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

    /** True if the Bubble booking {@code id} exists and belongs to {@code companyId}. */
    private boolean ownedByCompany(String id, String companyId) {
        Map<String, Object> record = bubbleClient.get(BookingBubbleMapper.TYPE, id);
        return record != null && companyId.equals(mapper.companyOf(record));
    }

    /**
     * Re-fetch the booking from Bubble so the response reflects persisted state.
     * Falls back to the request body (with the id set) if the read-back fails —
     * e.g. when privacy rules hide the freshly written record from this token.
     */
    private BookingDto reload(String id, BookingDto fallback) {
        if (id != null) {
            Map<String, Object> record = bubbleClient.get(BookingBubbleMapper.TYPE, id);
            if (record != null) {
                return mapper.toDto(record);
            }
        }
        if (fallback != null) {
            fallback.setId(id);
            fallback.setBubbleId(id);
        }
        return fallback;
    }
}
