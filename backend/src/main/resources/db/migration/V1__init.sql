CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";

-- users
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- provider_connections
CREATE TABLE provider_connections (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_type VARCHAR(50) NOT NULL,
    connection_status VARCHAR(30) NOT NULL DEFAULT 'DISCONNECTED',
    auth_payload JSONB,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, provider_type)
);

-- garmin_activities
CREATE TABLE garmin_activities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_connection_id BIGINT REFERENCES provider_connections(id),
    garmin_activity_id VARCHAR(100) NOT NULL,
    activity_type VARCHAR(50),
    activity_name VARCHAR(255),
    start_time TIMESTAMPTZ,
    duration_seconds INTEGER,
    distance_meters NUMERIC(12,2),
    average_pace_seconds NUMERIC(10,2),
    average_heart_rate INTEGER,
    max_heart_rate INTEGER,
    calories INTEGER,
    elevation_gain_meters NUMERIC(10,2),
    raw_payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, garmin_activity_id)
);

-- garmin_activity_laps
CREATE TABLE garmin_activity_laps (
    id BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES garmin_activities(id) ON DELETE CASCADE,
    lap_index INTEGER NOT NULL,
    start_time TIMESTAMPTZ,
    duration_seconds INTEGER,
    distance_meters NUMERIC(12,2),
    average_pace_seconds NUMERIC(10,2),
    average_heart_rate INTEGER,
    max_heart_rate INTEGER,
    raw_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (activity_id, lap_index)
);

-- garmin_daily_health_metrics
CREATE TABLE garmin_daily_health_metrics (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    metric_date DATE NOT NULL,
    resting_heart_rate INTEGER,
    hrv_avg NUMERIC(10,2),
    stress_avg NUMERIC(10,2),
    body_battery_min INTEGER,
    body_battery_max INTEGER,
    steps INTEGER,
    calories_total INTEGER,
    raw_payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, metric_date)
);

-- garmin_sleep_sessions
CREATE TABLE garmin_sleep_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sleep_date DATE NOT NULL,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    total_sleep_seconds INTEGER,
    deep_sleep_seconds INTEGER,
    light_sleep_seconds INTEGER,
    rem_sleep_seconds INTEGER,
    awake_seconds INTEGER,
    sleep_score INTEGER,
    raw_payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, sleep_date)
);

-- goals
CREATE TABLE goals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    goal_type VARCHAR(50) NOT NULL,
    description TEXT,
    target_value NUMERIC(12,2),
    target_unit VARCHAR(50),
    start_date DATE,
    target_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- llm_providers
CREATE TABLE llm_providers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_name VARCHAR(50) NOT NULL,
    api_key_encrypted TEXT,
    default_chat_model VARCHAR(100),
    embedding_model VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    monthly_budget_limit NUMERIC(12,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, provider_name)
);

-- questions
CREATE TABLE questions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_text TEXT NOT NULL,
    intent VARCHAR(100),
    time_range_start DATE,
    time_range_end DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- insights
CREATE TABLE insights (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id BIGINT REFERENCES questions(id) ON DELETE SET NULL,
    category VARCHAR(50),
    title VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    confidence NUMERIC(4,3),
    model_provider VARCHAR(50),
    model_name VARCHAR(100),
    feedback_status VARCHAR(30),
    is_saved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- insight_evidences
CREATE TABLE insight_evidences (
    id BIGSERIAL PRIMARY KEY,
    insight_id BIGINT NOT NULL REFERENCES insights(id) ON DELETE CASCADE,
    evidence_type VARCHAR(50) NOT NULL,
    source_table VARCHAR(100),
    source_id BIGINT,
    evidence_summary TEXT,
    weight NUMERIC(4,3),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- graph_node_mappings
CREATE TABLE graph_node_mappings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_table VARCHAR(100) NOT NULL,
    source_id BIGINT NOT NULL,
    neo4j_node_id VARCHAR(100) NOT NULL,
    node_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (source_table, source_id, node_type)
);

-- Indexes
CREATE INDEX idx_garmin_activities_user_id ON garmin_activities(user_id);
CREATE INDEX idx_garmin_activities_start_time ON garmin_activities(start_time);
CREATE INDEX idx_garmin_health_metrics_user_date ON garmin_daily_health_metrics(user_id, metric_date);
CREATE INDEX idx_garmin_sleep_user_date ON garmin_sleep_sessions(user_id, sleep_date);
CREATE INDEX idx_insights_user_id ON insights(user_id);
CREATE INDEX idx_insights_saved ON insights(user_id, is_saved);
CREATE INDEX idx_questions_user_id ON questions(user_id);
