package com.comforthub.backoffice.payment.dto;

import com.comforthub.backoffice.payment.PaymentStatus;
import lombok.Builder;
import lombok.Data;

/** Result of initiating a hosted payment. */
@Data
@Builder
public class PaymentSession {
    private String providerRef;
    /** Hosted payment page URL to redirect the shopper to. */
    private String redirectUrl;
    private PaymentStatus status;
}
