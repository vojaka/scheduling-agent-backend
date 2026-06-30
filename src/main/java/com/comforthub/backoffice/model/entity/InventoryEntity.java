package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory")
@Data
@NoArgsConstructor
public class InventoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "bubble_id", unique = true)
    private String bubbleId;

    /** Scoping key — Bubble company text id. */
    @Column(name = "company_id")
    private String companyId;

    @Column(name = "name", nullable = false)
    private String name;

    /** 'Service' | 'Good' | etc. */
    @Column(name = "type")
    private String type;

    /** FK → categories.id (Main Product level, parent_id IS NULL). */
    @Column(name = "main_product_id")
    private UUID mainProductId;

    /** FK → categories.id (Sub Category level, parent_id IS NOT NULL). */
    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "is_deleted")
    private Boolean isDeleted;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (isDeleted == null) isDeleted = false;
    }
}
