package com.comforthub.backoffice.consumer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * One node of the consumer catalog tree: a Bubble Main Product (top level)
 * with its Categories (sub level) as {@code children}. The backoffice returns
 * the same two Bubble types as a flat list ({@code CategoryDto}); the consumer
 * app wants the nested shape for menu rendering.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CatalogCategoryDto {

    /** Bubble record id ({@code mainproduct} or {@code category} {@code _id}). */
    private String id;

    private String name;

    /** Main Products carry a sort order; Categories do not (null). */
    private Integer sortOrder;

    /** Sub categories — only populated on top-level (Main Product) nodes. */
    private List<CatalogCategoryDto> children = new ArrayList<>();
}
