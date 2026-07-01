package com.comforthub.backoffice.payment.provider;

import com.comforthub.backoffice.payment.dto.PaymentResult;
import com.comforthub.backoffice.payment.dto.PaymentSession;
import com.comforthub.backoffice.payment.dto.RecurringChargeRequest;
import com.comforthub.backoffice.payment.dto.RecurringInitRequest;

public interface RecurringPayments extends PaymentProvider {
    /** CIT — customer-present initial payment that captures consent + a reusable token. */
    PaymentSession initRecurring(RecurringInitRequest request);

    /** MIT — subsequent merchant-initiated charge against a stored token. */
    PaymentResult chargeRecurring(RecurringChargeRequest request);

    /** Revoke a stored token so it can no longer be charged. */
    void revokeToken(String tokenRef);
}
