-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- Add geography column to provider_locations
ALTER TABLE provider_locations ADD COLUMN IF NOT EXISTS location GEOGRAPHY(Point, 4326);

-- Update existing records
UPDATE provider_locations 
SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography 
WHERE location IS NULL;

-- Create spatial index
CREATE INDEX IF NOT EXISTS idx_provider_locations_gist ON provider_locations USING GIST (location);

-- Create function to auto-update location
CREATE OR REPLACE FUNCTION update_provider_location()
RETURNS TRIGGER AS $$
BEGIN
    NEW.location = ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger
DROP TRIGGER IF EXISTS trigger_update_location ON provider_locations;
CREATE TRIGGER trigger_update_location
    BEFORE INSERT OR UPDATE OF latitude, longitude
    ON provider_locations
    FOR EACH ROW
    EXECUTE FUNCTION update_provider_location();