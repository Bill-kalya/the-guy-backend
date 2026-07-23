-- V25: Media system improvements
-- Adds moderation_status to portfolio_images, DELETED status for verification_documents

-- 1. Add moderation_status to portfolio_images
ALTER TABLE portfolio_images ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(30) DEFAULT 'PENDING_REVIEW';

-- Backfill existing portfolio images as APPROVED (they were uploaded before moderation)
UPDATE portfolio_images SET moderation_status = 'APPROVED' WHERE moderation_status IS NULL;

-- 2. Ensure sort_order defaults are correct
ALTER TABLE portfolio_images ALTER COLUMN sort_order SET DEFAULT 0;
ALTER TABLE portfolio_images ALTER COLUMN sort_order SET NOT NULL;
