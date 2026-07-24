-- V28: Provider dashboard overhaul tables
-- Adds performance metrics, goals, achievements, reputation, and insights

-- 1. Provider performance (cached metrics)
CREATE TABLE IF NOT EXISTS provider_performance (
    provider_id UUID PRIMARY KEY REFERENCES providers(id) ON DELETE CASCADE,
    acceptance_rate DOUBLE PRECISION DEFAULT 0,
    completion_rate DOUBLE PRECISION DEFAULT 0,
    cancellation_rate DOUBLE PRECISION DEFAULT 0,
    response_rate DOUBLE PRECISION DEFAULT 0,
    avg_response_time VARCHAR(20) DEFAULT '0s',
    repeat_customer_count INTEGER DEFAULT 0,
    ranking VARCHAR(100) DEFAULT 'Building reputation',
    calculated_at TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW()
);

-- 2. Provider weekly goals
CREATE TABLE IF NOT EXISTS provider_goals (
    provider_id UUID PRIMARY KEY REFERENCES providers(id) ON DELETE CASCADE,
    weekly_target DOUBLE PRECISION DEFAULT 25000,
    weekly_progress DOUBLE PRECISION DEFAULT 0,
    week_number INTEGER NOT NULL,
    year INTEGER NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_provider_goals_week ON provider_goals(year, week_number);

-- 3. Provider achievements
CREATE TABLE IF NOT EXISTS provider_achievements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    achievement_id VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    icon VARCHAR(50) NOT NULL,
    unlocked BOOLEAN DEFAULT FALSE,
    unlocked_at TIMESTAMP,
    progress INTEGER DEFAULT 0,
    target INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_provider_achievements_provider ON provider_achievements(provider_id);

-- 4. Provider reputation (composite score)
CREATE TABLE IF NOT EXISTS provider_reputation (
    provider_id UUID PRIMARY KEY REFERENCES providers(id) ON DELETE CASCADE,
    score INTEGER DEFAULT 0,
    tier VARCHAR(20) DEFAULT 'NEW',
    sqs_contribution DOUBLE PRECISION DEFAULT 0,
    completion_contribution DOUBLE PRECISION DEFAULT 0,
    response_contribution DOUBLE PRECISION DEFAULT 0,
    consistency_bonus DOUBLE PRECISION DEFAULT 0,
    cancellation_penalty DOUBLE PRECISION DEFAULT 0,
    dispute_penalty DOUBLE PRECISION DEFAULT 0,
    calculated_at TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW()
);

-- 5. Provider insights (AI-generated)
CREATE TABLE IF NOT EXISTS provider_insights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    insight_type VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    frequency INTEGER DEFAULT 0,
    generated_at TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_provider_insights_provider ON provider_insights(provider_id);
