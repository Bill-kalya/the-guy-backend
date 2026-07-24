-- V29: Add Google OAuth columns to users table
-- Supports Google Sign-In account linking and auth provider tracking

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS google_id VARCHAR(255) UNIQUE,
    ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(20) DEFAULT 'LOCAL',
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

-- Index on google_id for fast lookups during OAuth flow
CREATE INDEX IF NOT EXISTS idx_users_google_id ON users (google_id) WHERE google_id IS NOT NULL;

-- Index on auth_provider for analytics
CREATE INDEX IF NOT EXISTS idx_users_auth_provider ON users (auth_provider);
