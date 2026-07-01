package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.DisputeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DisputeRepository extends JpaRepository<DisputeEntity, UUID> {
}
