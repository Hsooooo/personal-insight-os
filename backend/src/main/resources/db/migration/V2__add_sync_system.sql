-- sync_logs: 동기화 이력 테이블
CREATE TABLE sync_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_type VARCHAR(50) NOT NULL,
    sync_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    date_from DATE,
    date_to DATE,
    activities_count INTEGER DEFAULT 0,
    health_metrics_count INTEGER DEFAULT 0,
    sleep_count INTEGER DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sync_logs_user_id ON sync_logs(user_id);
CREATE INDEX idx_sync_logs_started_at ON sync_logs(started_at DESC);

-- provider_connections: 동기화 설정 컬럼 추가
ALTER TABLE provider_connections ADD COLUMN sync_config JSONB DEFAULT '{
    "full_sync_from": null,
    "last_sync_date": null,
    "sync_range_days": 7,
    "auto_sync_enabled": true,
    "auto_sync_cron": "0 3 * * *"
}';
