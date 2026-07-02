/**
 * Consumer-facing API — the REST layer for the ComfortHub customer app
 * (React Native / Expo), served under {@code /api/consumer/*}.
 *
 * <h2>Strangler intent</h2>
 * The consumer experience currently lives entirely in Bubble (pages + backend
 * workflows). This package strangles that front-end: the mobile app talks to
 * Spring Boot, and Spring proxies Bubble — the Data API for reads and the
 * <b>Workflow API</b> for writes with business logic (cart machinery, address
 * validation, Twilio phone verification). Bubble stays the source of truth and
 * keeps executing its own workflows (stock reservation, order creation,
 * payment nonces, courier orders), so the Bubble web app and this API remain
 * consistent while pieces are migrated out one by one. As logic moves into
 * Spring proper (e.g. Phase 6 payments, a future cart engine), individual
 * proxy calls here are replaced without changing the mobile contract.
 *
 * <h2>Scoping</h2>
 * Unlike the backoffice controllers (scoped by <i>company</i>), every endpoint
 * here is scoped by the authenticated <i>user</i>: the Auth0 JWT {@code sub}
 * is resolved to the live Bubble user record via the Data API
 * ({@code user."Auth - Auth0 - sub"}), and reads/writes are constrained to
 * that user's cart, orders, addresses and profile. See
 * {@link com.comforthub.backoffice.consumer.ConsumerUserService}.
 *
 * <h2>Unverified Bubble wire shapes</h2>
 * Several workflow parameter sets are documented from the Bubble editor but
 * not yet exercised against {@code version-test}. Those call sites are marked
 * {@code TODO(verify vs version-test)} — the same convention Phase 6 used for
 * provider sandbox gaps. None of them fall back silently: a mismatch surfaces
 * as a Bubble error propagated to the caller.
 */
package com.comforthub.backoffice.consumer;
