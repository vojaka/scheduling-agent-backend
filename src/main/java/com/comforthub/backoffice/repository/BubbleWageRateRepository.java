package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.BubbleWageRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BubbleWageRateRepository extends JpaRepository<BubbleWageRateEntity, UUID> {
    Optional<BubbleWageRateEntity> findByBubbleId(String bubbleId);
}
