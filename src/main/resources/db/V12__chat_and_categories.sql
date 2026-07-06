-- Chat Rooms table
CREATE TABLE IF NOT EXISTS chat_rooms (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID NOT NULL REFERENCES jobs(id),
    customer_id UUID NOT NULL REFERENCES users(id),
    provider_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chat_rooms_job ON chat_rooms(job_id);
CREATE INDEX idx_chat_rooms_customer ON chat_rooms(customer_id);
CREATE INDEX idx_chat_rooms_provider ON chat_rooms(provider_id);

-- Messages table
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    room_id UUID NOT NULL REFERENCES chat_rooms(id),
    sender_id UUID NOT NULL REFERENCES users(id),
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_room ON messages(room_id);
CREATE INDEX idx_messages_sender ON messages(sender_id);

-- Categories table
CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    icon_url TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    parent_id UUID REFERENCES categories(id),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_categories_parent ON categories(parent_id);
CREATE INDEX idx_categories_active ON categories(is_active);

-- Insert default categories
INSERT INTO categories (name, description, sort_order, is_active) VALUES
    ('Plumbing', 'Plumbing services including repairs and installations', 1, true),
    ('Electrical', 'Electrical services including wiring and repairs', 2, true),
    ('Cleaning', 'Home and office cleaning services', 3, true),
    ('Painting', 'Interior and exterior painting services', 4, true),
    ('Carpentry', 'Carpentry and woodwork services', 5, true),
    ('Gardening', 'Gardening and landscaping services', 6, true),
    ('Moving', 'Moving and relocation services', 7, true),
    ('Tutoring', 'Academic tutoring and lessons', 8, true),
    ('IT Support', 'Computer and IT support services', 9, true),
    ('Photography', 'Photography and videography services', 10, true)
ON CONFLICT (name) DO NOTHING;