package com.comforthub.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of an inventory item's "Service Description Extension" list —
 * shown in the Bubble "Modify Inventory" editor as a repeatable icon picker +
 * title + body + Add row. Backed by the separate Bubble
 * {@code inventoryextensions} data type (10 fields, verified live 2026-07-01
 * against {@code comforthub_schema.md}), linked from the parent
 * {@code inventory} record two ways: the child's own {@code Inventory}
 * back-reference field, and the parent's forward {@code Extensions [list]}
 * field. See {@link com.comforthub.backoffice.mapper.InventoryBubbleMapper}
 * for how both sides are kept in sync.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InventoryExtensionDto {

    /**
     * Bubble {@code inventoryextensions} record id. {@code null} for a new
     * row not yet persisted — the backend creates it and assigns an id on
     * save (see {@code InventoryController#syncExtensions}).
     */
    private String id;

    /**
     * Bubble "Icon" field (Text). VERIFY: the Bubble editor presents this as
     * an icon picker, so the stored value is likely an icon-set identifier
     * (e.g. a class/name token) rather than arbitrary free text — but the
     * schema itself only types the field as plain Text, and nothing further
     * is enforced server-side here.
     */
    private String icon;

    /** Bubble "Extension Name" field — the row's title. */
    private String title;

    /** Bubble "Extension Description" field — the row's body text. */
    private String body;

    /** Bubble "List Position" field — display order within the parent's list. */
    private Integer position;
}
