package com.comforthub.backoffice.payment.spi;

import com.comforthub.backoffice.payment.dto.DisputeEvent;
import com.comforthub.backoffice.payment.dto.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op default recorder. Logs instead of persisting so the integration layer
 * runs before the payment domain entities exist. Replaced automatically once a
 * real {@link PaymentRecorder} bean is provided (see {@link PaymentRecorderConfig}).
 */
public class LoggingPaymentRecorder implements PaymentRecorder {

    private static final Logger log = LoggerFactory.getLogger(LoggingPaymentRecorder.class);

    @Override
    public void recordInitiated(RecordedPayment payment) {
        log.info("[payment:stub] initiated {} {} ref={} order={} amount={} {} token={}",
                payment.provider(), payment.operation(), payment.providerRef(),
                payment.orderId(), payment.amountMinor(), payment.currency(), payment.tokenRef());
    }

    @Override
    public boolean isDuplicateEvent(String providerEventId) {
        // No store yet — never treat as duplicate. The real recorder dedupes on a unique index.
        return false;
    }

    @Override
    public void recordEvent(PaymentEvent event) {
        log.info("[payment:stub] event ref={} op={} status={} eventId={} sigValid={}",
                event.getProviderRef(), event.getOperation(), event.getStatus(),
                event.getProviderEventId(), event.isSignatureValid());
    }

    @Override
    public void recordDispute(DisputeEvent dispute) {
        log.warn("[payment:stub] DISPUTE ref={} disputeRef={} reason={} amount={} {}",
                dispute.getProviderRef(), dispute.getProviderDisputeRef(),
                dispute.getReason(), dispute.getAmountMinor(), dispute.getCurrency());
    }

    @Override
    public void recordToken(RecordedToken token) {
        log.info("[payment:stub] token stored provider={} customer={} tokenRef={} brand={} pan={}",
                token.provider(), token.customerRef(), token.tokenRef(),
                token.cardBrand(), token.maskedPan());
    }

    @Override
    public void revokeToken(String tokenRef) {
        log.info("[payment:stub] token revoked tokenRef={}", tokenRef);
    }
}
