package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.consumer.dto.CatalogCategoryDto;
import com.comforthub.backoffice.consumer.dto.ConsumerProductDto;
import com.comforthub.backoffice.consumer.mapper.ConsumerCatalogScope;
import com.comforthub.backoffice.dto.CategoryDto;
import com.comforthub.backoffice.dto.OfferingDto;
import com.comforthub.backoffice.dto.StockDto;
import com.comforthub.backoffice.mapper.CategoryBubbleMapper;
import com.comforthub.backoffice.mapper.InventoryBubbleMapper;
import com.comforthub.backoffice.mapper.OfferingBubbleMapper;
import com.comforthub.backoffice.mapper.StockBubbleMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Consumer catalog — read-only browsing of the marketplace.
 *
 * <p>Proxies the Bubble Data API through the existing backoffice mappers
 * (categories, inventory, offerings, stock). Unlike the backoffice
 * controllers there is <b>no company scope</b>: consumers browse the whole
 * marketplace (the Bubble shop pages behave the same way), limited to
 * non-deleted records. JWT authentication is still required — the catalog is
 * only served to signed-in app users.
 */
@RestController
@RequestMapping("/api/consumer/catalog")
public class ConsumerCatalogController {

    /** Bubble caps a single Data API page at 100 records. */
    private static final int BUBBLE_MAX_LIMIT = 100;

    private final BubbleClient bubbleClient;
    private final CategoryBubbleMapper categoryMapper;
    private final InventoryBubbleMapper inventoryMapper;
    private final OfferingBubbleMapper offeringMapper;
    private final StockBubbleMapper stockMapper;
    private final ConsumerCatalogScope scope;

    public ConsumerCatalogController(BubbleClient bubbleClient,
                                     CategoryBubbleMapper categoryMapper,
                                     InventoryBubbleMapper inventoryMapper,
                                     OfferingBubbleMapper offeringMapper,
                                     StockBubbleMapper stockMapper,
                                     ConsumerCatalogScope scope) {
        this.bubbleClient = bubbleClient;
        this.categoryMapper = categoryMapper;
        this.inventoryMapper = inventoryMapper;
        this.offeringMapper = offeringMapper;
        this.stockMapper = stockMapper;
        this.scope = scope;
    }

    /**
     * The category tree: Main Products (sorted by order, then name) with their
     * sub Categories nested as children.
     */
    @GetMapping("/categories")
    public List<CatalogCategoryDto> getCategories() {
        // Top level: all Main Products.
        List<CatalogCategoryDto> tops = new ArrayList<>();
        Map<String, CatalogCategoryDto> byId = new HashMap<>();
        for (Map<String, Object> mp : bubbleClient.list(
                CategoryBubbleMapper.MAINPRODUCT_TYPE, null, 0, BUBBLE_MAX_LIMIT).getResults()) {
            CatalogCategoryDto node = toNode(categoryMapper.toMainProductDto(mp, null));
            tops.add(node);
            if (node.getId() != null) {
                byId.put(node.getId(), node);
            }
        }
        // Sub level: all Categories, nested under their Main Product.
        for (Map<String, Object> cat : bubbleClient.list(
                CategoryBubbleMapper.CATEGORY_TYPE, null, 0, BUBBLE_MAX_LIMIT).getResults()) {
            CategoryDto dto = categoryMapper.toCategoryDto(cat, null);
            CatalogCategoryDto parent = dto.getParentId() != null ? byId.get(dto.getParentId()) : null;
            if (parent != null) {
                parent.getChildren().add(toNode(dto));
            }
        }
        tops.sort(Comparator
                .comparingInt((CatalogCategoryDto c) -> c.getSortOrder() != null ? c.getSortOrder() : 0)
                .thenComparing(c -> c.getName() != null ? c.getName() : "", String.CASE_INSENSITIVE_ORDER));
        for (CatalogCategoryDto top : tops) {
            top.getChildren().sort(Comparator.comparing(
                    c -> c.getName() != null ? c.getName() : "", String.CASE_INSENSITIVE_ORDER));
        }
        return tops;
    }

