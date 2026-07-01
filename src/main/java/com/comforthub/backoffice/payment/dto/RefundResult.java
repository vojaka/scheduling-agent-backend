package com.comforthub.backoffice.payment.dto;

import com.comforthub.backoffice.payment.PaymentStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundResult {
    private String providerRef;
    private String refundRef;
    private PaymentStatus status;
    private long amountMinor;
}
