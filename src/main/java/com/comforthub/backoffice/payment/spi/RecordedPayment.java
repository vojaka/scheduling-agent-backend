package com.comforthub.backoffice.payment.spi;

import com.comforthub.backoffice.payment.PaymentOperation;
import com.comforthub.backoffice.payment.ProviderKey;

/** Immutable carrier for a payment to be persisted. No card data — token refs only. */
public record RecordedPayment(
        ProviderKey provider,
        PaymentOperation operation,
        String providerRef,
        String orderId,
        String companyId,
        long amountMinor,
        String currency,
        String method,
        String tokenRef
) {
}
