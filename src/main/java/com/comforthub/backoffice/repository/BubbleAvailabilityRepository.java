package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.BubbleAvailabilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BubbleAvailabilityRepository extends JpaRepository<BubbleAvailabilityEntity, UUID> {
    Optional<BubbleAvailabilityEntity> findByBubbleId(String bubbleId);
}
