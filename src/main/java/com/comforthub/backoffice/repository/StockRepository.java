package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.StockEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends JpaRepository<StockEntity, UUID> {

    Optional<StockEntity> findByStoreIdAndInventoryId(UUID storeId, UUID inventoryId);

    @Query("SELECT s FROM StockEntity s JOIN InventoryEntity i ON i.id = s.inventoryId" +
           " WHERE s.companyId = :companyId" +
           " AND (:storeId IS NULL OR s.storeId = :storeId)" +
           " AND (:name IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', :name, '%')))")
    Page<StockEntity> findByCompanyFiltered(@Param("companyId") String companyId,
                                            @Param("storeId") UUID storeId,
                                            @Param("name") String name,
                                            Pageable pageable);
}
