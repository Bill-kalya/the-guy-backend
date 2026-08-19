ALTER TABLE payments ADD COLUMN idempotency_key VARCHAR(64);
ALTER TABLE payments ADD CONSTRAINT uk_payments_idempotency UNIQUE (idempotency_key);

CREATE INDEX idx_payments_idempotency ON payments(idempotency_key);