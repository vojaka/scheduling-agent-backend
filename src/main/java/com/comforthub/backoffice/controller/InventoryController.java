package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.dto.InventoryDto;
import com.comforthub.backoffice.dto.InventoryExtensionDto;
import com.comforthub.backoffice.mapper.InventoryBubbleMapper;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Inventory CRUD — the product/service catalog (NOT stock quantities).
 * Soft-delete via the Bubble is_deleted flag.
 *
 * <p>Phase 5: Bubble is the source of truth. This controller <b>proxies the
 * Bubble Data API</b> for both reads and writes and never touches PostgreSQL
 * (which is analytics-only, fed by the hourly ETL). Every call is scoped to the
 * caller's company via a Bubble {@code constraints} filter.
 *
 * <p>The REST contract is unchanged from the former JPA implementation
 * (same routes, params and JSON shape, including the Spring Data {@code Page}
 * envelope), so the React UI needs no changes. The Bubble field-alias mapping
 * lives entirely in {@link InventoryBubbleMapper}.
 *
 * <p><b>NOTE:</b> {@code GET /{id}/offerings} previously returned
 * {@code List<UUID>}; it now returns {@code List<String>} of Bubble ids. Both
 * serialise to JSON strings, so the wire contract is preserved. The link itself
 * is INFERRED — see {@link InventoryBubbleMapper#offeringsOf} — and must be
 * verified against the live Bubble schema.
 *
 * <p><b>Extensions (#93):</b> the "Service Description Extension" repeatable
 * icon/title/body rows are backed by the separate Bubble
 * {@code inventoryextensions} type. Rather than a whole new controller, this
 * class reads/writes them as a list scoped to the parent inventory item —
 * see {@code GET /{id}/extensions} and {@link #syncExtensions}.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    /** Bubble caps a single Data API page at 100 records. */
    private static final int BUBBLE_MAX_LIMIT = 100;

    private final BubbleClient bubbleClient;
    private final InventoryBubbleMapper mapper;
    private final CurrentUserService currentUserService;

    public InventoryController(BubbleClient bubbleClient,
                               InventoryBubbleMapper mapper,
                               CurrentUserService currentUserService) {
        this.bubbleClient = bubbleClient;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    /**
     * List active (non-deleted) inventory items for the company.
     * Optional {@code search} param filters by name (case-insensitive substring).
     * Bubble cursor pagination is mapped onto the Spring Data {@link Page}
     * envelope the UI expects.
     */
    @GetMapping
    public Page<InventoryDto> getInventory(@RequestParam(required = false) String search,
                                           Pageable pageable) {
        Optional<String> companyOpt = currentUserService.currentCompanyId();
        if (companyOpt.isEmpty()) {
            return Page.empty(pageable);
        }

        String constraints = mapper.buildConstraints(companyOpt.get(), search);

        int limit = Math.min(pageable.getPageSize(), BUBBLE_MAX_LIMIT);
        int cursor = (int) pageable.getOffset();

        BubbleListResult result = bubbleClient.list(
                InventoryBubbleMapper.TYPE, constraints, cursor, limit,
                InventoryBubbleMapper.SORT_CREATED_DATE, true);

        List<InventoryDto> content = result.getResults().stream()
                .map(mapper::toDto)
                .toList();

        // Bubble reports the count in this page and how many remain after it;
        // total = items before this page + this page + items remaining.
        long total = (long) cursor + result.getCount() + result.getRemaining();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Returns the offering ids linked to a specific inventory item.
     * (Was {@code List<UUID>}; now {@code List<String>} of Bubble ids.)
     */
    @GetMapping("/{id}/offerings")
    public ResponseEntity<List<String>> getLinkedOfferings(@PathVariable String id) {
        String companyId = currentUserService.currentCompanyId().orElse(null);
        if (companyId == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> record = bubbleClient.get(InventoryBubbleMapper.TYPE, id);
        if (record == null
                || !companyId.equals(mapper.companyOf(record))
                || mapper.isDeleted(record)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mapper.offeringsOf(record));
    }

    /**
     * Returns the "Service Description Extension" rows (icon/title/body)
     * linked to a specific inventory item, ordered by Bubble's
     * {@code List Position}. Used by the edit modal to hydrate the
     * repeatable extensions editor, since list responses from
     * {@link #getInventory} don't eager-load extensions (see
     * {@link InventoryDto#getExtensions()}).
     */
    @GetMapping("/{id}/extensions")
    public ResponseEntity<List<InventoryExtensionDto>> getExtensions(@PathVariable String id) {
        String companyId = currentUserService.currentCompanyId().orElse(null);
        if (companyId == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> record = bubbleClient.get(InventoryBubbleMapper.TYPE, id);
        if (record == null || !companyId.equals(mapper.companyOf(record))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(loadExtensions(id));
    }

    @PostMapping
    public ResponseEntity<InventoryDto> createInventory(@RequestBody InventoryDto body) {
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    Map<String, Object> createBody = mapper.toCreateBody(body, companyId);
                    String newId = bubbleClient.create(InventoryBubbleMapper.TYPE, createBody);
                    if (newId != null && body.getExtensions() != null) {
                        syncExtensions(newId, body.getExtensions());
                    }
                    return ResponseEntity.ok(reload(newId, body));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<InventoryDto> updateInventory(@PathVariable String id,
                                                        @RequestBody InventoryDto body) {
        return currentUserService.currentCompanyId()
                .filter(companyId -> ownedAndActive(id, companyId))
                .map(companyId -> {
                    bubbleClient.update(InventoryBubbleMapper.TYPE, id, mapper.toUpdateBody(body));
                    if (body.getExtensions() != null) {
                        syncExtensions(id, body.getExtensions());
                    }
                    return ResponseEntity.ok(reload(id, body));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Soft-delete — sets the Bubble is_deleted flag = true (never hard-deletes). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInventory(@PathVariable String id) {
        return currentUserService.currentCompanyId()
                .filter(companyId -> ownedByCompany(id, companyId))
                .map(companyId -> {
                    bubbleClient.update(InventoryBubbleMapper.TYPE, id, mapper.softDeleteBody());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------------- helpers

    /** True if the Bubble inventory {@code id} exists and belongs to {@code companyId}. */
    private boolean ownedByCompany(String id, String companyId) {
        Map<String, Object> record = bubbleClient.get(InventoryBubbleMapper.TYPE, id);
        return record != null && companyId.equals(mapper.companyOf(record));
    }

    /**
     * True if the Bubble inventory {@code id} exists, belongs to {@code companyId}
     * and is still active (not soft-deleted). Matches the old PUT/offerings
     * filter which excluded {@code is_deleted = true} rows.
     */
    private boolean ownedAndActive(String id, String companyId) {
        Map<String, Object> record = bubbleClient.get(InventoryBubbleMapper.TYPE, id);
        return record != null
                && companyId.equals(mapper.companyOf(record))
                && !mapper.isDeleted(record);
    }

    /**
     * Re-fetch the item from Bubble so the response reflects persisted state,
     * including its current extensions.
     * Falls back to the request body (with the id set) if the read-back fails —
     * e.g. when privacy rules hide the freshly written record from this token.
     */
    private InventoryDto reload(String id, InventoryDto fallback) {
        if (id != null) {
            Map<String, Object> record = bubbleClient.get(InventoryBubbleMapper.TYPE, id);
            if (record != null) {
                InventoryDto dto = mapper.toDto(record);
                dto.setExtensions(loadExtensions(id));
                return dto;
            }
        }
        if (fallback != null) {
            fallback.setId(id);
            fallback.setBubbleId(id);
        }
        return fallback;
    }

    /** Fetch and order the {@code inventoryextensions} rows for one inventory item. */
    private List<InventoryExtensionDto> loadExtensions(String inventoryId) {
        String constraints = mapper.buildExtensionConstraints(inventoryId);
        BubbleListResult result = bubbleClient.list(
                InventoryBubbleMapper.EXTENSION_TYPE, constraints, 0, BUBBLE_MAX_LIMIT);
        List<InventoryExtensionDto> extensions = new ArrayList<>(result.getResults().stream()
                .map(mapper::toExtensionDto)
                .toList());
        // Defensive client-side sort in addition to relying on Bubble's own
        // ordering, so display order is stable regardless of Bubble's default.
        extensions.sort(InventoryBubbleMapper.BY_POSITION);
        return extensions;
    }

    /**
     * Reconciles an inventory item's {@code inventoryextensions} child
     * records against the desired list from the UI: updates rows that carry
     * an existing Bubble id, creates rows that don't, deletes any existing
     * child not present in {@code desired}, and mirrors the resulting id
     * order onto the parent's forward {@code Extensions} list field.
     */
    private void syncExtensions(String inventoryId, List<InventoryExtensionDto> desired) {
        List<InventoryExtensionDto> existing = loadExtensions(inventoryId);
        Set<String> existingIds = new LinkedHashSet<>();
        for (InventoryExtensionDto e : existing) {
            if (e.getId() != null) {
                existingIds.add(e.getId());
            }
        }

        Set<String> keptIds = new LinkedHashSet<>();
        List<String> orderedIds = new ArrayList<>();
        int position = 0;
        for (InventoryExtensionDto d : desired) {
            d.setPosition(position++);
            if (d.getId() != null && existingIds.contains(d.getId())) {
                bubbleClient.update(InventoryBubbleMapper.EXTENSION_TYPE, d.getId(),
                        mapper.toExtensionUpdateBody(d));
                keptIds.add(d.getId());
                orderedIds.add(d.getId());
            } else {
                String newId = bubbleClient.create(InventoryBubbleMapper.EXTENSION_TYPE,
                        mapper.toExtensionCreateBody(d, inventoryId));
                if (newId != null) {
                    keptIds.add(newId);
                    orderedIds.add(newId);
                }
            }
        }

        for (String existingId : existingIds) {
            if (!keptIds.contains(existingId)) {
                bubbleClient.delete(InventoryBubbleMapper.EXTENSION_TYPE, existingId);
            }
        }

        bubbleClient.update(InventoryBubbleMapper.TYPE, inventoryId, mapper.extensionsListBody(orderedIds));
    }
}
