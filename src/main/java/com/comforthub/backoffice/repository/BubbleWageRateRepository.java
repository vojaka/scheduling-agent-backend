package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.BubbleWageRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BubbleWageRateRepository extends JpaRepository<BubbleWageRateEntity, String> {
}
