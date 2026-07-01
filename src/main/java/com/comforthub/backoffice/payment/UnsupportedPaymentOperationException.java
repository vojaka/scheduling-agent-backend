package com.comforthub.backoffice.payment;

/** Raised when a provider is asked for a capability it does not implement. */
public class UnsupportedPaymentOperationException extends PaymentException {
    public UnsupportedPaymentOperationException(ProviderKey provider, PaymentCapability capability) {
        super("Provider " + provider + " does not support capability " + capability);
    }
}
