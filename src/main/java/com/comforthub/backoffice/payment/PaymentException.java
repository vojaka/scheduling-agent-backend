package com.comforthub.backoffice.payment;

/** Base runtime exception for the payment domain. */
public class PaymentException extends RuntimeException {
    public PaymentException(String message) {
        super(message);
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
