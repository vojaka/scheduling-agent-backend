package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.PaymentEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEventEntity, UUID> {
    boolean existsByProviderEventId(String providerEventId);
}
