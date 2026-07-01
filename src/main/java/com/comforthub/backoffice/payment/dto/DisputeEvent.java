package com.comforthub.backoffice.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/** Normalized inbound chargeback/dispute notification. */
@Data
@Builder
public class DisputeEvent {
    /** Original payment reference. */
    private String providerRef;
    private String providerDisputeRef;
    private String providerEventId;
    private String reason;
    private Long amountMinor;
    private String currency;
    private Instant respondBy;
    private boolean signatureValid;
    private String rawPayload;
}
