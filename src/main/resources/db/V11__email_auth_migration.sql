-- Migration to email-first authentication
-- Drops phone_number requirement, makes email the primary identifier

-- Drop the old phone index
DROP INDEX IF EXISTS idx_users_phone;

-- Make phone_number nullable (it's no longer required)
ALTER TABLE users ALTER COLUMN phone_number DROP NOT NULL;

-- Make email NOT NULL (primary identifier now)
ALTER TABLE users ALTER COLUMN email SET NOT NULL;

-- Add verification token column for email verification
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_token TEXT UNIQUE;

-- Add index on email for faster lookups
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Add index on verification_token for faster verification lookups
CREATE INDEX IF NOT EXISTS idx_users_verification_token ON users(verification_token);