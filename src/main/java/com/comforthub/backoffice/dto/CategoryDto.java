package com.comforthub.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Category payload for the React backoffice — a flattened two-level tree.
 *
 * <p>Field names mirror the former JPA {@code CategoryEntity} JSON one-for-one
 * (ids are now Bubble strings). In Bubble the tree is split across two types:
 * a top-level item is a {@code Main Product} ({@code parentId == null}) and a
 * sub item is a {@code Category} ({@code parentId} = its Main Product id).
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryDto {

    /** Bubble record id (of the Main Product or Category). */
    private String id;

    /** Same Bubble id, for parity with the old {@code bubbleId} field. */
    private String bubbleId;

    /** Owning company (the scoping key). */
    private String companyId;

    private String name;

    /** {@code null} = Main Product (top level); else the parent Main Product id. */
    private String parentId;

    private Integer sortOrder;

    /** ISO-8601 instant (Bubble "Created Date"). */
    private String createdAt;
}
