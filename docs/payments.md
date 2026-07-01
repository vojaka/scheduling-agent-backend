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

## Auth & company scoping

`/api/payments/**` requires the Auth0 JWT (the same resource-server chain as the
rest of `/api/**`). The caller's company is resolved from the JWT principal via
`CurrentUserService` and stamped onto each request **server-side** — a
client-supplied `companyId` in the body is ignored. Callers with no resolvable
company get `403`. (Ownership of a referenced payment on refund is enforced once
persistence lands — #83.)

`/webhooks/{provider}` is public (it sits outside `/api/**`) and is authenticated
by provider signature inside the provider implementation instead; a permanent
failure such as a bad signature returns `400` so the provider stops retrying.

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
