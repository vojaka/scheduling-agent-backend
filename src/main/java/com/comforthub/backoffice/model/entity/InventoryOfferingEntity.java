package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Join entity for the inventory_offerings many-to-many link table.
 * Used by OfferingController to assign offerings to inventory items.
 */
@Entity
@Table(name = "inventory_offerings")
@IdClass(InventoryOfferingEntity.PK.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryOfferingEntity {

    @Id
    @Column(name = "inventory_id")
    private UUID inventoryId;

    @Id
    @Column(name = "offering_id")
    private UUID offeringId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private UUID inventoryId;
        private UUID offeringId;
    }
}
