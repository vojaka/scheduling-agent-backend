package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Append-only audit log of inbound provider webhook/callback events. */
@Entity
@Table(name = "payment_events")
@Data
@NoArgsConstructor
public class PaymentEventEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "payment_id")
    private UUID paymentId;

    /** Provider-unique event id — UNIQUE, used for webhook idempotency (#84 dedupes on it). */
    @Column(name = "provider_event_id", nullable = false, unique = true)
    private String providerEventId;

    @Column(name = "operation")
    private String operation;

    @Column(name = "type")
    private String type;

    @Column(name = "status", nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "signature_valid", nullable = false)
    private Boolean signatureValid = false;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;
}
