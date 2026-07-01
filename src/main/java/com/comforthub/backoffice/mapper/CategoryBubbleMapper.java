package com.comforthub.backoffice.mapper;

import com.comforthub.backoffice.dto.CategoryDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates the backoffice's flat two-level category tree to/from Bubble, where
 * it is split across two types (confirmed from the live data types):
 * <ul>
 *   <li><b>Main Product</b> ({@code mainproduct}) = the top level — fields
 *       {@code name}, {@code order}; a company's tops are the {@code Company}'s
 *       {@code "Products"} list (that's how they're scoped — neither Main Product
 *       nor Category has a company field).</li>
 *   <li><b>Category</b> ({@code category}) = the sub level — fields {@code name}
 *       and a {@code mainProduct} ref (its parent).</li>
 * </ul>
 * So the {@link CategoryDto} tree is: Main Products ({@code parentId == null}) +
 * Categories ({@code parentId} = their {@code mainProduct}).
 */
@Component
public class CategoryBubbleMapper {

    /** Bubble object types. */
    public static final String COMPANY_TYPE = "company";
    public static final String MAINPRODUCT_TYPE = "mainproduct";
    public static final String CATEGORY_TYPE = "category";

    /** Company's list of Main Product ids — how top-level categories are scoped. */
    static final String F_COMPANY_PRODUCTS = "Products";

    // Main Product (top level) fields.
    static final String F_MP_NAME  = "name";
    static final String F_MP_ORDER = "order";

    // Category (sub level) fields.
    static final String F_CAT_NAME        = "name";
    static final String F_CAT_MAINPRODUCT = "mainProduct";

    private final ObjectMapper objectMapper;

    public CategoryBubbleMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- reads

    /** Map a Bubble {@code mainproduct} record to a top-level category DTO. */
    public CategoryDto toMainProductDto(Map<String, Object> r, String companyId) {
        CategoryDto dto = baseDto(r, companyId);
        dto.setName(readString(r, F_MP_NAME));
        dto.setParentId(null); // top level
        dto.setSortOrder(readInteger(r, F_MP_ORDER));
        return dto;
    }

    /** Map a Bubble {@code category} record to a sub-level category DTO. */
    public CategoryDto toCategoryDto(Map<String, Object> r, String companyId) {
        CategoryDto dto = baseDto(r, companyId);
        dto.setName(readString(r, F_CAT_NAME));
        dto.setParentId(readString(r, F_CAT_MAINPRODUCT)); // parent Main Product
        dto.setSortOrder(null); // Category has no order field
        return dto;
    }

    private CategoryDto baseDto(Map<String, Object> r, String companyId) {
        CategoryDto dto = new CategoryDto();
        String id = readString(r, "_id");
        dto.setId(id);
        dto.setBubbleId(id);
        dto.setCompanyId(companyId);
        dto.setCreatedAt(readInstant(r, "Created Date"));
        return dto;
    }

    /** The Main Product ids held on a Company record (its top-level categories). */
    public List<String> productIdsOf(Map<String, Object> companyRecord) {
        return readStringList(companyRecord, F_COMPANY_PRODUCTS);
    }

    // ------------------------------------------------------- constraints/scope

    /** Constraints selecting the sub-categories under any of {@code mainProductIds}. */
    public String categoriesByMainProducts(Collection<String> mainProductIds) {
        return writeConstraints(List.of(
                constraint(F_CAT_MAINPRODUCT, "in", new ArrayList<>(mainProductIds))));
    }

    /** Constraints selecting records whose {@code _id} is one of {@code ids}. */
    public String idIn(Collection<String> ids) {
        return writeConstraints(List.of(constraint("_id", "in", new ArrayList<>(ids))));
    }

    // --------------------------------------------------------------- writes

    /** Body for creating a Main Product (top-level category). */
    public Map<String, Object> mainProductCreateBody(CategoryDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_MP_NAME, dto.getName());
        body.put(F_MP_ORDER, dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        return body;
    }

    /** Partial body for updating a Main Product. */
    public Map<String, Object> mainProductUpdateBody(CategoryDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_MP_NAME, dto.getName());
        putIfPresent(body, F_MP_ORDER, dto.getSortOrder());
        return body;
    }

    /** Body for creating a Category (sub-level) under {@code parentId}. */
    public Map<String, Object> categoryCreateBody(CategoryDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_CAT_NAME, dto.getName());
        putIfPresent(body, F_CAT_MAINPRODUCT, dto.getParentId());
        return body;
    }

    /** Partial body for updating a Category. */
    public Map<String, Object> categoryUpdateBody(CategoryDto dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_CAT_NAME, dto.getName());
        putIfPresent(body, F_CAT_MAINPRODUCT, dto.getParentId()); // re-parent
        return body;
    }

    /**
     * New value for a Company's {@code Products} list with {@code mainProductId}
     * added (idempotent). Returns null if already present.
     */
    public Map<String, Object> addProduct(Map<String, Object> companyRecord, String mainProductId) {
        List<String> current = productIdsOf(companyRecord);
        if (current.contains(mainProductId)) {
            return null;
        }
        List<String> updated = new ArrayList<>(current);
        updated.add(mainProductId);
        return productsListBody(updated);
    }

    /**
     * New value for a Company's {@code Products} list with {@code mainProductId}
     * removed (idempotent). Returns null if absent.
     */
    public Map<String, Object> removeProduct(Map<String, Object> companyRecord, String mainProductId) {
        List<String> current = productIdsOf(companyRecord);
        if (!current.contains(mainProductId)) {
            return null;
        }
        List<String> updated = new ArrayList<>(current);
        updated.removeIf(mainProductId::equals);
        return productsListBody(updated);
    }

    private Map<String, Object> productsListBody(List<String> ids) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_COMPANY_PRODUCTS, ids);
        return body;
    }

    // --------------------------------------------------------------- helpers

    private String writeConstraints(List<Map<String, Object>> constraints) {
        try {
            return objectMapper.writeValueAsString(constraints);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build Bubble constraints", e);
        }
    }

    private static Map<String, Object> constraint(String key, String type, Object value) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("key", key);
        c.put("constraint_type", type);
        c.put("value", value);
        return c;
    }

    private static void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value != null && !(value instanceof String s && s.isBlank())) {
            body.put(key, value);
        }
    }

    private static String readString(Map<String, Object> r, String key) {
        if (r == null) {
            return null;
        }
        Object v = r.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private static Integer readInteger(Map<String, Object> r, String key) {
        Object v = r == null ? null : r.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(v).trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String readInstant(Map<String, Object> r, String key) {
        Object v = r == null ? null : r.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return Instant.ofEpochMilli(n.longValue()).toString();
        }
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(Map<String, Object> r, String key) {
        List<String> out = new ArrayList<>();
        Object v = r == null ? null : r.get(key);
        if (v == null) {
            return out;
        }
        if (v instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        } else {
            String s = String.valueOf(v);
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }
}
