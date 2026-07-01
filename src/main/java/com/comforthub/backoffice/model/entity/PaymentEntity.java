package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
public class PaymentEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "operation", nullable = false)
    private String operation;

    @Column(name = "provider_ref", nullable = false)
    private String providerRef;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "method")
    private String method;

    @Column(name = "token_ref")
    private String tokenRef;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring = false;

    /** Links a REFUND/CHARGEBACK/CANCELLATION row back to the original charge. */
    @Column(name = "parent_payment_id")
    private UUID parentPaymentId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
