package com.comforthub.backoffice.payment.dto;

import com.comforthub.backoffice.payment.ProviderKey;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundRequest {
    private ProviderKey provider;
    /** Provider reference of the original payment. */
    private String providerRef;
    /** null ⇒ full refund. */
    private Long amountMinor;
    private String currency;
    private String reason;
}
