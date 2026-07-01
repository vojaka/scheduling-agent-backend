# Payment integrations (Montonio + EveryPay)

Server-side payment layer for the ComfortHub backend. Tracking epic: **issue #79**.

## Design

Operation-oriented abstraction. Payment *operations* are first-class; providers
implement the **capabilities** they support, resolved via `PaymentProviderRegistry`.

| Capability interface | Operation(s) |
|---|---|
| `OneOffPayments` | one-off charge |
| `RecurringPayments` | `initRecurring` (CIT), `chargeRecurring` (MIT), `revokeToken` |
| `RefundablePayments` | refund (full/partial) |
| `DisputeAware` | inbound chargeback/dispute |
| `WebhookHandling` | inbound payment events |

`PaymentService` orchestrates each operation; `PaymentController` (`/api/payments`)
and `PaymentWebhookController` (`/webhooks/{provider}`) are the HTTP surface.

## No stored card data

Both providers use hosted payment pages + provider-side tokenization. The backend
never sees or stores a PAN. Recurring uses provider-issued tokens only
(`token_ref` + masked display PAN).

## Persistence seam — deferred on purpose

`PaymentRecorder` (package `payment.spi`) is the single persistence port. Until the
domain entities land (**issue #83**), `LoggingPaymentRecorder` is wired as a no-op
default via `@ConditionalOnMissingBean`. Provide a `@Component` implementing
`PaymentRecorder` (JPA + Flyway + Bubble order write-back) and it takes over
automatically — no changes needed in the service or providers.

## Observability

`PaymentMetrics` emits Micrometer counters/timers (initiated, succeeded, failed,
refunds, chargebacks, webhook signature failures, provider latency) — scraped by
the existing Prometheus + Grafana stack; errors go to Sentry.

## Config

See `payments.*` in `application.properties` and the `.env.example` keys. Sandbox
base URLs are the defaults.

## ⚠️ Before go-live

Exact provider request/response envelopes (Montonio `data`-wrapped JWT + refund
path; EveryPay refund endpoint + callback fields) must be verified against each
provider's **sandbox**. Signing/verification logic is stable; wire shapes are
marked `TODO(#81)` / `TODO(#82)`.
