CREATE TABLE providers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    bio TEXT,
    profile_image_url TEXT,
    verification_level VARCHAR(20) CHECK (verification_level IN ('NONE', 'BASIC', 'ID_VERIFIED', 'BUSINESS')) DEFAULT 'NONE',
    is_online BOOLEAN DEFAULT FALSE,
    last_active_at TIMESTAMP,
    rating_avg NUMERIC(3,2) DEFAULT 0,
    total_reviews INT DEFAULT 0,
    jobs_completed INT DEFAULT 0,
    jobs_cancelled INT DEFAULT 0,
    response_rate NUMERIC(5,4) DEFAULT 1.0,
    repeat_clients_percentage NUMERIC(5,2) DEFAULT 0,
    dynamic_price_multiplier NUMERIC(4,2) DEFAULT 1.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_provider_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_rating_range CHECK (rating_avg >= 0 AND rating_avg <= 5)
);

CREATE INDEX idx_providers_user ON providers(user_id);
CREATE INDEX idx_providers_online ON providers(is_online);
CREATE INDEX idx_providers_rating ON providers(rating_avg DESC);
CREATE INDEX idx_providers_verification ON providers(verification_level);