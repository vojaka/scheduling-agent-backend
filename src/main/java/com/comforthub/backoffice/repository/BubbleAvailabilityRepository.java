package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.BubbleAvailabilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BubbleAvailabilityRepository extends JpaRepository<BubbleAvailabilityEntity, String> {
}
