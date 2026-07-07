-- Migration to create provider_statistics table for SQS caching

CREATE TABLE provider_statistics (
    provider_id UUID PRIMARY KEY REFERENCES providers(id) ON DELETE CASCADE,
    
    sqs DOUBLE PRECISION DEFAULT 0,
    
    professionalism_score DOUBLE PRECISION DEFAULT 0,
    communication_score DOUBLE PRECISION DEFAULT 0,
    timeliness_score DOUBLE PRECISION DEFAULT 0,
    work_quality_score DOUBLE PRECISION DEFAULT 0,
    value_score DOUBLE PRECISION DEFAULT 0,
    reliability_score DOUBLE PRECISION DEFAULT 0,
    courtesy_score DOUBLE PRECISION DEFAULT 0,
    
    review_count INTEGER DEFAULT 0,
    
    updated_at TIMESTAMP DEFAULT NOW(),
    
    CONSTRAINT fk_provider_statistics_provider FOREIGN KEY (provider_id) REFERENCES providers(id)
);

CREATE INDEX idx_provider_statistics_sqs ON provider_statistics(sqs DESC);