package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BubbleShiftRepository extends JpaRepository<BubbleShiftEntity, UUID> {
    Optional<BubbleShiftEntity> findByBubbleId(String bubbleId);
    List<BubbleShiftEntity> findByAssignedCompany(String assignedCompany);
    Page<BubbleShiftEntity> findByAssignedCompany(String assignedCompany, Pageable pageable);
}
