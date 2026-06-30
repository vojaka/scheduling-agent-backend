package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.BubbleStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BubbleStoreRepository extends JpaRepository<BubbleStoreEntity, UUID> {
    Optional<BubbleStoreEntity> findByBubbleId(String bubbleId);
}
