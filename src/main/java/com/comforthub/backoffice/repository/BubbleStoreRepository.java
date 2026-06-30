package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.BubbleStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BubbleStoreRepository extends JpaRepository<BubbleStoreEntity, UUID> {
    Optional<BubbleStoreEntity> findByBubbleId(String bubbleId);
    List<BubbleStoreEntity> findByCompany(String company);
    Page<BubbleStoreEntity> findByCompany(String company, Pageable pageable);
}
