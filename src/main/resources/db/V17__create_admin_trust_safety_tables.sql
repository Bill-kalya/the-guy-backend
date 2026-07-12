-- Admin Actions
CREATE TABLE IF NOT EXISTS admin_actions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    admin_id UUID REFERENCES users(id) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    details TEXT NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    device_id VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    timestamp TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_actions_admin ON admin_actions(admin_id);
CREATE INDEX IF NOT EXISTS idx_admin_actions_created ON admin_actions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_actions_type ON admin_actions(action_type);

-- Risk Scores
CREATE TABLE IF NOT EXISTS risk_scores (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) NOT NULL,
    user_type VARCHAR(20) NOT NULL,
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 100),
    risk_level VARCHAR(20) NOT NULL,
    factors JSONB NOT NULL,
    recommendations TEXT,
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_risk_scores_user ON risk_scores(user_id);
CREATE INDEX IF NOT EXISTS idx_risk_scores_score ON risk_scores(score);
CREATE INDEX IF NOT EXISTS idx_risk_scores_level ON risk_scores(risk_level);

-- Disputes
CREATE TABLE IF NOT EXISTS disputes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID REFERENCES jobs(id) NOT NULL,
    customer_id UUID REFERENCES users(id) NOT NULL,
    provider_id UUID REFERENCES providers(id) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    assignee_id UUID REFERENCES users(id),
    resolution_id UUID,
    resolution_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_disputes_job ON disputes(job_id);
CREATE INDEX IF NOT EXISTS idx_disputes_status ON disputes(status);
CREATE INDEX IF NOT EXISTS idx_disputes_assignee ON disputes(assignee_id);

-- Moderation Cases
CREATE TABLE IF NOT EXISTS moderation_cases (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reported_by UUID REFERENCES users(id) NOT NULL,
    reported_user UUID REFERENCES users(id) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    content_id UUID,
    reason TEXT NOT NULL,
    evidence TEXT,
    status VARCHAR(50) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    assignee_id UUID REFERENCES users(id),
    action_taken TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    escalated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_moderation_status ON moderation_cases(status);
CREATE INDEX IF NOT EXISTS idx_moderation_assignee ON moderation_cases(assignee_id);

