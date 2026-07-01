package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.dto.StoreDto;
import com.comforthub.backoffice.mapper.StoreBubbleMapper;
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
 * Stores CRUD — a company's physical/logical locations. Soft-delete via the
 * Bubble {@code isdeleted_boolean} flag.
 *
 * <p>Phase 5: Bubble is the source of truth. This controller <b>proxies the
 * Bubble Data API</b> for both reads and writes and never touches PostgreSQL
 * (which is analytics-only, fed by the hourly ETL). Every call is scoped to the
 * caller's company via a Bubble {@code constraints} filter, and writes re-check
 * ownership via {@code get()}. Structure mirrors {@link CategoryController} /
 * {@link InventoryController} (Bubble proxy + {@link CurrentUserService}
 * scoping + soft-delete); Bubble field-alias mapping lives in
 * {@link StoreBubbleMapper}.
 *
 * <p><b>Replaces the former {@code DataController.getStores}</b> Postgres-mirror
 * read at this same {@code GET /api/stores} path. Rationale for replacing rather
 * than keeping the Postgres read for the list view:
 * <ul>
 *   <li><b>Read-after-write correctness</b> — writes hit Bubble immediately, but
 *       the Postgres mirror only refreshes on the hourly ETL, so a kept-Postgres
 *       list would hide a just-created/edited store for up to an hour. Proxying
 *       the read keeps the list consistent with writes, exactly as Phase 5 did
 *       for categories/inventory/offerings.</li>
 *   <li><b>No mapping conflict</b> — two handlers cannot both map
 *       {@code GET /api/stores}; the read must live in one place.</li>
 * </ul>
 * The response keeps the Spring Data {@link Page} envelope the old endpoint
 * returned (and that the sibling {@code inventory}/{@code offerings} proxies use),
 * so the store list view needs no shape change — only the ids become Bubble
 * strings instead of UUIDs.
 */
@RestController
@RequestMapping("/api/stores")
public class StoreController {

    /** Bubble caps a single Data API page at 100 records. */
    private static final int BUBBLE_MAX_LIMIT = 100;

    private final BubbleClient bubbleClient;
    private final StoreBubbleMapper mapper;
    private final CurrentUserService currentUserService;

    public StoreController(BubbleClient bubbleClient,
                           StoreBubbleMapper mapper,
                           CurrentUserService currentUserService) {
        this.bubbleClient = bubbleClient;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    /**
     * List active (non-deleted) stores for the company, ordered by name. Bubble
     * cursor pagination is mapped onto the Spring Data {@link Page} envelope the
     * UI expects.
     */
    @GetMapping
    public Page<StoreDto> getStores(Pageable pageable) {
        Optional<String> companyOpt = currentUserService.currentCompanyId();
        if (companyOpt.isEmpty()) {
            return Page.empty(pageable);
        }

        String constraints = mapper.buildConstraints(companyOpt.get());

        int limit = Math.min(pageable.getPageSize(), BUBBLE_MAX_LIMIT);
        int cursor = (int) pageable.getOffset();

        BubbleListResult result = bubbleClient.list(
                StoreBubbleMapper.TYPE, constraints, cursor, limit,
                StoreBubbleMapper.SORT_NAME, false);

        List<StoreDto> content = result.getResults().stream()
                .map(mapper::toDto)
                .toList();

        // Bubble reports the count in this page and how many remain after it;
        // total = items before this page + this page + items remaining.
        long total = (long) cursor + result.getCount() + result.getRemaining();
        return new PageImpl<>(content, pageable, total);
    }

    @PostMapping
    public ResponseEntity<StoreDto> createStore(@RequestBody StoreDto body) {
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    Map<String, Object> createBody = mapper.toCreateBody(body, companyId);
                    String newId = bubbleClient.create(StoreBubbleMapper.TYPE, createBody);
                    return ResponseEntity.ok(reload(newId, body));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<StoreDto> updateStore(@PathVariable String id,
                                                @RequestBody StoreDto body) {
        return currentUserService.currentCompanyId()
                .filter(companyId -> ownedAndActive(id, companyId))
                .map(companyId -> {
                    bubbleClient.update(StoreBubbleMapper.TYPE, id, mapper.toUpdateBody(body));
                    return ResponseEntity.ok(reload(id, body));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Soft-delete — sets the Bubble is_deleted flag = true (never hard-deletes). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStore(@PathVariable String id) {
        return currentUserService.currentCompanyId()
                .filter(companyId -> ownedByCompany(id, companyId))
                .map(companyId -> {
                    bubbleClient.update(StoreBubbleMapper.TYPE, id, mapper.softDeleteBody());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------------- helpers

    /** True if the Bubble store {@code id} exists and belongs to {@code companyId}. */
    private boolean ownedByCompany(String id, String companyId) {
        Map<String, Object> record = bubbleClient.get(StoreBubbleMapper.TYPE, id);
        return record != null && companyId.equals(mapper.companyOf(record));
    }

    /** True if the Bubble store {@code id} exists, belongs to {@code companyId} and is still active. */
    private boolean ownedAndActive(String id, String companyId) {
        Map<String, Object> record = bubbleClient.get(StoreBubbleMapper.TYPE, id);
        return record != null
                && companyId.equals(mapper.companyOf(record))
                && !mapper.isDeleted(record);
    }

    /**
     * Re-fetch the store from Bubble so the response reflects persisted state.
     * Falls back to the request body (with the id set) if the read-back fails —
     * e.g. when privacy rules hide the freshly written record from this token.
     */
    private StoreDto reload(String id, StoreDto fallback) {
        if (id != null) {
            Map<String, Object> record = bubbleClient.get(StoreBubbleMapper.TYPE, id);
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
