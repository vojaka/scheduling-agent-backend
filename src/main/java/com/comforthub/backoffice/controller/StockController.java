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
 * Bubble Data API</b> for reads and writes and never touches PostgreSQL.
 *
 * <p><b>Company scoping is indirect.</b> The Bubble {@code stock} type has no
 * merchant field — it links only to a {@code Store}. So every call first
 * resolves the caller's company's store ids (Bubble {@code store} where
 * {@code Company = companyId}) and constrains stock to {@code Store in [...]}.
 *
 * <p>The REST contract is unchanged (same routes/params and Spring {@code Page}
 * envelope); {@code storeId}/{@code inventoryId} are now Bubble text ids. The
 * {@code name} (inventory-name) filter is a cross-entity join and is accepted
 * but ignored (see {@link StockBubbleMapper}).
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
     * List stock entries for the company (scoped through its stores).
     * Optional {@code storeId} restricts to one of the company's stores;
     * {@code name} is accepted but not applied (cross-entity — see class doc).
     */
    @GetMapping
    public Page<StockDto> getStock(@RequestParam(required = false) String storeId,
                                   @RequestParam(required = false) String name,
                                   Pageable pageable) {
        Optional<String> companyOpt = currentUserService.currentCompanyId();
        if (companyOpt.isEmpty()) {
            return Page.empty(pageable);
        }
        String companyId = companyOpt.get();

        List<String> storeIds = companyStoreIds(companyId);
        if (storeId != null && !storeId.isBlank()) {
            // Restrict to the requested store only if it belongs to the company.
            storeIds = storeIds.contains(storeId) ? List.of(storeId) : List.of();
        }
        if (storeIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        String constraints = mapper.stockByStoresConstraints(storeIds);
        int limit = Math.min(pageable.getPageSize(), BUBBLE_MAX_LIMIT);
        int cursor = (int) pageable.getOffset();

        BubbleListResult result = bubbleClient.list(
                StockBubbleMapper.TYPE, constraints, cursor, limit,
                StockBubbleMapper.SORT_CREATED_DATE, true);

        List<StockDto> content = result.getResults().stream()
                .map(r -> {
                    StockDto dto = mapper.toDto(r);
                    dto.setCompanyId(companyId); // stock has no merchant field of its own
                    return dto;
                })
                .toList();

        long total = (long) cursor + result.getCount() + result.getRemaining();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Update the quantity for a store + inventory combination (upsert).
     * Body: {@code { "quantity": 42 }}. The store must belong to the caller's
     * company; {@code storeId}/{@code inventoryId} are Bubble text ids.
     */
    @PutMapping("/{storeId}/{inventoryId}")
    public ResponseEntity<StockDto> updateQuantity(@PathVariable String storeId,
                                                   @PathVariable String inventoryId,
                                                   @RequestBody StockDto body) {
        Optional<String> companyOpt = currentUserService.currentCompanyId();
        if (companyOpt.isEmpty()) {
            return ResponseEntity.status(403).build();
        }
        String companyId = companyOpt.get();

        // The store must be one of the company's stores.
        if (!companyStoreIds(companyId).contains(storeId)) {
            return ResponseEntity.notFound().build();
        }
        Integer quantity = body.getQuantity();

        // Locate an existing row for this store + inventory.
        BubbleListResult existing = bubbleClient.list(
                StockBubbleMapper.TYPE,
                mapper.findByStoreAndInventory(storeId, inventoryId), 0, 1);

        String id;
        if (!existing.getResults().isEmpty()) {
            id = mapper.toDto(existing.getResults().get(0)).getId();
            bubbleClient.update(StockBubbleMapper.TYPE, id, mapper.quantityUpdateBody(quantity));
        } else {
            id = bubbleClient.create(StockBubbleMapper.TYPE,
                    mapper.toCreateBody(storeId, inventoryId, quantity));
        }

        return ResponseEntity.ok(reload(id, companyId, storeId, inventoryId, quantity));
    }

    // ------------------------------------------------------------- helpers

    /** The Bubble store ids that belong to {@code companyId}. */
    private List<String> companyStoreIds(String companyId) {
        BubbleListResult stores = bubbleClient.list(
                StockBubbleMapper.STORE_TYPE,
                mapper.storeCompanyConstraints(companyId), 0, BUBBLE_MAX_LIMIT);
        return mapper.storeIdsOf(stores.getResults());
    }

    /**
     * Re-fetch the stock row so the response reflects persisted state; falls back
     * to a DTO assembled from the request if the read-back fails.
     */
    private StockDto reload(String id, String companyId, String storeId,
                            String inventoryId, Integer quantity) {
        if (id != null) {
            Map<String, Object> record = bubbleClient.get(StockBubbleMapper.TYPE, id);
            if (record != null) {
                StockDto dto = mapper.toDto(record);
                dto.setCompanyId(companyId);
                return dto;
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
