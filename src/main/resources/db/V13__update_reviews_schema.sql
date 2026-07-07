-- Migration to update reviews table for SQS (Service Quality Score) system

-- First, drop the old rating columns
ALTER TABLE reviews DROP COLUMN IF EXISTS rating_quality;
ALTER TABLE reviews DROP COLUMN IF EXISTS rating_reliability;
ALTER TABLE reviews DROP COLUMN IF EXISTS rating_communication;

-- Add new SQS columns
ALTER TABLE reviews ADD COLUMN overall_experience INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reviews ADD COLUMN timeliness INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reviews ADD COLUMN professionalism INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reviews ADD COLUMN communication INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reviews ADD COLUMN courtesy INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reviews ADD COLUMN work_quality INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reviews ADD COLUMN attention_to_detail INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reviews ADD COLUMN cleanliness INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reviews ADD COLUMN reliability INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reviews ADD COLUMN value_for_money INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reviews ADD COLUMN problem_resolution INTEGER;
ALTER TABLE reviews ADD COLUMN recommendation INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reviews ADD COLUMN service_quality_score DOUBLE PRECISION NOT NULL DEFAULT 0;

-- Add constraints to ensure scores are between 0 and 100
ALTER TABLE reviews ADD CONSTRAINT check_overall_experience CHECK (overall_experience BETWEEN 0 AND 100);
ALTER TABLE reviews ADD CONSTRAINT check_timeliness CHECK (timeliness BETWEEN 0 AND 100);
ALTER TABLE reviews ADD CONSTRAINT check_professionalism CHECK (professionalism BETWEEN 0 AND 100);
ALTER TABLE reviews ADD CONSTRAINT check_communication CHECK (communication BETWEEN 0 AND 100);
ALTER TABLE reviews ADD CONSTRAINT check_courtesy CHECK (courtesy BETWEEN 0 AND 100);
ALTER TABLE reviews ADD CONSTRAINT check_work_quality CHECK (work_quality BETWEEN 0 AND 100);
ALTER TABLE reviews ADD CONSTRAINT check_attention_to_detail CHECK (attention_to_detail BETWEEN 0 AND 100);
ALTER TABLE reviews ADD CONSTRAINT check_cleanliness CHECK (cleanliness BETWEEN 0 AND 100);
ALTER TABLE reviews ADD CONSTRAINT check_reliability CHECK (reliability BETWEEN 0 AND 100);
ALTER TABLE reviews ADD CONSTRAINT check_value_for_money CHECK (value_for_money BETWEEN 0 AND 100);
ALTER TABLE reviews ADD CONSTRAINT check_problem_resolution CHECK (problem_resolution IS NULL OR (problem_resolution BETWEEN 0 AND 100));
ALTER TABLE reviews ADD CONSTRAINT check_recommendation CHECK (recommendation BETWEEN 0 AND 100);
ALTER TABLE reviews ADD CONSTRAINT check_sqs CHECK (service_quality_score BETWEEN 0 AND 100);

-- Make comment nullable since it's optional
ALTER TABLE reviews ALTER COLUMN comment DROP NOT NULL;

-- Drop old indexes
DROP INDEX IF EXISTS idx_reviews_provider;
DROP INDEX IF EXISTS idx_reviews_customer;
DROP INDEX IF EXISTS idx_reviews_created;

-- Recreate indexes
CREATE INDEX idx_reviews_provider ON reviews(provider_id);
CREATE INDEX idx_reviews_customer ON reviews(customer_id);
CREATE INDEX idx_reviews_created ON reviews(created_at);
CREATE INDEX idx_reviews_sqs ON reviews(service_quality_score);