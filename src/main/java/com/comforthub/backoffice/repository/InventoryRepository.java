package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.InventoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InventoryRepository extends JpaRepository<InventoryEntity, UUID> {

    @Query("SELECT i FROM InventoryEntity i WHERE i.companyId = :companyId AND i.isDeleted = false" +
           " AND (:search IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<InventoryEntity> findActive(@Param("companyId") String companyId,
                                     @Param("search") String search,
                                     Pageable pageable);
}
