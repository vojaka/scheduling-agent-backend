package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.InventoryOfferingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InventoryOfferingRepository
        extends JpaRepository<InventoryOfferingEntity, InventoryOfferingEntity.PK> {

    /** All inventory items an offering is assigned to. */
    List<InventoryOfferingEntity> findByOfferingId(UUID offeringId);

    /** All offerings for a given inventory item. */
    @Query("SELECT io.offeringId FROM InventoryOfferingEntity io WHERE io.inventoryId = :inventoryId")
    List<UUID> findOfferingIdsByInventoryId(@Param("inventoryId") UUID inventoryId);

    void deleteByInventoryIdAndOfferingId(UUID inventoryId, UUID offeringId);
}
