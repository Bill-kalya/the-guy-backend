CREATE TABLE jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID REFERENCES users(id) NOT NULL,
    provider_id UUID REFERENCES providers(id),
    service_category VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) CHECK (status IN ('REQUESTED', 'MATCHING', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')) NOT NULL,
    urgency VARCHAR(10) CHECK (urgency IN ('INSTANT', 'SCHEDULED')) NOT NULL,
    scheduled_time TIMESTAMP,
    price_estimate_min NUMERIC(10,2),
    price_estimate_max NUMERIC(10,2),
    provider_proposed_price NUMERIC(10,2),
    final_price NUMERIC(10,2),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP,
    completed_at TIMESTAMP,
    version INTEGER DEFAULT 0,
    
    CONSTRAINT fk_job_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_job_provider FOREIGN KEY (provider_id) REFERENCES providers(id),
    CONSTRAINT chk_price_range CHECK (price_estimate_min <= price_estimate_max)
);

CREATE INDEX idx_jobs_customer ON jobs(customer_id);
CREATE INDEX idx_jobs_provider ON jobs(provider_id);
CREATE INDEX idx_jobs_status ON jobs(status);
CREATE INDEX idx_jobs_created ON jobs(created_at);
CREATE INDEX idx_jobs_location ON jobs(latitude, longitude);