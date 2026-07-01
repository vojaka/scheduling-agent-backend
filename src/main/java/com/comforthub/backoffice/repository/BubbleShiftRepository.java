package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BubbleShiftRepository extends JpaRepository<BubbleShiftEntity, UUID> {
    Optional<BubbleShiftEntity> findByBubbleId(String bubbleId);
    List<BubbleShiftEntity> findByAssignedCompany(String assignedCompany);
    Page<BubbleShiftEntity> findByAssignedCompany(String assignedCompany, Pageable pageable);

    @Query("SELECT s FROM BubbleShiftEntity s WHERE s.assignedCompany = :companyId " +
           "AND s.startTime >= :from AND s.startTime <= :to " +
           "AND (s.assignedUser IS NULL OR s.assignedUser = '') " +
           "ORDER BY s.startTime ASC")
    List<BubbleShiftEntity> findUnassignedShifts(@Param("companyId") String companyId,
                                                 @Param("from") OffsetDateTime from,
                                                 @Param("to") OffsetDateTime to);
}
