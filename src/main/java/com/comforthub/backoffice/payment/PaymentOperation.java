package com.comforthub.backoffice.payment;

/** First-class payment operations modelled across all providers. */
public enum PaymentOperation {
    ONE_OFF,
    RECURRING_INIT,
    RECURRING_CHARGE,
    REFUND,
    CHARGEBACK,
    CANCELLATION
}
