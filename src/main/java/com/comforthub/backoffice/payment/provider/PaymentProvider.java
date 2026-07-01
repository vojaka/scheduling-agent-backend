package com.comforthub.backoffice.payment.provider;

import com.comforthub.backoffice.payment.PaymentCapability;
import com.comforthub.backoffice.payment.ProviderKey;

import java.util.Set;

/** Base marker for a payment provider. Capabilities are opt-in via sub-interfaces. */
public interface PaymentProvider {
    ProviderKey key();

    Set<PaymentCapability> capabilities();
}
