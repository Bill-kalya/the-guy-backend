-- V40: Call-out fee for services
-- Providers set a call-out/inspection fee separate from the job's final quote.
ALTER TABLE services ADD COLUMN IF NOT EXISTS call_out_fee NUMERIC(10,2);
