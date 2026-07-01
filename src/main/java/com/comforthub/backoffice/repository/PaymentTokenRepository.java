package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.PaymentTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTokenRepository extends JpaRepository<PaymentTokenEntity, UUID> {
    Optional<PaymentTokenEntity> findByTokenRef(String tokenRef);
}
