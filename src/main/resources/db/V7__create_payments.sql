CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID REFERENCES jobs(id) NOT NULL,
    customer_id UUID REFERENCES users(id) NOT NULL,
    provider_id UUID REFERENCES providers(id) NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) CHECK (status IN ('PENDING', 'HELD', 'RELEASED', 'REFUNDED', 'FAILED')) NOT NULL,
    payment_method VARCHAR(10) CHECK (payment_method IN ('MPESA', 'CARD', 'CASH')) NOT NULL,
    transaction_reference VARCHAR(100) UNIQUE,
    mpesa_receipt_number VARCHAR(50),
    checkout_request_id VARCHAR(100),
    metadata TEXT,
    paid_at TIMESTAMP,
    released_at TIMESTAMP,
    refunded_at TIMESTAMP,
    failure_reason TEXT,
    retry_count INTEGER DEFAULT 0,
    version INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_payment_job FOREIGN KEY (job_id) REFERENCES jobs(id),
    CONSTRAINT fk_payment_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_payment_provider FOREIGN KEY (provider_id) REFERENCES providers(id),
    CONSTRAINT chk_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_payments_job ON payments(job_id);
CREATE INDEX idx_payments_customer ON payments(customer_id);
CREATE INDEX idx_payments_provider ON payments(provider_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_transaction ON payments(transaction_reference);