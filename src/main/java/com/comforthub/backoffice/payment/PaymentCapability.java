package com.comforthub.backoffice.payment;

/** Capabilities a provider may implement. Used by the registry to resolve providers. */
public enum PaymentCapability {
    ONE_OFF,
    RECURRING,
    REFUND,
    DISPUTE,
    WEBHOOK
}
