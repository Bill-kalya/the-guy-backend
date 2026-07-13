-- Migration to create provider_locations table for nearby provider discovery
-- No PostGIS dependency - uses regular lat/lng columns with Haversine formula

CREATE TABLE IF NOT EXISTS provider_locations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    provider_id UUID NOT NULL UNIQUE REFERENCES providers(id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude >= -90 AND latitude <= 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude >= -180 AND longitude <= 180),
    heading DOUBLE PRECISION,
    speed DOUBLE PRECISION,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for bounding box queries
CREATE INDEX IF NOT EXISTS idx_provider_locations_provider ON provider_locations(provider_id);
CREATE INDEX IF NOT EXISTS idx_provider_locations_lat ON provider_locations(latitude);
CREATE INDEX IF NOT EXISTS idx_provider_locations_lng ON provider_locations(longitude);

-- Composite index for the bounding box + online filter
CREATE INDEX IF NOT EXISTS idx_provider_locations_lat_lng ON provider_locations(latitude, longitude);