    /**
     * Active products, optionally filtered by Main Product / Category / name
     * substring, each enriched with its offerings and per-store stock.
     */
    @GetMapping("/products")
    public List<ConsumerProductDto> getProducts(@RequestParam(required = false) String mainProductId,
                                                @RequestParam(required = false) String categoryId,
                                                @RequestParam(required = false) String search,
                                                @RequestParam(required = false) Integer cursor,
                                                @RequestParam(required = false) Integer limit) {
        int l = limit != null ? Math.min(limit, BUBBLE_MAX_LIMIT) : BUBBLE_MAX_LIMIT;
        List<Map<String, Object>> records = bubbleClient.list(
                InventoryBubbleMapper.TYPE,
                scope.activeProducts(mainProductId, categoryId, search),
                cursor, l,
                InventoryBubbleMapper.SORT_CREATED_DATE, true).getResults();
        return enrich(records);
    }

    /** One product with offerings and stock; 404 when missing or soft-deleted. */
    @GetMapping("/products/{id}")
    public ResponseEntity<ConsumerProductDto> getProduct(@PathVariable String id) {
        Map<String, Object> record = bubbleClient.get(InventoryBubbleMapper.TYPE, id);
        if (record == null || inventoryMapper.isDeleted(record)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(enrich(List.of(record)).get(0));
    }

    // ------------------------------------------------------------- helpers

    /**
     * Batch-enrich inventory records: one offerings fetch ({@code _id in}) and
     * one stock fetch ({@code Inventory in}) for the whole page — no N+1.
     */
    private List<ConsumerProductDto> enrich(List<Map<String, Object>> records) {
        List<String> productIds = new ArrayList<>();
        Set<String> offeringIds = new LinkedHashSet<>();
        for (Map<String, Object> r : records) {
            Object id = r.get("_id");
            if (id != null) {
                productIds.add(String.valueOf(id));
            }
            offeringIds.addAll(inventoryMapper.offeringsOf(r));
        }

        Map<String, OfferingDto> offeringsById = new HashMap<>();
        if (!offeringIds.isEmpty()) {
            for (Map<String, Object> o : bubbleClient.list(
                    OfferingBubbleMapper.TYPE, scope.idIn(offeringIds), 0, BUBBLE_MAX_LIMIT).getResults()) {
                OfferingDto dto = offeringMapper.toDto(o);
                if (dto.getId() != null) {
                    offeringsById.put(dto.getId(), dto);
                }
            }
        }

        Map<String, List<StockDto>> stockByInventory = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Map<String, Object> s : bubbleClient.list(
                    StockBubbleMapper.TYPE, scope.stockForInventories(productIds), 0, BUBBLE_MAX_LIMIT).getResults()) {
                StockDto dto = stockMapper.toDto(s);
                if (dto.getInventoryId() != null) {
                    stockByInventory.computeIfAbsent(dto.getInventoryId(), k -> new ArrayList<>()).add(dto);
                }
            }
        }

        List<ConsumerProductDto> out = new ArrayList<>(records.size());
        for (Map<String, Object> r : records) {
            ConsumerProductDto dto = new ConsumerProductDto();
            dto.setProduct(inventoryMapper.toDto(r));
            for (String offeringId : inventoryMapper.offeringsOf(r)) {
                OfferingDto offering = offeringsById.get(offeringId);
                if (offering != null) {
                    dto.getOfferings().add(offering);
                }
            }
            List<StockDto> stock = stockByInventory.get(dto.getProduct().getId());
            if (stock != null) {
                dto.getStock().addAll(stock);
            }
            dto.setAvailable(isAvailable(dto));
            out.add(dto);
        }
        return out;
    }

    private static boolean isAvailable(ConsumerProductDto dto) {
        boolean unlimited = dto.getOfferings().stream()
                .anyMatch(o -> Boolean.TRUE.equals(o.getUnlimitedQuantity()));
        boolean inStock = dto.getStock().stream()
                .anyMatch(s -> s.getQuantity() != null && s.getQuantity() > 0);
        return unlimited || inStock;
    }

    private static CatalogCategoryDto toNode(CategoryDto dto) {
        CatalogCategoryDto node = new CatalogCategoryDto();
        node.setId(dto.getId());
        node.setName(dto.getName());
        node.setSortOrder(dto.getSortOrder());
        return node;
    }
}
