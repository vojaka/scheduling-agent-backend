package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.CategoryEntity;
import com.comforthub.backoffice.repository.CategoryRepository;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Categories CRUD — two-level tree (Main Products + Sub Categories).
 * All reads/writes are scoped to the caller's company via CurrentUserService.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;

    public CategoryController(CategoryRepository categoryRepository,
                              CurrentUserService currentUserService) {
        this.categoryRepository = categoryRepository;
        this.currentUserService = currentUserService;
    }

    /** Returns all categories (main + sub) for the company, ordered by sort_order then name. */
    @GetMapping
    public List<CategoryEntity> getCategories() {
        return currentUserService.currentCompanyId()
                .map(categoryRepository::findByCompanyIdOrderBySortOrderAscNameAsc)
                .orElseGet(Collections::emptyList);
    }

    @PostMapping
    public ResponseEntity<CategoryEntity> createCategory(@RequestBody CategoryEntity body) {
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    body.setId(null);
                    body.setCompanyId(companyId);
                    return ResponseEntity.ok(categoryRepository.save(body));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryEntity> updateCategory(@PathVariable UUID id,
                                                         @RequestBody CategoryEntity body) {
        return currentUserService.currentCompanyId()
                .flatMap(companyId -> categoryRepository.findById(id)
                        .filter(c -> companyId.equals(c.getCompanyId())))
                .map(existing -> {
                    existing.setName(body.getName());
                    if (body.getParentId() != null) existing.setParentId(body.getParentId());
                    if (body.getSortOrder() != null) existing.setSortOrder(body.getSortOrder());
                    return ResponseEntity.ok(categoryRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        var existing = currentUserService.currentCompanyId()
                .flatMap(companyId -> categoryRepository.findById(id)
                        .filter(c -> companyId.equals(c.getCompanyId())));
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        categoryRepository.delete(existing.get());
        return ResponseEntity.ok().build();
    }
}
