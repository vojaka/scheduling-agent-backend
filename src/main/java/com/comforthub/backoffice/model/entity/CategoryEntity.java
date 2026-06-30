package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "categories")
@Data
@NoArgsConstructor
public class CategoryEntity {

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

    /** NULL = Main Product level; non-null = Sub Category. */
    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (sortOrder == null) sortOrder = 0;
    }
}
