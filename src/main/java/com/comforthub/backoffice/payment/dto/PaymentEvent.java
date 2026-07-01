package com.comforthub.backoffice.payment.dto;

import com.comforthub.backoffice.payment.PaymentOperation;
import com.comforthub.backoffice.payment.PaymentStatus;
import lombok.Builder;
import lombok.Data;

/** Normalized inbound payment webhook/callback event. */
@Data
@Builder
public class PaymentEvent {
    private String providerRef;
    /** Provider-unique event id used for idempotent processing. */
    private String providerEventId;
    private PaymentOperation operation;
    private PaymentStatus status;
    private Long amountMinor;
    private String currency;
    private String tokenRef;
    private boolean signatureValid;
    private String rawPayload;
}
