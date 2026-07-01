package com.comforthub.backoffice.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/** Micrometer instrumentation for payments (scraped by Prometheus, shown in Grafana). */
@Component
public class PaymentMetrics {

    private final MeterRegistry registry;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void initiated(ProviderKey provider, PaymentOperation op, String method) {
        Counter.builder("payments_initiated_total")
                .tag("provider", provider.name())
                .tag("operation", op.name())
                .tag("method", method == null ? "default" : method)
                .register(registry).increment();
    }

    public void succeeded(ProviderKey provider, PaymentOperation op) {
        Counter.builder("payments_succeeded_total")
                .tag("provider", provider.name())
                .tag("operation", op.name())
                .register(registry).increment();
    }

    public void failed(ProviderKey provider, PaymentOperation op, String reason) {
        Counter.builder("payments_failed_total")
                .tag("provider", provider.name())
                .tag("operation", op.name())
                .tag("reason", reason)
                .register(registry).increment();
    }

    public void refund(ProviderKey provider) {
        Counter.builder("payments_refunds_total")
                .tag("provider", provider.name())
                .register(registry).increment();
    }

    public void chargeback(ProviderKey provider) {
        Counter.builder("payments_chargebacks_total")
                .tag("provider", provider.name())
                .register(registry).increment();
    }

    public void webhookReceived(ProviderKey provider) {
        Counter.builder("payment_webhooks_received_total")
                .tag("provider", provider.name())
                .register(registry).increment();
    }

    public void signatureFailure(ProviderKey provider) {
        Counter.builder("payment_webhook_signature_failures_total")
                .tag("provider", provider.name())
                .register(registry).increment();
    }

    public void recurringCharge(ProviderKey provider, boolean success) {
        Counter.builder("payment_recurring_charges_total")
                .tag("provider", provider.name())
                .tag("outcome", success ? "success" : "failure")
                .register(registry).increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopProviderTimer(Timer.Sample sample, ProviderKey provider, String endpoint, String outcome) {
        sample.stop(Timer.builder("payment_provider_request_seconds")
                .tag("provider", provider.name())
                .tag("endpoint", endpoint)
                .tag("outcome", outcome)
                .register(registry));
    }
}
