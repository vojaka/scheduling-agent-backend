package com.comforthub.backoffice.payment;

/** Normalized payment status across providers. */
public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    PAID,
    FAILED,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    CHARGEBACK,
    DISPUTED
}
