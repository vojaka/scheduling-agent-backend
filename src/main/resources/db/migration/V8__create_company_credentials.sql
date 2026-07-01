CREATE TABLE company_credentials (
    id UUID PRIMARY KEY,
    company_id VARCHAR(255) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    key_name VARCHAR(100) NOT NULL,
    encrypted_value TEXT NOT NULL,
    nonce VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_company_provider_key UNIQUE (company_id, provider, key_name)
);

CREATE INDEX idx_company_credentials_company ON company_credentials(company_id);
