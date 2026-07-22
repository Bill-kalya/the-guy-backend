-- ============================================================
-- V23: Financial tables (re-creates anything V21/V22 missed)
-- All statements use IF NOT EXISTS for safety
-- ============================================================

-- Price snapshot at booking time (immutable)
CREATE TABLE IF NOT EXISTS booking_price_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID REFERENCES jobs(id) NOT NULL UNIQUE,
    service_category VARCHAR(50) NOT NULL,
    service_price NUMERIC(10,2) NOT NULL,
    platform_fee NUMERIC(10,2) NOT NULL,
    tax_amount NUMERIC(10,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(10,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bps_job ON booking_price_snapshots(job_id);

-- Full payment records (ledger-compatible, separate from V7 payments)
CREATE TABLE IF NOT EXISTS payment_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID REFERENCES jobs(id) NOT NULL,
    customer_id UUID REFERENCES users(id) NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    provider_reference VARCHAR(200),
    payment_method VARCHAR(20) NOT NULL,
    transaction_code VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    paid_at TIMESTAMP,
    version INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pr_job ON payment_records(job_id);
CREATE INDEX IF NOT EXISTS idx_pr_customer ON payment_records(customer_id);
CREATE INDEX IF NOT EXISTS idx_pr_status ON payment_records(status);

-- Provider wallets
CREATE TABLE IF NOT EXISTS wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID REFERENCES providers(id) NOT NULL UNIQUE,
    pending_balance NUMERIC(12,2) NOT NULL DEFAULT 0,
    available_balance NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    version INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wallets_provider ON wallets(provider_id);

-- Wallet transaction history (immutable append-only)
CREATE TABLE IF NOT EXISTS wallet_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID REFERENCES wallets(id) NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    type VARCHAR(10) NOT NULL,
    reference_type VARCHAR(20) NOT NULL,
    reference_id UUID NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wt_wallet ON wallet_transactions(wallet_id);
CREATE INDEX IF NOT EXISTS idx_wt_reference ON wallet_transactions(reference_type, reference_id);

-- Double-entry ledger (the accounting backbone)
CREATE TABLE IF NOT EXISTS ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_code VARCHAR(30) NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    reference_type VARCHAR(30) NOT NULL,
    reference_id UUID NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_le_account ON ledger_entries(account_code);
CREATE INDEX IF NOT EXISTS idx_le_reference ON ledger_entries(reference_type, reference_id);
CREATE INDEX IF NOT EXISTS idx_le_created ON ledger_entries(created_at);

-- Provider payout requests
CREATE TABLE IF NOT EXISTS payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID REFERENCES providers(id) NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transaction_reference VARCHAR(200),
    processed_at TIMESTAMP,
    version INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payouts_provider ON payouts(provider_id);
CREATE INDEX IF NOT EXISTS idx_payouts_status ON payouts(status);

-- Tax records (KRA compliance)
CREATE TABLE IF NOT EXISTS tax_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID REFERENCES jobs(id) NOT NULL UNIQUE,
    tax_type VARCHAR(20) NOT NULL DEFAULT 'VAT',
    tax_rate NUMERIC(5,2) NOT NULL,
    tax_amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tax_records_job ON tax_records(job_id);

-- Financial audit log (immutable, every money action)
CREATE TABLE IF NOT EXISTS financial_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    action VARCHAR(40) NOT NULL,
    entity_type VARCHAR(30) NOT NULL,
    entity_id UUID NOT NULL,
    metadata JSONB,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fal_actor ON financial_audit_logs(actor_id, actor_type);
CREATE INDEX IF NOT EXISTS idx_fal_action ON financial_audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_fal_entity ON financial_audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_fal_created ON financial_audit_logs(created_at);

-- Extend existing disputes table (V17) with financial fields
ALTER TABLE disputes ADD COLUMN IF NOT EXISTS refund_amount NUMERIC(10,2);
ALTER TABLE disputes ADD COLUMN IF NOT EXISTS provider_penalty NUMERIC(10,2);
ALTER TABLE disputes ADD COLUMN IF NOT EXISTS opened_by_id UUID REFERENCES users(id);
ALTER TABLE disputes ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;

-- M-Pesa transaction records (from V22)
CREATE TABLE IF NOT EXISTS mpesa_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checkout_request_id VARCHAR(255),
    merchant_request_id VARCHAR(255),
    mpesa_receipt_number VARCHAR(255),
    phone_number VARCHAR(50),
    amount DOUBLE PRECISION,
    reference VARCHAR(255),
    result_code INTEGER,
    result_desc TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'INITIATED',
    callback_payload TEXT,
    transaction_date VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mpesa_checkout ON mpesa_transactions(checkout_request_id);
CREATE INDEX IF NOT EXISTS idx_mpesa_receipt ON mpesa_transactions(mpesa_receipt_number);
CREATE INDEX IF NOT EXISTS idx_mpesa_status ON mpesa_transactions(status);
