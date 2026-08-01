ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS completion_notes TEXT,
    ADD COLUMN IF NOT EXISTS completion_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS completion_longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS confirmation_deadline TIMESTAMP;

CREATE TABLE IF NOT EXISTS job_completion_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    photo_url TEXT NOT NULL
);