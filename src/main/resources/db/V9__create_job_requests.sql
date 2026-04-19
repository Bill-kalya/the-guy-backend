CREATE TABLE job_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID REFERENCES jobs(id) NOT NULL,
    provider_id UUID REFERENCES providers(id) NOT NULL,
    status VARCHAR(20) CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED')) NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    responded_at TIMESTAMP,
    proposed_price NUMERIC(10,2),
    decline_reason TEXT,
    retry_number INTEGER DEFAULT 0,
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_request_job FOREIGN KEY (job_id) REFERENCES jobs(id),
    CONSTRAINT fk_request_provider FOREIGN KEY (provider_id) REFERENCES providers(id)
);

CREATE INDEX idx_job_requests_job ON job_requests(job_id);
CREATE INDEX idx_job_requests_provider ON job_requests(provider_id);
CREATE INDEX idx_job_requests_status ON job_requests(status);
CREATE INDEX idx_job_requests_sent ON job_requests(sent_at);