package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    /**
     * List orders for a company with optional filters.
     * All params except companyId are optional (pass null to skip).
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.companyId = :companyId" +
           " AND (:storeId IS NULL OR o.storeId = :storeId)" +
           " AND (:assignedTo IS NULL OR o.assignedTo = :assignedTo)" +
           " AND (CAST(:orderNr AS string) IS NULL OR LOWER(o.orderNr) LIKE LOWER(CONCAT('%', CAST(:orderNr AS string), '%')))" +
           " AND (CAST(:customer AS string) IS NULL OR LOWER(o.customerName) LIKE LOWER(CONCAT('%', CAST(:customer AS string), '%')))" +
           " ORDER BY o.createdAt DESC")
    Page<OrderEntity> findByCompanyFiltered(@Param("companyId") String companyId,
                                            @Param("storeId") UUID storeId,
                                            @Param("assignedTo") UUID assignedTo,
                                            @Param("orderNr") String orderNr,
                                            @Param("customer") String customer,
                                            Pageable pageable);
}
