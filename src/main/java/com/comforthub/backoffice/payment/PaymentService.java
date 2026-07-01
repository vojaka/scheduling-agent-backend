package com.comforthub.backoffice.payment;

import com.comforthub.backoffice.payment.config.PaymentProperties;
import com.comforthub.backoffice.payment.dto.*;
import com.comforthub.backoffice.payment.provider.*;
import com.comforthub.backoffice.payment.spi.PaymentRecorder;
import com.comforthub.backoffice.payment.spi.RecordedPayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Orchestrates payment operations against capability-typed providers. */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentProviderRegistry registry;
    private final PaymentRecorder recorder;
    private final PaymentMetrics metrics;
    private final PaymentProperties properties;

    public PaymentService(PaymentProviderRegistry registry, PaymentRecorder recorder,
                          PaymentMetrics metrics, PaymentProperties properties) {
        this.registry = registry;
        this.recorder = recorder;
        this.metrics = metrics;
        this.properties = properties;
    }

    private ProviderKey resolve(ProviderKey requested) {
        return requested != null ? requested : properties.getDefaultProvider();
    }

    public PaymentSession payOneOff(OneOffPaymentRequest request) {
        ProviderKey key = resolve(request.getProvider());
        OneOffPayments provider = registry.require(key, OneOffPayments.class, PaymentCapability.ONE_OFF);
        metrics.initiated(key, PaymentOperation.ONE_OFF, request.getMethod());
        try {
            PaymentSession session = provider.createOneOff(request);
            recorder.recordInitiated(new RecordedPayment(key, PaymentOperation.ONE_OFF,
                    session.getProviderRef(), request.getOrderId(), request.getCompanyId(),
                    request.getAmountMinor(), request.getCurrency(), request.getMethod(), null, null));
            return session;
        } catch (RuntimeException e) {
            metrics.failed(key, PaymentOperation.ONE_OFF, "create_error");
            throw e;
        }
    }

    public PaymentSession startRecurring(RecurringInitRequest request) {
        ProviderKey key = resolve(request.getProvider());
        RecurringPayments provider = registry.require(key, RecurringPayments.class, PaymentCapability.RECURRING);
        metrics.initiated(key, PaymentOperation.RECURRING_INIT, request.getMethod());
        try {
            PaymentSession session = provider.initRecurring(request);
            recorder.recordInitiated(new RecordedPayment(key, PaymentOperation.RECURRING_INIT,
                    session.getProviderRef(), request.getOrderId(), request.getCompanyId(),
                    request.getAmountMinor(), request.getCurrency(), request.getMethod(), null, null));
            return session;
        } catch (RuntimeException e) {
            metrics.failed(key, PaymentOperation.RECURRING_INIT, "init_error");
            throw e;
        }
    }

    public PaymentResult chargeRecurring(RecurringChargeRequest request) {
        ProviderKey key = resolve(request.getProvider());
        RecurringPayments provider = registry.require(key, RecurringPayments.class, PaymentCapability.RECURRING);
        try {
            PaymentResult result = provider.chargeRecurring(request);
            boolean ok = result.getStatus() == PaymentStatus.PAID || result.getStatus() == PaymentStatus.AUTHORIZED;
            metrics.recurringCharge(key, ok);
            recorder.recordInitiated(new RecordedPayment(key, PaymentOperation.RECURRING_CHARGE,
                    result.getProviderRef(), request.getOrderId(), request.getCompanyId(),
                    request.getAmountMinor(), request.getCurrency(), null, request.getTokenRef(), null));
            return result;
        } catch (RuntimeException e) {
            metrics.recurringCharge(key, false);
            throw e;
        }
    }

    public RefundResult refund(RefundRequest request) {
        ProviderKey key = resolve(request.getProvider());
        RefundablePayments provider = registry.require(key, RefundablePayments.class, PaymentCapability.REFUND);
        RefundResult result = provider.refund(request);
        metrics.refund(key);
        // result.getProviderRef() echoes back the *original* payment's provider ref
        // (see MontonioPaymentProvider/EveryPayPaymentProvider#refund) — orderId/
        // companyId are intentionally left null here (RefundRequest doesn't carry
        // them; refunds are keyed by provider ref only) and backfilled by the
        // recorder from the parent payment it resolves via parentProviderRef.
        recorder.recordInitiated(new RecordedPayment(key, PaymentOperation.REFUND,
                result.getRefundRef(), null, null, result.getAmountMinor(),
                request.getCurrency(), null, null, result.getProviderRef()));
        return result;
    }

    /** Handle an inbound webhook/callback for the given provider. */
    public void handleWebhook(ProviderKey key, String rawBody, Map<String, String> headers) {
        metrics.webhookReceived(key);
        WebhookHandling provider = registry.require(key, WebhookHandling.class, PaymentCapability.WEBHOOK);
        PaymentEvent event = provider.parseWebhook(rawBody, headers);

        if (!event.isSignatureValid()) {
            metrics.signatureFailure(key);
            log.warn("Rejected {} webhook with invalid signature", key);
            throw new PaymentException("Invalid webhook signature for " + key);
        }
        if (event.getProviderEventId() != null && recorder.isDuplicateEvent(event.getProviderEventId())) {
            log.info("Duplicate {} webhook {} ignored", key, event.getProviderEventId());
            return;
        }

        recorder.recordEvent(event);

        if (event.getOperation() == PaymentOperation.CHARGEBACK || event.getStatus() == PaymentStatus.CHARGEBACK) {
            handleDispute(key, rawBody, headers);
        } else if (event.getStatus() == PaymentStatus.PAID) {
            metrics.succeeded(key, event.getOperation());
        }
    }

    private void handleDispute(ProviderKey key, String rawBody, Map<String, String> headers) {
        if (registry.get(key) instanceof DisputeAware disputeProvider) {
            DisputeEvent dispute = disputeProvider.parseDispute(rawBody, headers);
            // Provider implementations don't stamp their own key (see DisputeEvent.provider).
            dispute.setProvider(key);
            recorder.recordDispute(dispute);
            metrics.chargeback(key);
            log.warn("Chargeback recorded for {} payment {}", key, dispute.getProviderRef());
        }
    }
}
