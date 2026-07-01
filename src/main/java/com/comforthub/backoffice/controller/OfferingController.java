package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.dto.OfferingDto;
import com.comforthub.backoffice.mapper.InventoryBubbleMapper;
import com.comforthub.backoffice.mapper.OfferingBubbleMapper;
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
 * Offerings CRUD — purchasable variants of inventory items — plus the
 * assign/unassign endpoints linking offerings to inventory.
 *
 * <p>Phase 5: Bubble is the source of truth. This controller <b>proxies the
 * Bubble Data API</b> for both reads and writes and never touches PostgreSQL
 * (which is analytics-only, fed by the hourly ETL). Every call is scoped to the
 * caller's company via a Bubble {@code constraints} filter, and writes re-check
 * ownership via {@code get()}.
 *
 * <p>The REST contract is unchanged from the former JPA implementation (same
 * routes, params and JSON shape, including the Spring Data {@code Page}
 * envelope), so the React UI needs no changes. All Bubble field-alias mapping
 * lives in {@link OfferingBubbleMapper}.
 *
 * <p><b>INFERRED / UNVERIFIED:</b> the {@code offerings} field aliases and the
 * inventory<->offering link model are inferred guesses — see
 * {@link OfferingBubbleMapper}. The assign/unassign endpoints assume the link is
 * a LIST field on the offering that is read-modify-written as a whole.
 */
@RestController
@RequestMapping("/api/offerings")
public class OfferingController {

    /** Bubble caps a single Data API page at 100 records. */
    private static final int BUBBLE_MAX_LIMIT = 100;

    private final BubbleClient bubbleClient;
    private final OfferingBubbleMapper mapper;
    private final InventoryBubbleMapper inventoryMapper;
    private final CurrentUserService currentUserService;

    public OfferingController(BubbleClient bubbleClient,
                              OfferingBubbleMapper mapper,
                              InventoryBubbleMapper inventoryMapper,
                              CurrentUserService currentUserService) {
        this.bubbleClient = bubbleClient;
        this.mapper = mapper;
        this.inventoryMapper = inventoryMapper;
        this.currentUserService = currentUserService;
    }

    /**
     * List offerings for the company.
     * Optional {@code status} param filters by 'Active', 'Draft', 'Archive' or 'Inactive'. Bubble
     * cursor pagination is mapped onto the Spring Data {@link Page} envelope the
     * UI expects.
     */
    @GetMapping
    public Page<OfferingDto> getOfferings(@RequestParam(required = false) String status,
                                          Pageable pageable) {
        Optional<String> companyOpt = currentUserService.currentCompanyId();
        if (companyOpt.isEmpty()) {
            return Page.empty(pageable);
        }

        String constraints = mapper.buildConstraints(companyOpt.get(), status);

        int limit = Math.min(pageable.getPageSize(), BUBBLE_MAX_LIMIT);
        int cursor = (int) pageable.getOffset();

        BubbleListResult result = bubbleClient.list(
                OfferingBubbleMapper.TYPE, constraints, cursor, limit,
                OfferingBubbleMapper.SORT_CREATED_DATE, true);

        List<OfferingDto> content = result.getResults().stream()
                .map(mapper::toDto)
                .toList();

        // Bubble reports the count in this page and how many remain after it;
        // total = items before this page + this page + items remaining.
        long total = (long) cursor + result.getCount() + result.getRemaining();
        return new PageImpl<>(content, pageable, total);
    }

