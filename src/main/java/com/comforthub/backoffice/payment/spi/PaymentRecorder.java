package com.comforthub.backoffice.payment.spi;

import com.comforthub.backoffice.payment.dto.DisputeEvent;
import com.comforthub.backoffice.payment.dto.PaymentEvent;

/**
 * Persistence seam for the payment domain.
 *
 * <p>Deliberately provider- and JPA-agnostic. The concrete implementation
 * (entities + Flyway migration + Bubble order write-back) is intentionally
 * NOT part of this change — it lands with the domain-entity work (issue #83).
 * Until then {@link LoggingPaymentRecorder} keeps the app running by logging.
 *
 * <p>Wiring point: provide a {@code @Component} implementing this interface and
 * it replaces the logging default (see {@code PaymentRecorderConfig}).
 */
public interface PaymentRecorder {

    /** Record that an operation was initiated (before redirect / provider ack). */
    void recordInitiated(RecordedPayment payment);

    /** Idempotency guard: has this provider event already been applied? */
    boolean isDuplicateEvent(String providerEventId);

    /** Persist a normalized inbound payment event and advance payment state. */
    void recordEvent(PaymentEvent event);

    /** Persist a normalized dispute/chargeback and flag the payment/order. */
    void recordDispute(DisputeEvent dispute);

    /** Persist a token issued during a recurring-init (CIT) payment. */
    void recordToken(RecordedToken token);

    /** Mark a stored token revoked. */
    void revokeToken(String tokenRef);
}
