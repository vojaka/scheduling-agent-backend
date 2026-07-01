-- Completes the operational payments domain for issue #83.
--
-- Context: V7__create_payments_tables.sql (payments/payment_events/payment_tokens/
-- disputes) and V8__create_company_credentials.sql already landed directly on
-- main outside the normal branch/PR flow before this migration was written (see
-- the PR body for this change for the verification note). V7's tables were a
-- reasonable first pass but did not carry every column issue #83 specifies.
-- This migration brings all four tables up to the issue #83 spec without
-- dropping any data.

-- payments: parent/child linkage (refunds & chargebacks -> the original
-- charge) and the recurring flag.
ALTER TABLE payments
    ADD COLUMN is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN parent_payment_id UUID NULL REFERENCES payments(id);

CREATE INDEX idx_payments_parent_payment_id ON payments(parent_payment_id);

-- payment_events: rename the ad-hoc event_name/created_at columns to match
-- the issue's audit-log spec (type / received_at), add operation +
-- signature_valid, and switch raw_payload to jsonb so it's queryable.
-- Safe to cast in place: this domain hasn't taken live traffic yet (Phase 6
-- is still in progress), so raw_payload is expected to be empty or,
-- everywhere it's non-empty, already-valid JSON (both providers' webhook
-- parsers require valid JSON input before they ever reach the recorder).
ALTER TABLE payment_events RENAME COLUMN event_name TO type;
ALTER TABLE payment_events RENAME COLUMN created_at TO received_at;
ALTER TABLE payment_events ADD COLUMN operation VARCHAR(50);
ALTER TABLE payment_events ADD COLUMN signature_valid BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE payment_events
    ALTER COLUMN raw_payload TYPE JSONB USING (
        CASE WHEN raw_payload IS NULL OR raw_payload = '' THEN NULL
             ELSE raw_payload::jsonb END
    );

-- payment_tokens: expiry (populated once #85's real token issuance lands;
-- nullable for now as the issue spec calls for).
ALTER TABLE payment_tokens ADD COLUMN expires_at TIMESTAMPTZ NULL;

-- disputes: bring up to the issue's spec — provider, a real status
-- lifecycle (OPEN/WON/LOST/CANCELLED), a response deadline, a raw payload
-- audit trail, and updated_at. Existing rows (there should be none yet)
-- default to OPEN.
ALTER TABLE disputes ADD COLUMN provider VARCHAR(50);
ALTER TABLE disputes ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'OPEN';
ALTER TABLE disputes ADD COLUMN respond_by TIMESTAMPTZ NULL;
ALTER TABLE disputes ADD COLUMN raw_payload JSONB NULL;
ALTER TABLE disputes ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
