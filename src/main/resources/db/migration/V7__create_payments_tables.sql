CREATE TABLE payments (
    id UUID PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    provider_ref VARCHAR(255) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    company_id VARCHAR(255) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    method VARCHAR(50),
    token_ref VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_provider_ref ON payments(provider_ref);
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_company_id ON payments(company_id);

CREATE TABLE payment_events (
    id UUID PRIMARY KEY,
    payment_id UUID REFERENCES payments(id) ON DELETE CASCADE,
    provider_event_id VARCHAR(255) UNIQUE NOT NULL,
    event_name VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    raw_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE payment_tokens (
    id UUID PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    company_id VARCHAR(255) NOT NULL,
    customer_ref VARCHAR(255) NOT NULL,
    token_ref VARCHAR(255) UNIQUE NOT NULL,
    agreement_type VARCHAR(50) NOT NULL,
    card_brand VARCHAR(50),
    masked_pan VARCHAR(50),
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_tokens_token_ref ON payment_tokens(token_ref);

CREATE TABLE disputes (
    id UUID PRIMARY KEY,
    payment_id UUID REFERENCES payments(id) ON DELETE CASCADE,
    provider_ref VARCHAR(255) NOT NULL,
    provider_dispute_ref VARCHAR(255) UNIQUE NOT NULL,
    reason TEXT,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
