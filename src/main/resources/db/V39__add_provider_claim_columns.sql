ALTER TABLE providers ADD COLUMN account_claimed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE providers ADD COLUMN claim_code VARCHAR(32);
ALTER TABLE providers ADD COLUMN claim_code_expires_at TIMESTAMP;

CREATE INDEX idx_providers_account_claimed ON providers(account_claimed);
