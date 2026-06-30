package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.StockEntity;
import com.comforthub.backoffice.repository.StockRepository;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Stock — per-store inventory quantity levels.
 * Reads are paginated and filterable by store + inventory name.
 * Writes only support quantity updates (no create/delete — rows are auto-created by sync).
 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockRepository stockRepository;
    private final CurrentUserService currentUserService;

    public StockController(StockRepository stockRepository,
                           CurrentUserService currentUserService) {
        this.stockRepository = stockRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * List stock entries for the company.
     * Optional {@code storeId} and {@code name} (inventory name substring) params.
     */
    @GetMapping
    public Page<StockEntity> getStock(@RequestParam(required = false) UUID storeId,
                                      @RequestParam(required = false) String name,
                                      Pageable pageable) {
        return currentUserService.currentCompanyId()
                .map(companyId -> stockRepository.findByCompanyFiltered(companyId, storeId, name, pageable))
                .orElseGet(Page::empty);
    }

    /**
     * Update the quantity for a specific store + inventory combination.
     * Creates the row if it doesn't exist yet (upsert).
     * Body: {@code { "quantity": 42 }}.
     */
    @PutMapping("/{storeId}/{inventoryId}")
    public ResponseEntity<StockEntity> updateQuantity(@PathVariable UUID storeId,
                                                       @PathVariable UUID inventoryId,
                                                       @RequestBody StockEntity body) {
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    StockEntity stock = stockRepository
                            .findByStoreIdAndInventoryId(storeId, inventoryId)
                            .orElseGet(() -> {
                                StockEntity s = new StockEntity();
                                s.setCompanyId(companyId);
                                s.setStoreId(storeId);
                                s.setInventoryId(inventoryId);
                                return s;
                            });
                    stock.setQuantity(body.getQuantity());
                    return ResponseEntity.ok(stockRepository.save(stock));
                })
                .orElse(ResponseEntity.status(403).build());
    }
}
