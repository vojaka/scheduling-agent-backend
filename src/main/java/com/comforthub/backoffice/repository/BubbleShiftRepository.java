package com.comforthub.backoffice.repository;
import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BubbleShiftRepository extends JpaRepository<BubbleShiftEntity, String> {
    List<BubbleShiftEntity> findByAssignedCompany(String assignedCompany);
}
