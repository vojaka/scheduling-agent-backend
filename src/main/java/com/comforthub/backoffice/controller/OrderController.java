package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.dto.OrderDto;
import com.comforthub.backoffice.mapper.OrderBubbleMapper;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Orders CRUD + kanban status transition.
 *
 * <p>Phase 5: Bubble is the source of truth. This controller <b>proxies the
 * Bubble Data API</b> for both reads and writes and never touches PostgreSQL
 * (which is analytics-only, fed by the hourly ETL). Every call is scoped to the
 * caller's company via a Bubble {@code constraints} filter.
 *
 * <p>The REST contract is unchanged from the former JPA implementation
 * (same routes, params and JSON shape, including the Spring Data {@code Page}
 * envelope), so the React UI needs no changes. The Bubble field-alias mapping
 * lives entirely in {@link OrderBubbleMapper}.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Set<String> VALID_STATUSES = Set.of(
            "not_started", "planned", "preparation_in_progress",
            "ready_for_pickup", "courier_assigned", "completed"
    );

    /** Bubble caps a single Data API page at 100 records. */
    private static final int BUBBLE_MAX_LIMIT = 100;

    private final BubbleClient bubbleClient;
    private final OrderBubbleMapper mapper;
    private final CurrentUserService currentUserService;

    public OrderController(BubbleClient bubbleClient,
                           OrderBubbleMapper mapper,
                           CurrentUserService currentUserService) {
        this.bubbleClient = bubbleClient;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    /**
     * List orders for the company.
     * Optional params: storeId, assignedTo (worker id), orderNr (substring),
     * customer (name substring). Bubble cursor pagination is mapped onto the
     * Spring Data {@link Page} envelope the UI expects.
     */
    @GetMapping
    public Page<OrderDto> getOrders(@RequestParam(required = false) String storeId,
                                    @RequestParam(required = false) String assignedTo,
                                    @RequestParam(required = false) String orderNr,
                                    @RequestParam(required = false) String customer,
                                    Pageable pageable) {
        Optional<String> companyOpt = currentUserService.currentCompanyId();
        if (companyOpt.isEmpty()) {
            return Page.empty(pageable);
        }

        String constraints = mapper.buildConstraints(
                companyOpt.get(), storeId, assignedTo, orderNr, customer);

        int limit = Math.min(pageable.getPageSize(), BUBBLE_MAX_LIMIT);
        int cursor = (int) pageable.getOffset();

        BubbleListResult result = bubbleClient.list(
                OrderBubbleMapper.TYPE, constraints, cursor, limit,
                OrderBubbleMapper.SORT_CREATED_DATE, true);

        List<OrderDto> content = result.getResults().stream()
                .map(mapper::toDto)
                .toList();

        // Bubble reports the count in this page and how many remain after it;
        // total = items before this page + this page + items remaining.
        long total = (long) cursor + result.getCount() + result.getRemaining();
        return new PageImpl<>(content, pageable, total);
    }

    @PostMapping
    public ResponseEntity<OrderDto> createOrder(@RequestBody OrderDto body) {
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    // Auto-generate orderNr if not supplied (parity with old behaviour).
                    if (body.getOrderNr() == null || body.getOrderNr().isBlank()) {
                        body.setOrderNr("#" + String.format("%06d",
                                System.currentTimeMillis() % 1_000_000));
                    }
                    Map<String, Object> createBody = mapper.toCreateBody(body, companyId);
                    String newId = bubbleClient.create(OrderBubbleMapper.TYPE, createBody);
                    return ResponseEntity.ok(reload(newId, body));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderDto> updateOrder(@PathVariable String id,
                                                @RequestBody OrderDto body) {
        return currentUserService.currentCompanyId()
                .filter(companyId -> ownedByCompany(id, companyId))
                .map(companyId -> {
                    bubbleClient.update(OrderBubbleMapper.TYPE, id, mapper.toUpdateBody(body));
                    return ResponseEntity.ok(reload(id, body));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Move an order to a new kanban column.
     * Body: {@code { "status": "planned" }}
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDto> updateStatus(@PathVariable String id,
                                                 @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        if (newStatus == null || !VALID_STATUSES.contains(newStatus)) {
            return ResponseEntity.badRequest().build();
        }
        return currentUserService.currentCompanyId()
                .filter(companyId -> ownedByCompany(id, companyId))
                .map(companyId -> {
                    bubbleClient.update(OrderBubbleMapper.TYPE, id,
                            mapper.statusUpdateBody(newStatus));
                    return ResponseEntity.ok(reload(id, null));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------------- helpers

    /** True if the Bubble order {@code id} exists and belongs to {@code companyId}. */
    private boolean ownedByCompany(String id, String companyId) {
        Map<String, Object> record = bubbleClient.get(OrderBubbleMapper.TYPE, id);
        return record != null && companyId.equals(mapper.companyOf(record));
    }

    /**
     * Re-fetch the order from Bubble so the response reflects persisted state.
     * Falls back to the request body (with the id set) if the read-back fails —
     * e.g. when privacy rules hide the freshly written record from this token.
     */
    private OrderDto reload(String id, OrderDto fallback) {
        if (id != null) {
            Map<String, Object> record = bubbleClient.get(OrderBubbleMapper.TYPE, id);
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
