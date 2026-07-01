package com.comforthub.backoffice.payment.dto;

import com.comforthub.backoffice.payment.ProviderKey;
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
    /**
     * Stamped by {@code PaymentService.handleDispute} after {@code parseDispute}
     * returns — provider implementations don't set this themselves, mirroring how
     * {@code PaymentEvent.operation} is caller-known rather than provider-parsed.
     */
    private ProviderKey provider;
}
