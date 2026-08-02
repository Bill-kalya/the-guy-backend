-- V37: Add missing public_id columns to provider media tables
-- The version of V24 applied to production did not include public_id;
-- the column was added to V24 later, so production is missing it.

ALTER TABLE portfolio_images ADD COLUMN IF NOT EXISTS public_id VARCHAR(255);

ALTER TABLE verification_documents ADD COLUMN IF NOT EXISTS public_id VARCHAR(255);
