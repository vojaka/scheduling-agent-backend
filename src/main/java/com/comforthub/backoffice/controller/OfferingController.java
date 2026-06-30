package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.InventoryOfferingEntity;
import com.comforthub.backoffice.model.entity.OfferingEntity;
import com.comforthub.backoffice.repository.InventoryOfferingRepository;
import com.comforthub.backoffice.repository.InventoryRepository;
import com.comforthub.backoffice.repository.OfferingRepository;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Offerings CRUD — purchasable variants of inventory items.
 * Also handles the assign endpoint to link offerings to inventory.
 */
@RestController
@RequestMapping("/api/offerings")
public class OfferingController {

    private final OfferingRepository offeringRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryOfferingRepository inventoryOfferingRepository;
    private final CurrentUserService currentUserService;

    public OfferingController(OfferingRepository offeringRepository,
                              InventoryRepository inventoryRepository,
                              InventoryOfferingRepository inventoryOfferingRepository,
                              CurrentUserService currentUserService) {
        this.offeringRepository = offeringRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryOfferingRepository = inventoryOfferingRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * List offerings for the company.
     * Optional {@code status} param filters by 'Active' or 'Inactive'.
     */
    @GetMapping
    public Page<OfferingEntity> getOfferings(@RequestParam(required = false) String status,
                                             Pageable pageable) {
        return currentUserService.currentCompanyId()
                .map(companyId -> offeringRepository.findByCompanyIdAndStatus(companyId, status, pageable))
                .orElseGet(Page::empty);
    }

    @PostMapping
    public ResponseEntity<OfferingEntity> createOffering(@RequestBody OfferingEntity body) {
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    body.setId(null);
                    body.setCompanyId(companyId);
                    return ResponseEntity.ok(offeringRepository.save(body));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<OfferingEntity> updateOffering(@PathVariable UUID id,
                                                         @RequestBody OfferingEntity body) {
        return currentUserService.currentCompanyId()
                .flatMap(companyId -> offeringRepository.findById(id)
                        .filter(o -> companyId.equals(o.getCompanyId())))
                .map(existing -> {
                    if (body.getName() != null)            existing.setName(body.getName());
                    if (body.getType() != null)            existing.setType(body.getType());
                    if (body.getStatus() != null)          existing.setStatus(body.getStatus());
                    if (body.getDeliveryType() != null)    existing.setDeliveryType(body.getDeliveryType());
                    if (body.getPayOptions() != null)      existing.setPayOptions(body.getPayOptions());
                    if (body.getPriceSource() != null)     existing.setPriceSource(body.getPriceSource());
                    if (body.getDefaultType() != null)     existing.setDefaultType(body.getDefaultType());
                    if (body.getLimitedVisibility() != null)  existing.setLimitedVisibility(body.getLimitedVisibility());
                    if (body.getUnlimitedQuantity() != null)  existing.setUnlimitedQuantity(body.getUnlimitedQuantity());
                    if (body.getQuantityRequired() != null)   existing.setQuantityRequired(body.getQuantityRequired());
                    return ResponseEntity.ok(offeringRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Assign this offering to an inventory item.
     * Body: {@code { "inventoryId": "<uuid>" }}.
     * Idempotent — duplicate assignments are ignored (ON CONFLICT DO NOTHING via the PK constraint).
     */
    @PostMapping("/{id}/assign")
    public ResponseEntity<Void> assignToInventory(@PathVariable UUID id,
                                                   @RequestBody Map<String, UUID> body) {
        UUID inventoryId = body.get("inventoryId");
        if (inventoryId == null) return ResponseEntity.badRequest().build();

        return currentUserService.currentCompanyId()
                .flatMap(companyId -> offeringRepository.findById(id)
                        .filter(o -> companyId.equals(o.getCompanyId()))
                        .flatMap(o -> inventoryRepository.findById(inventoryId)
                                .filter(i -> companyId.equals(i.getCompanyId()))))
                .map(i -> {
                    InventoryOfferingEntity link = new InventoryOfferingEntity(inventoryId, id);
                    // save is idempotent — if the row already exists the PK constraint ignores it
                    if (!inventoryOfferingRepository.existsById(new InventoryOfferingEntity.PK(inventoryId, id))) {
                        inventoryOfferingRepository.save(link);
                    }
                    return ResponseEntity.<Void>ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Remove the link between this offering and an inventory item.
     * Body: {@code { "inventoryId": "<uuid>" }}.
     */
    @DeleteMapping("/{id}/assign")
    public ResponseEntity<Void> unassignFromInventory(@PathVariable UUID id,
                                                       @RequestBody Map<String, UUID> body) {
        UUID inventoryId = body.get("inventoryId");
        if (inventoryId == null) return ResponseEntity.badRequest().build();

        return currentUserService.currentCompanyId()
                .flatMap(companyId -> offeringRepository.findById(id)
                        .filter(o -> companyId.equals(o.getCompanyId())))
                .map(o -> {
                    inventoryOfferingRepository.deleteByInventoryIdAndOfferingId(inventoryId, id);
                    return ResponseEntity.<Void>ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOffering(@PathVariable UUID id) {
        return currentUserService.currentCompanyId()
                .flatMap(companyId -> offeringRepository.findById(id)
                        .filter(o -> companyId.equals(o.getCompanyId())))
                .map(existing -> {
                    offeringRepository.delete(existing);
                    return ResponseEntity.<Void>ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
