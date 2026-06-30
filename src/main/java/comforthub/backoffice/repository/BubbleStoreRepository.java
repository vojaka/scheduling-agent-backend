package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.BubbleStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BubbleStoreRepository extends JpaRepository<BubbleStoreEntity, String> {
}
