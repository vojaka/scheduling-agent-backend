package com.comforthub.backoffice.payment.provider;

import com.comforthub.backoffice.payment.dto.DisputeEvent;

import java.util.Map;

public interface DisputeAware extends PaymentProvider {
    /** Verify + normalize an inbound chargeback/dispute notification. */
    DisputeEvent parseDispute(String rawBody, Map<String, String> headers);
}