    @PostMapping
    public ResponseEntity<OfferingDto> createOffering(@RequestBody OfferingDto body) {
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    Map<String, Object> createBody = mapper.toCreateBody(body, companyId);
                    String newId = bubbleClient.create(OfferingBubbleMapper.TYPE, createBody);
                    return ResponseEntity.ok(reload(newId, body));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<OfferingDto> updateOffering(@PathVariable String id,
                                                      @RequestBody OfferingDto body) {
        return currentUserService.currentCompanyId()
                .filter(companyId -> ownedByCompany(id, companyId))
                .map(companyId -> {
                    bubbleClient.update(OfferingBubbleMapper.TYPE, id, mapper.toUpdateBody(body));
                    return ResponseEntity.ok(reload(id, body));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Assign this offering to an inventory item.
     * Body: {@code { "inventoryId": "<id>" }}. Idempotent.
     *
     * <p>The inventory↔offering link is <b>bidirectional</b>: the offering carries
     * an {@code Inventory} list and the inventory carries an {@code Offerings}
     * list. Bubble list fields aren't auto-synced over the Data API, so we update
     * <b>both</b> sides (read-modify-write each list; already-present ids are
     * skipped). Verifies both records exist and belong to the company (restores
     * the old JPA inventory-ownership check).
     */
    @PostMapping("/{id}/assign")
    public ResponseEntity<Void> assignToInventory(@PathVariable String id,
                                                   @RequestBody Map<String, String> body) {
        String inventoryId = body.get("inventoryId");
        if (inventoryId == null || inventoryId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    Map<String, Object> offering = bubbleClient.get(OfferingBubbleMapper.TYPE, id);
                    if (offering == null || !companyId.equals(mapper.companyOf(offering))) {
                        return ResponseEntity.notFound().<Void>build();
                    }
                    Map<String, Object> inventory = bubbleClient.get(InventoryBubbleMapper.TYPE, inventoryId);
                    if (inventory == null
                            || !companyId.equals(inventoryMapper.companyOf(inventory))
                            || inventoryMapper.isDeleted(inventory)) {
                        return ResponseEntity.notFound().<Void>build();
                    }
                    // Keep both sides of the bidirectional link in sync.
                    Map<String, Object> offeringPatch = mapper.addInventoryToList(offering, inventoryId);
                    if (offeringPatch != null) {
                        bubbleClient.update(OfferingBubbleMapper.TYPE, id, offeringPatch);
                    }
                    Map<String, Object> inventoryPatch = inventoryMapper.addOfferingToList(inventory, id);
                    if (inventoryPatch != null) {
                        bubbleClient.update(InventoryBubbleMapper.TYPE, inventoryId, inventoryPatch);
                    }
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Remove the link between this offering and an inventory item.
     * Body: {@code { "inventoryId": "<id>" }}. Idempotent.
     *
     * <p>Removes the id from <b>both</b> sides of the bidirectional link (the
     * offering's {@code Inventory} list and the inventory's {@code Offerings}
     * list). The inventory-side update is best-effort — skipped if that record is
     * missing or foreign.
     */
    @DeleteMapping("/{id}/assign")
    public ResponseEntity<Void> unassignFromInventory(@PathVariable String id,
                                                       @RequestBody Map<String, String> body) {
        String inventoryId = body.get("inventoryId");
        if (inventoryId == null || inventoryId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    Map<String, Object> offering = bubbleClient.get(OfferingBubbleMapper.TYPE, id);
                    if (offering == null || !companyId.equals(mapper.companyOf(offering))) {
                        return ResponseEntity.notFound().<Void>build();
                    }
                    Map<String, Object> offeringPatch = mapper.removeInventoryFromList(offering, inventoryId);
                    if (offeringPatch != null) {
                        bubbleClient.update(OfferingBubbleMapper.TYPE, id, offeringPatch);
                    }
                    // Best-effort: also drop this offering from the inventory's list.
                    Map<String, Object> inventory = bubbleClient.get(InventoryBubbleMapper.TYPE, inventoryId);
                    if (inventory != null && companyId.equals(inventoryMapper.companyOf(inventory))) {
                        Map<String, Object> inventoryPatch = inventoryMapper.removeOfferingFromList(inventory, id);
                        if (inventoryPatch != null) {
                            bubbleClient.update(InventoryBubbleMapper.TYPE, inventoryId, inventoryPatch);
                        }
                    }
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOffering(@PathVariable String id) {
        return currentUserService.currentCompanyId()
                .filter(companyId -> ownedByCompany(id, companyId))
                .map(companyId -> {
                    bubbleClient.delete(OfferingBubbleMapper.TYPE, id);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------------- helpers

    /** True if the Bubble offering {@code id} exists and belongs to {@code companyId}. */
    private boolean ownedByCompany(String id, String companyId) {
        Map<String, Object> record = bubbleClient.get(OfferingBubbleMapper.TYPE, id);
        return record != null && companyId.equals(mapper.companyOf(record));
    }

    /**
     * Re-fetch the offering from Bubble so the response reflects persisted state.
     * Falls back to the request body (with the id set) if the read-back fails —
     * e.g. when privacy rules hide the freshly written record from this token.
     */
    private OfferingDto reload(String id, OfferingDto fallback) {
        if (id != null) {
            Map<String, Object> record = bubbleClient.get(OfferingBubbleMapper.TYPE, id);
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
