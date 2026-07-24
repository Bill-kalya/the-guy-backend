-- V26: Trust & Safety support
-- Adds city to provider_locations for risk heat map, adds moderation_cases count queries support

ALTER TABLE provider_locations ADD COLUMN IF NOT EXISTS city VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_provider_location_city ON provider_locations(city);

-- Add provider_status column for suspend/ban tracking
ALTER TABLE providers ADD COLUMN IF NOT EXISTS provider_status VARCHAR(20) DEFAULT 'ACTIVE';
UPDATE providers SET provider_status = 'ACTIVE' WHERE provider_status IS NULL;
CREATE INDEX IF NOT EXISTS idx_providers_status ON providers(provider_status);
