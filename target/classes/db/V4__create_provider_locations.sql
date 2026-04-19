CREATE TABLE provider_locations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    provider_id UUID REFERENCES providers(id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_location_provider FOREIGN KEY (provider_id) REFERENCES providers(id)
);

CREATE INDEX idx_provider_location_provider ON provider_locations(provider_id);
CREATE INDEX idx_provider_location_coords ON provider_locations(latitude, longitude);

-- PostGIS extension and column will be added in V10