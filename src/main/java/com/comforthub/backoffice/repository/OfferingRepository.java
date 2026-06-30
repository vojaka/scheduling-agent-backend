package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.OfferingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface OfferingRepository extends JpaRepository<OfferingEntity, UUID> {

    @Query("SELECT o FROM OfferingEntity o WHERE o.companyId = :companyId" +
           " AND (:status IS NULL OR o.status = :status)")
    Page<OfferingEntity> findByCompanyIdAndStatus(@Param("companyId") String companyId,
                                                  @Param("status") String status,
                                                  Pageable pageable);
}
