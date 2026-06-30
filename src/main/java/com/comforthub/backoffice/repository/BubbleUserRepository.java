package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BubbleUserRepository extends JpaRepository<BubbleUserEntity, UUID> {
    Optional<BubbleUserEntity> findByBubbleId(String bubbleId);
    Optional<BubbleUserEntity> findByAuth0UserId(String auth0UserId);
    List<BubbleUserEntity> findByCompanyId(String companyId);
}
