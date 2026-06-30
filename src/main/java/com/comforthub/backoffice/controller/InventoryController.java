package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.InventoryEntity;
import com.comforthub.backoffice.repository.InventoryOfferingRepository;
import com.comforthub.backoffice.repository.InventoryRepository;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Inventory CRUD — the product/service catalog (NOT stock quantities).
 * Soft-delete via is_deleted flag.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryRepository inventoryRepository;
    private final InventoryOfferingRepository inventoryOfferingRepository;
    private final CurrentUserService currentUserService;

    public InventoryController(InventoryRepository inventoryRepository,
                               InventoryOfferingRepository inventoryOfferingRepository,
                               CurrentUserService currentUserService) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryOfferingRepository = inventoryOfferingRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * List active (non-deleted) inventory items for the company.
     * Optional {@code search} param filters by name (case-insensitive).
     */
    @GetMapping
    public Page<InventoryEntity> getInventory(@RequestParam(required = false) String search,
                                              Pageable pageable) {
        return currentUserService.currentCompanyId()
                .map(companyId -> inventoryRepository.findActive(companyId, search, pageable))
                .orElseGet(Page::empty);
    }

    /** Returns the offering IDs linked to a specific inventory item. */
    @GetMapping("/{id}/offerings")
    public ResponseEntity<List<UUID>> getLinkedOfferings(@PathVariable UUID id) {
        return currentUserService.currentCompanyId()
                .flatMap(companyId -> inventoryRepository.findById(id)
                        .filter(i -> companyId.equals(i.getCompanyId()) && !Boolean.TRUE.equals(i.getIsDeleted())))
                .map(i -> ResponseEntity.ok(inventoryOfferingRepository.findOfferingIdsByInventoryId(id)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<InventoryEntity> createInventory(@RequestBody InventoryEntity body) {
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    body.setId(null);
                    body.setCompanyId(companyId);
                    body.setIsDeleted(false);
                    return ResponseEntity.ok(inventoryRepository.save(body));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<InventoryEntity> updateInventory(@PathVariable UUID id,
                                                           @RequestBody InventoryEntity body) {
        return currentUserService.currentCompanyId()
                .flatMap(companyId -> inventoryRepository.findById(id)
                        .filter(i -> companyId.equals(i.getCompanyId()) && !Boolean.TRUE.equals(i.getIsDeleted())))
                .map(existing -> {
                    if (body.getName() != null)          existing.setName(body.getName());
                    if (body.getType() != null)          existing.setType(body.getType());
                    if (body.getMainProductId() != null) existing.setMainProductId(body.getMainProductId());
                    if (body.getCategoryId() != null)    existing.setCategoryId(body.getCategoryId());
                    return ResponseEntity.ok(inventoryRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Soft-delete — sets is_deleted = true. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInventory(@PathVariable UUID id) {
        var existing = currentUserService.currentCompanyId()
                .flatMap(companyId -> inventoryRepository.findById(id)
                        .filter(i -> companyId.equals(i.getCompanyId())));
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        existing.get().setIsDeleted(true);
        inventoryRepository.save(existing.get());
        return ResponseEntity.ok().build();
    }
}
