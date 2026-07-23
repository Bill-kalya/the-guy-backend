-- V24: One Provider = One Service
-- Adds category_id to providers, creates portfolio_images and verification_documents tables

-- 1. Add category_id to providers
ALTER TABLE providers ADD COLUMN IF NOT EXISTS category_id VARCHAR(255);

-- 2. Backfill category_id from existing services (first active service per provider)
UPDATE providers p
SET category_id = (
    SELECT s.category
    FROM services s
    WHERE s.provider_id = p.id AND s.is_active = true
    ORDER BY s.created_at ASC
    LIMIT 1
)
WHERE p.category_id IS NULL;

-- 3. Create portfolio_images table
CREATE TABLE IF NOT EXISTS portfolio_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_portfolio_provider ON portfolio_images(provider_id);

-- 4. Create verification_documents table
CREATE TABLE IF NOT EXISTS verification_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    document_type VARCHAR(50) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    rejection_reason TEXT,
    reviewed_at TIMESTAMP,
    reviewed_by UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_verification_provider ON verification_documents(provider_id);

-- 5. Add index on category_id for fast lookups
CREATE INDEX IF NOT EXISTS idx_providers_category ON providers(category_id);
