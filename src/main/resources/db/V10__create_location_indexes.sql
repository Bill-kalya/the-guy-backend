-- V10: Location performance indexes (no PostGIS required)
-- Uses regular PostgreSQL indexes for the Haversine-based location queries

-- Index for provider lookups
CREATE INDEX IF NOT EXISTS idx_provider_locations_provider ON provider_locations(provider_id);

-- Indexes for bounding box filtering
CREATE INDEX IF NOT EXISTS idx_provider_locations_lat ON provider_locations(latitude);
CREATE INDEX IF NOT EXISTS idx_provider_locations_lng ON provider_locations(longitude);

-- Composite index for efficient bounding box queries
CREATE INDEX IF NOT EXISTS idx_provider_locations_lat_lng ON provider_locations(latitude, longitude);

-- Index for tracking recent location updates
CREATE INDEX IF NOT EXISTS idx_provider_locations_updated ON provider_locations(updated_at DESC);

-- Add heading and speed columns if they don't exist (used for future real-time tracking)
ALTER TABLE provider_locations 
ADD COLUMN IF NOT EXISTS heading DOUBLE PRECISION;

ALTER TABLE provider_locations 
ADD COLUMN IF NOT EXISTS speed DOUBLE PRECISION;

-- Ensure coordinates are NOT NULL
ALTER TABLE provider_locations 
ALTER COLUMN latitude SET NOT NULL,
ALTER COLUMN longitude SET NOT NULL;

-- Add check constraints for valid coordinate ranges (PostgreSQL does not support IF NOT EXISTS for constraints)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_latitude_range'
    ) THEN
        ALTER TABLE provider_locations
        ADD CONSTRAINT chk_latitude_range
        CHECK (latitude >= -90 AND latitude <= 90);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_longitude_range'
    ) THEN
        ALTER TABLE provider_locations
        ADD CONSTRAINT chk_longitude_range
        CHECK (longitude >= -180 AND longitude <= 180);
    END IF;
END $$;
