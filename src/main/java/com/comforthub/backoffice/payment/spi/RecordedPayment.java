package com.comforthub.backoffice.payment.spi;

import com.comforthub.backoffice.payment.PaymentOperation;
import com.comforthub.backoffice.payment.ProviderKey;

/**
 * Immutable carrier for a payment to be persisted. No card data — token refs only.
 *
 * @param parentProviderRef for REFUND/CHARGEBACK/CANCELLATION operations, the
 *        provider reference of the *original* charge this row descends from
 *        (e.g. {@code RefundRequest.providerRef}, echoed back on {@code RefundResult}).
 *        {@code null} for a fresh ONE_OFF/RECURRING_INIT/RECURRING_CHARGE payment.
 *        The recorder resolves this to {@code payments.parent_payment_id} and also
 *        uses it to backfill {@code orderId}/{@code companyId} when the caller
 *        didn't have them on hand (refunds are keyed by provider ref only — see
 *        {@code RefundRequest}).
 */
public record RecordedPayment(
        ProviderKey provider,
        PaymentOperation operation,
        String providerRef,
        String orderId,
        String companyId,
        long amountMinor,
        String currency,
        String method,
        String tokenRef,
        String parentProviderRef
) {
}
