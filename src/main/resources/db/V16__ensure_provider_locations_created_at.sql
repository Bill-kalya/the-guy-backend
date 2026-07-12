-- Ensure provider_locations table has created_at column expected by BaseEntity (createdAt).
-- Fixes runtime error:
-- "The column name created_at was not found in this ResultSet."

ALTER TABLE provider_locations
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

