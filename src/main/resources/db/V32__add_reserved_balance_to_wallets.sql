-- Add reserved_balance to wallets for withdrawal protection
-- reservedBalance holds funds during pending payout to prevent duplicate withdrawals
ALTER TABLE wallets ADD COLUMN reserved_balance DOUBLE PRECISION NOT NULL DEFAULT 0.0;
