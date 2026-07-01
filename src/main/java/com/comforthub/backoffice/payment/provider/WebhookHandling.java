package com.comforthub.backoffice.payment.provider;

import com.comforthub.backoffice.payment.dto.PaymentEvent;

import java.util.Map;

public interface WebhookHandling extends PaymentProvider {
    /** Verify + normalize an inbound payment webhook/callback. */
    PaymentEvent parseWebhook(String rawBody, Map<String, String> headers);
}
