package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "disputes")
@Data
@NoArgsConstructor
public class DisputeEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Links back to the original charge in {@code payments} — the #83 acceptance criterion. */
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "provider_ref", nullable = false)
    private String providerRef;

    @Column(name = "provider")
    private String provider;

    @Column(name = "provider_dispute_ref", nullable = false, unique = true)
    private String providerDisputeRef;

    @Column(name = "reason")
    private String reason;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @Column(name = "currency", nullable = false)
    private String currency;

    /** OPEN / WON / LOST / CANCELLED. */
    @Column(name = "status", nullable = false)
    private String status = "OPEN";

    @Column(name = "respond_by")
    private OffsetDateTime respondBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
