package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock")
@Data
@NoArgsConstructor
public class StockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Scoping key — Bubble company text id. */
    @Column(name = "company_id")
    private String companyId;

    /** FK → stores.id */
    @Column(name = "store_id")
    private UUID storeId;

    /** FK → inventory.id */
    @Column(name = "inventory_id")
    private UUID inventoryId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
        if (quantity == null) quantity = 0;
    }
}
