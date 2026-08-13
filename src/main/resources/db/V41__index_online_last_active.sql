-- Speed up the stale-heartbeat sweep and online-now counts.
-- Providers are considered online only while is_online = true AND
-- last_active_at is recent; this composite index serves both lookups.
CREATE INDEX IF NOT EXISTS idx_providers_online_last_active
    ON providers (is_online, last_active_at);
