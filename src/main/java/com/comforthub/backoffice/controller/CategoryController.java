package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.dto.CategoryDto;
import com.comforthub.backoffice.mapper.CategoryBubbleMapper;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Categories CRUD — a flat two-level tree (Main Products + Sub Categories).
 *
 * <p>Phase 5: Bubble is the source of truth; this controller proxies the Bubble
 * Data API and never touches PostgreSQL.
 *
 * <p><b>The tree spans two Bubble types</b> (see {@link CategoryBubbleMapper}):
 * top-level items are {@code Main Product}s (scoped by the {@code Company}'s
 * {@code Products} list), sub items are {@code Category}s (linked to their Main
 * Product via {@code mainProduct}). Reads merge both; writes route by level
 * ({@code parentId == null} ⇒ Main Product, else Category).
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private static final int BUBBLE_MAX_LIMIT = 100;

    private final BubbleClient bubbleClient;
    private final CategoryBubbleMapper mapper;
    private final CurrentUserService currentUserService;

    public CategoryController(BubbleClient bubbleClient,
                              CategoryBubbleMapper mapper,
                              CurrentUserService currentUserService) {
        this.bubbleClient = bubbleClient;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    /** All categories (Main Products + Sub Categories) for the company, sorted by order then name. */
    @GetMapping
    public List<CategoryDto> getCategories() {
        Optional<String> companyOpt = currentUserService.currentCompanyId();
        if (companyOpt.isEmpty()) {
            return List.of();
        }
        String companyId = companyOpt.get();

        List<String> productIds = companyProductIds(companyId);
        if (productIds.isEmpty()) {
            return List.of();
        }

        List<CategoryDto> out = new ArrayList<>();
        // Top level: the company's Main Products.
        for (Map<String, Object> mp : bubbleClient.list(
                CategoryBubbleMapper.MAINPRODUCT_TYPE, mapper.idIn(productIds), 0, BUBBLE_MAX_LIMIT)
                .getResults()) {
            out.add(mapper.toMainProductDto(mp, companyId));
        }
        // Sub level: Categories whose parent Main Product belongs to the company.
        for (Map<String, Object> cat : bubbleClient.list(
                CategoryBubbleMapper.CATEGORY_TYPE, mapper.categoriesByMainProducts(productIds), 0, BUBBLE_MAX_LIMIT)
                .getResults()) {
            out.add(mapper.toCategoryDto(cat, companyId));
        }

        out.sort(Comparator
                .comparingInt((CategoryDto c) -> c.getSortOrder() != null ? c.getSortOrder() : 0)
                .thenComparing(c -> c.getName() != null ? c.getName() : "", String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /**
     * Create a category. {@code parentId == null} ⇒ a Main Product (added to the
     * company's Products list so it's scoped); otherwise a Category under that
     * Main Product.
     */
    @PostMapping
    public ResponseEntity<CategoryDto> createCategory(@RequestBody CategoryDto body) {
        Optional<String> companyOpt = currentUserService.currentCompanyId();
        if (companyOpt.isEmpty()) {
            return ResponseEntity.status(403).build();
        }
        String companyId = companyOpt.get();

        if (body.getParentId() == null) {
            // Top-level Main Product — create then attach to the company.
            String newId = bubbleClient.create(
                    CategoryBubbleMapper.MAINPRODUCT_TYPE, mapper.mainProductCreateBody(body));
            attachProductToCompany(companyId, newId);
            return ResponseEntity.ok(reloadMainProduct(newId, companyId, body));
        }
        // Sub Category — must hang off one of the company's Main Products.
        if (!companyProductIds(companyId).contains(body.getParentId())) {
            return ResponseEntity.status(403).build();
        }
        String newId = bubbleClient.create(
                CategoryBubbleMapper.CATEGORY_TYPE, mapper.categoryCreateBody(body));
        return ResponseEntity.ok(reloadCategory(newId, companyId, body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDto> updateCategory(@PathVariable String id,
                                                      @RequestBody CategoryDto body) {
        Optional<String> companyOpt = currentUserService.currentCompanyId();
        if (companyOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String companyId = companyOpt.get();
        List<String> productIds = companyProductIds(companyId);

        // A Main Product the company owns?
        if (productIds.contains(id)) {
            bubbleClient.update(CategoryBubbleMapper.MAINPRODUCT_TYPE, id, mapper.mainProductUpdateBody(body));
            return ResponseEntity.ok(reloadMainProduct(id, companyId, body));
        }
        // Otherwise a Category under one of the company's Main Products?
        Map<String, Object> cat = bubbleClient.get(CategoryBubbleMapper.CATEGORY_TYPE, id);
        if (cat != null && productIds.contains(mapper.toCategoryDto(cat, companyId).getParentId())) {
            bubbleClient.update(CategoryBubbleMapper.CATEGORY_TYPE, id, mapper.categoryUpdateBody(body));
            return ResponseEntity.ok(reloadCategory(id, companyId, body));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable String id) {
        Optional<String> companyOpt = currentUserService.currentCompanyId();
        if (companyOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String companyId = companyOpt.get();
        List<String> productIds = companyProductIds(companyId);

        if (productIds.contains(id)) {
            // Main Product — detach from the company, then delete.
            detachProductFromCompany(companyId, id);
            bubbleClient.delete(CategoryBubbleMapper.MAINPRODUCT_TYPE, id);
            return ResponseEntity.ok().build();
        }
        Map<String, Object> cat = bubbleClient.get(CategoryBubbleMapper.CATEGORY_TYPE, id);
        if (cat != null && productIds.contains(mapper.toCategoryDto(cat, companyId).getParentId())) {
            bubbleClient.delete(CategoryBubbleMapper.CATEGORY_TYPE, id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ------------------------------------------------------------- helpers

    /** The company's Main Product ids (its top-level categories). */
    private List<String> companyProductIds(String companyId) {
        Map<String, Object> company = bubbleClient.get(CategoryBubbleMapper.COMPANY_TYPE, companyId);
        return company == null ? List.of() : mapper.productIdsOf(company);
    }

    private void attachProductToCompany(String companyId, String mainProductId) {
        Map<String, Object> company = bubbleClient.get(CategoryBubbleMapper.COMPANY_TYPE, companyId);
        if (company != null) {
            Map<String, Object> patch = mapper.addProduct(company, mainProductId);
            if (patch != null) {
                bubbleClient.update(CategoryBubbleMapper.COMPANY_TYPE, companyId, patch);
            }
        }
    }

    private void detachProductFromCompany(String companyId, String mainProductId) {
        Map<String, Object> company = bubbleClient.get(CategoryBubbleMapper.COMPANY_TYPE, companyId);
        if (company != null) {
            Map<String, Object> patch = mapper.removeProduct(company, mainProductId);
            if (patch != null) {
                bubbleClient.update(CategoryBubbleMapper.COMPANY_TYPE, companyId, patch);
            }
        }
    }

    private CategoryDto reloadMainProduct(String id, String companyId, CategoryDto fallback) {
        if (id != null) {
            Map<String, Object> r = bubbleClient.get(CategoryBubbleMapper.MAINPRODUCT_TYPE, id);
            if (r != null) {
                return mapper.toMainProductDto(r, companyId);
            }
        }
        return withIds(fallback, id, companyId);
    }

    private CategoryDto reloadCategory(String id, String companyId, CategoryDto fallback) {
        if (id != null) {
            Map<String, Object> r = bubbleClient.get(CategoryBubbleMapper.CATEGORY_TYPE, id);
            if (r != null) {
                return mapper.toCategoryDto(r, companyId);
            }
        }
        return withIds(fallback, id, companyId);
    }

    private static CategoryDto withIds(CategoryDto dto, String id, String companyId) {
        if (dto != null) {
            dto.setId(id);
            dto.setBubbleId(id);
            dto.setCompanyId(companyId);
        }
        return dto;
    }
}
