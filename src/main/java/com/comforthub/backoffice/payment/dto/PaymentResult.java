package com.comforthub.backoffice.payment.dto;

import com.comforthub.backoffice.payment.PaymentStatus;
import lombok.Builder;
import lombok.Data;

/** Result of a server-to-server charge (e.g. MIT). */
@Data
@Builder
public class PaymentResult {
    private String providerRef;
    private PaymentStatus status;
    /** Set when a token was issued/used. */
    private String tokenRef;
    private String rawStatus;
}
