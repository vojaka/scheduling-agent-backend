package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_tokens")
@Data
@NoArgsConstructor
public class PaymentTokenEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Column(name = "customer_ref", nullable = false)
    private String customerRef;

    @Column(name = "token_ref", nullable = false, unique = true)
    private String tokenRef;

    @Column(name = "agreement_type", nullable = false)
    private String agreementType;

    @Column(name = "card_brand")
    private String cardBrand;

    @Column(name = "masked_pan")
    private String maskedPan;

    @Column(name = "is_revoked", nullable = false)
    private Boolean isRevoked = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
