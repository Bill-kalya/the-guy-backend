CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID UNIQUE REFERENCES jobs(id) NOT NULL,
    customer_id UUID REFERENCES users(id) NOT NULL,
    provider_id UUID REFERENCES providers(id) NOT NULL,
    rating_quality INTEGER CHECK (rating_quality BETWEEN 1 AND 5) NOT NULL,
    rating_reliability INTEGER CHECK (rating_reliability BETWEEN 1 AND 5) NOT NULL,
    rating_communication INTEGER CHECK (rating_communication BETWEEN 1 AND 5) NOT NULL,
    comment TEXT NOT NULL,
    is_verified_purchase BOOLEAN DEFAULT TRUE,
    is_helpful BOOLEAN DEFAULT FALSE,
    helpful_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_review_job FOREIGN KEY (job_id) REFERENCES jobs(id),
    CONSTRAINT fk_review_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_review_provider FOREIGN KEY (provider_id) REFERENCES providers(id)
);

CREATE INDEX idx_reviews_provider ON reviews(provider_id);
CREATE INDEX idx_reviews_customer ON reviews(customer_id);
CREATE INDEX idx_reviews_created ON reviews(created_at);