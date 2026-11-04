-- PayFlow Initial Database Schema Migration
-- Migration V1: Core Tables, Constraints, and Indexes

CREATE TABLE merchants (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE merchant_users (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_merchant_user UNIQUE (merchant_id, user_id)
);

CREATE TABLE api_credentials (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    key_prefix VARCHAR(32) NOT NULL,
    secret_hash VARCHAR(255) NOT NULL,
    scopes VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE providers (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    adapter_type VARCHAR(50) NOT NULL,
    capabilities_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE merchant_provider_configs (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    provider_id UUID NOT NULL REFERENCES providers(id),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL DEFAULT 1 CHECK (priority >= 1 AND priority <= 100),
    weight INT NOT NULL DEFAULT 100 CHECK (weight >= 0 AND weight <= 100),
    encrypted_config_reference VARCHAR(255),
    CONSTRAINT uq_merchant_provider UNIQUE (merchant_id, provider_id)
);

CREATE TABLE routing_rules (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    name VARCHAR(100) NOT NULL,
    strategy VARCHAR(50) NOT NULL,
    conditions_json TEXT NOT NULL,
    priority INT NOT NULL DEFAULT 1 CHECK (priority >= 1 AND priority <= 1000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    order_id VARCHAR(100) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    provider_id UUID NOT NULL REFERENCES providers(id),
    attempt_number INT NOT NULL,
    provider_reference VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    failure_code VARCHAR(100),
    failure_message_safe VARCHAR(500),
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_payment_attempt UNIQUE (payment_id, attempt_number)
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    operation VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    resource_id UUID,
    response_status INT NOT NULL,
    response_body_safe TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_idempotency UNIQUE (merchant_id, operation, idempotency_key)
);

CREATE TABLE webhook_events (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES providers(id),
    provider_event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    signature_valid BOOLEAN NOT NULL DEFAULT FALSE,
    payload_redacted TEXT NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    processing_status VARCHAR(50) NOT NULL,
    CONSTRAINT uq_webhook_event UNIQUE (provider_id, provider_event_id)
);

CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    provider_refund_reference VARCHAR(255),
    reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    publish_attempts INT NOT NULL DEFAULT 0
);

CREATE TABLE reconciliation_cases (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    attempt_id UUID REFERENCES payment_attempts(id),
    expected_status VARCHAR(50) NOT NULL,
    provider_status VARCHAR(50),
    case_status VARCHAR(50) NOT NULL,
    resolution VARCHAR(50),
    resolution_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    merchant_id UUID REFERENCES merchants(id),
    actor_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    metadata_redacted TEXT,
    correlation_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Essential Performance & Operational Indexes
CREATE INDEX idx_payments_merchant_created ON payments(merchant_id, created_at);
CREATE INDEX idx_payments_merchant_status_created ON payments(merchant_id, status, created_at);
CREATE INDEX idx_payments_order ON payments(merchant_id, order_id);
CREATE INDEX idx_payment_attempts_payment ON payment_attempts(payment_id);
CREATE INDEX idx_payment_attempts_provider_ref ON payment_attempts(provider_reference);
CREATE INDEX idx_refunds_payment_status ON refunds(payment_id, status);
CREATE INDEX idx_refunds_merchant_created ON refunds(merchant_id, created_at);
CREATE INDEX idx_outbox_unpublished ON outbox_events(published_at, occurred_at);
CREATE INDEX idx_reconciliation_status ON reconciliation_cases(case_status, created_at);
CREATE INDEX idx_audit_logs_merchant_created ON audit_logs(merchant_id, created_at);

