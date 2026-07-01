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
company get `403`.

`/webhooks/{provider}` is public (it sits outside `/api/**`) and is authenticated
by provider signature inside the provider implementation instead; a permanent
failure such as a bad signature returns `400` so the provider stops retrying.

## Persistence — operational Postgres domain (issue #83)

`PaymentRecorder` (package `payment.spi`) is the single persistence port.
`JpaPaymentRecorder` is the real implementation — `payments`, `payment_events`,
`payment_tokens` and `disputes` (Flyway `V7__create_payments_tables.sql`, extended
by `V10__complete_payments_domain.sql`) are a **new operational domain**, separate
from the `bubble_*` analytics mirror ETL feeds for Metabase. It's wired in
automatically via `@Primary` — `LoggingPaymentRecorder` only takes over if no real
`PaymentRecorder` bean is present (see `PaymentRecorderConfig`'s
`@ConditionalOnMissingBean`), which in practice means it's now dead code on `main`
since `JpaPaymentRecorder` is always registered.

Refunds and chargebacks are linked back to the original charge via
`payments.parent_payment_id` / `disputes.payment_id`, resolved through the
provider reference each operation carries. On a terminal status transition
(webhook-driven), `JpaPaymentRecorder` writes the order's `S - Order Payment
Status` back to Bubble via `BubbleClient.update("order", orderId, ...)` — Bubble
stays the system of record for the order. The exact non-"Paid" option-set string
values (`Cancelled` / `Refunded` / `Partial` / `Disputed`) are **not yet verified**
against a live Bubble sandbox order — see the `TODO(#84/#87)` note in
`JpaPaymentRecorder`.

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

Dispute/chargeback payload parsing is still a stub for both providers
(`MontonioPaymentProvider.parseDispute` / `EveryPayPaymentProvider.parseDispute`
return near-empty `DisputeEvent`s — no real `providerDisputeRef`/amount/currency
extraction yet). That's tracked under **#84**; #83 only delivered the
schema/entity/linkage this depends on once #84 lands real parsing.
