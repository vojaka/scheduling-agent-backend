package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.dto.StockDto;
import com.comforthub.backoffice.mapper.StockBubbleMapper;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stock — per-store inventory quantity levels.
 *
 * <p>Phase 5: Bubble is the source of truth. This controller <b>proxies the
 * Bubble Data API</b> for both reads and writes and never touches PostgreSQL
 * (which is analytics-only, fed by the hourly ETL). Every call is scoped to the
 * caller's company via a Bubble {@code constraints} filter.
 *
 * <p>The REST contract is unchanged from the former JPA implementation (same
 * routes, params and JSON shape, including the Spring Data {@code Page}
 * envelope), so the React UI needs no changes. The only wire-level difference
 * is that the {@code storeId}/{@code inventoryId} path/query values are now
 * Bubble text ids (strings) rather than UUIDs. The Bubble field-alias mapping
 * lives entirely in {@link StockBubbleMapper}.
 *
 * <p><b>Unsupported {@code name} filter:</b> {@code name} filters by the
 * <i>linked inventory's</i> name — a cross-entity join a single Bubble
 * constraint on the {@code stock} type cannot express. As in
 * {@code OrderController}, the param is accepted but ignored (see
 * {@link StockBubbleMapper#buildConstraints}); a follow-up should resolve
 * matching inventory ids by name first, then constrain on them.
 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    /** Bubble caps a single Data API page at 100 records. */
    private static final int BUBBLE_MAX_LIMIT = 100;

    private final BubbleClient bubbleClient;
    private final StockBubbleMapper mapper;
    private final CurrentUserService currentUserService;

    public StockController(BubbleClient bubbleClient,
                           StockBubbleMapper mapper,
                           CurrentUserService currentUserService) {
        this.bubbleClient = bubbleClient;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    /**
     * List stock entries for the company.
     * Optional {@code storeId} and {@code name} (inventory name substring) params.
     * Bubble cursor pagination is mapped onto the Spring Data {@link Page}
     * envelope the UI expects.
     *
     * <p>Note: {@code name} is accepted but NOT applied — it filters by the
     * linked inventory's name, which a single stock-type constraint cannot
     * express (see class doc).
     */
    @GetMapping
    public Page<StockDto> getStock(@RequestParam(required = false) String storeId,
                                   @RequestParam(required = false) String name,
                                   Pageable pageable) {
        Optional<String> companyOpt = currentUserService.currentCompanyId();
        if (companyOpt.isEmpty()) {
            return Page.empty(pageable);
        }

        // `name` is intentionally passed through but ignored by the mapper.
        String constraints = mapper.buildConstraints(companyOpt.get(), storeId, name);

        int limit = Math.min(pageable.getPageSize(), BUBBLE_MAX_LIMIT);
        int cursor = (int) pageable.getOffset();

        BubbleListResult result = bubbleClient.list(
                StockBubbleMapper.TYPE, constraints, cursor, limit,
                StockBubbleMapper.SORT_CREATED_DATE, true);

        List<StockDto> content = result.getResults().stream()
                .map(mapper::toDto)
                .toList();

        // Bubble reports the count in this page and how many remain after it;
        // total = items before this page + this page + items remaining.
        long total = (long) cursor + result.getCount() + result.getRemaining();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Update the quantity for a specific store + inventory combination.
     * Creates the Bubble {@code stock} row if it doesn't exist yet (upsert).
     * Body: {@code { "quantity": 42 }}.
     *
     * <p>{@code storeId}/{@code inventoryId} are Bubble text ids.
     */
    @PutMapping("/{storeId}/{inventoryId}")
    public ResponseEntity<StockDto> updateQuantity(@PathVariable String storeId,
                                                   @PathVariable String inventoryId,
                                                   @RequestBody StockDto body) {
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    Integer quantity = body.getQuantity();

                    // Locate an existing row for this company + store + inventory.
                    String constraints = mapper.findByStoreAndInventory(
                            companyId, storeId, inventoryId);
                    BubbleListResult existing = bubbleClient.list(
                            StockBubbleMapper.TYPE, constraints, 0, 1);

                    String id;
                    if (!existing.getResults().isEmpty()) {
                        // Update the found row's quantity.
                        id = mapper.toDto(existing.getResults().get(0)).getId();
                        bubbleClient.update(StockBubbleMapper.TYPE, id,
                                mapper.quantityUpdateBody(quantity));
                    } else {
                        // No row yet — create one, company-scoped.
                        id = bubbleClient.create(StockBubbleMapper.TYPE,
                                mapper.toCreateBody(companyId, storeId, inventoryId, quantity));
                    }

                    return ResponseEntity.ok(reload(id, companyId, storeId, inventoryId, quantity));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    // ------------------------------------------------------------- helpers

    /**
     * Re-fetch the stock row from Bubble so the response reflects persisted
     * state. Falls back to a DTO assembled from the request when the read-back
     * fails — e.g. when privacy rules hide the freshly written record from this
     * token.
     */
    private StockDto reload(String id, String companyId, String storeId,
                            String inventoryId, Integer quantity) {
        if (id != null) {
            Map<String, Object> record = bubbleClient.get(StockBubbleMapper.TYPE, id);
            if (record != null) {
                return mapper.toDto(record);
            }
        }
        StockDto fallback = new StockDto();
        fallback.setId(id);
        fallback.setCompanyId(companyId);
        fallback.setStoreId(storeId);
        fallback.setInventoryId(inventoryId);
        fallback.setQuantity(quantity != null ? quantity : 0);
        return fallback;
    }
}
