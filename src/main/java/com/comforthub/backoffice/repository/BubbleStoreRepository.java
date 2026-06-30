package com.comforthub.backoffice.repository;
import com.comforthub.backoffice.model.entity.BubbleStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BubbleStoreRepository extends JpaRepository<BubbleStoreEntity, String> {
    List<BubbleStoreEntity> findByCompany(String company);
}
