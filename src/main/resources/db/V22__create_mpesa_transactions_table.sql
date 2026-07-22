-- V22: Create mpesa_transactions table for payment gateway abstraction
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
