-- V30: Add missing created_at to risk_scores
-- risk_scores was created in V17 without created_at, but RiskScore entity extends BaseEntity
-- which maps created_at. Backfills from calculated_at as the closest proxy.

ALTER TABLE risk_scores ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

UPDATE risk_scores SET created_at = calculated_at WHERE created_at IS NULL;

ALTER TABLE risk_scores ALTER COLUMN created_at SET NOT NULL;
