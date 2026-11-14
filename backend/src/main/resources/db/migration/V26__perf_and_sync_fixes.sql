-- V26: 동기화 안정성 + 조회 성능 + 누락값 정리

-- ---------------------------------------------------------------------------
-- 1. 동기화: 사용자/Provider당 RUNNING 1건만 허용
--    마이그레이션 시점(애플리케이션 중지 상태)의 RUNNING은 모두 중단된 작업이므로 FAILED로 정리한 뒤
--    부분 유니크 인덱스를 만든다. (중복 RUNNING이 있으면 인덱스 생성이 실패하므로 선행 필수)
-- ---------------------------------------------------------------------------
UPDATE sync_logs
SET status = 'FAILED',
    error_message = COALESCE(error_message, 'Interrupted (cleaned up by migration V26)'),
    completed_at = COALESCE(completed_at, NOW())
WHERE status = 'RUNNING';

CREATE UNIQUE INDEX IF NOT EXISTS uq_sync_logs_running
    ON sync_logs (user_id, provider_type)
    WHERE status = 'RUNNING';

-- 최신 로그 조회 (findLatestByUserIdAndProviderType, 목록)
CREATE INDEX IF NOT EXISTS idx_sync_logs_user_provider_started
    ON sync_logs (user_id, provider_type, started_at DESC);

-- 사용되지 않던 sync_config.auto_sync_cron 제거 (스케줄은 sync.schedule.cron 전역 설정)
UPDATE provider_connections
SET sync_config = sync_config - 'auto_sync_cron'
WHERE sync_config ? 'auto_sync_cron';

ALTER TABLE provider_connections ALTER COLUMN sync_config SET DEFAULT '{
    "full_sync_from": null,
    "last_sync_date": null,
    "sync_range_days": 7,
    "auto_sync_enabled": true
}';

-- ---------------------------------------------------------------------------
-- 2. 조회 인덱스
-- ---------------------------------------------------------------------------
-- 인사이트 목록/카테고리별 최신순, 피드백 필터, 대시보드 최신 N개
CREATE INDEX IF NOT EXISTS idx_insights_user_category_created
    ON insights (user_id, category, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_insights_user_feedback
    ON insights (user_id, feedback_status);
CREATE INDEX IF NOT EXISTS idx_insights_user_created
    ON insights (user_id, created_at DESC);

-- 근거 배치 조회 (insight_id IN (...))
CREATE INDEX IF NOT EXISTS idx_insight_evidences_insight
    ON insight_evidences (insight_id);

-- 활동 최신순/기간 조회 (findRecentByUserId, findByUserIdOrderByStartTimeDesc)
CREATE INDEX IF NOT EXISTS idx_activities_user_start
    ON activities (user_id, start_time DESC);

-- 목표/질문/그래프 매핑 사용자별 조회
CREATE INDEX IF NOT EXISTS idx_goals_user
    ON goals (user_id);
CREATE INDEX IF NOT EXISTS idx_questions_user_created
    ON questions (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_graph_node_mappings_user
    ON graph_node_mappings (user_id);

-- 일 단위 원문 (user_id, metric_date IN (...)) 조회는 uk_garmin_daily_raw(user_id, metric_date, data_type)가 커버

-- ---------------------------------------------------------------------------
-- 3. 누락값 정리: 과거 garmin_sync.py가 값이 없을 때 0을 저장했음.
--    0이 생리학적으로 불가능한 지표는 NULL로 바꿔 평균/기준선 오염을 막는다.
--    (되돌릴 필요가 없는 정보 손실 없음: 0은 "측정 없음"이었다)
-- ---------------------------------------------------------------------------
UPDATE garmin_daily_health_metrics SET resting_heart_rate = NULL WHERE resting_heart_rate = 0;
UPDATE garmin_daily_health_metrics SET hrv_avg = NULL WHERE hrv_avg = 0;
UPDATE garmin_daily_health_metrics SET stress_avg = NULL WHERE stress_avg <= 0;

UPDATE garmin_sleep_sessions SET sleep_score = NULL WHERE sleep_score = 0;
UPDATE garmin_sleep_sessions SET total_sleep_seconds = NULL WHERE total_sleep_seconds = 0;

UPDATE activities SET average_heart_rate = NULL WHERE average_heart_rate = 0;
UPDATE activities SET max_heart_rate = NULL WHERE max_heart_rate = 0;
