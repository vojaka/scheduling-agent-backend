package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BubbleUserRepository extends JpaRepository<BubbleUserEntity, UUID> {
    Optional<BubbleUserEntity> findByBubbleId(String bubbleId);
    Optional<BubbleUserEntity> findByAuth0UserId(String auth0UserId);
    List<BubbleUserEntity> findByCompanyId(String companyId);
    Page<BubbleUserEntity> findByCompanyId(String companyId, Pageable pageable);

    /** Company-scoped membership check for the invite flow (case-insensitive email). */
    List<BubbleUserEntity> findByCompanyIdAndEmailIgnoreCase(String companyId, String email);
}
