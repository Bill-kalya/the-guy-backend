CREATE TABLE matching_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID REFERENCES jobs(id) NOT NULL,
    provider_id UUID REFERENCES providers(id) NOT NULL,
    match_score NUMERIC(10,6),
    distance_score NUMERIC(10,6),
    reputation_score NUMERIC(10,6),
    price_score NUMERIC(10,6),
    availability_score NUMERIC(10,6),
    responsiveness_score NUMERIC(10,6),
    demand_boost_score NUMERIC(10,6),
    was_selected BOOLEAN DEFAULT FALSE,
    rank_position INTEGER,
    algorithm_version VARCHAR(20),
    response_time_ms BIGINT,
    debug_data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_match_job FOREIGN KEY (job_id) REFERENCES jobs(id),
    CONSTRAINT fk_match_provider FOREIGN KEY (provider_id) REFERENCES providers(id)
);

CREATE INDEX idx_matching_logs_job ON matching_logs(job_id);
CREATE INDEX idx_matching_logs_provider ON matching_logs(provider_id);
CREATE INDEX idx_matching_logs_created ON matching_logs(created_at);