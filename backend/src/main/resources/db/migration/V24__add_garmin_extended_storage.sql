-- Garmin 제공 데이터 전량 저장: 일일 raw 페이로드 테이블 + 고가치 필드 구조화 승격
-- 1) 신규 엔드포인트(HRV, Body Battery 등)는 garmin_daily_raw에 jsonb로 전량 저장
-- 2) 기존 sleep/health raw_payload에서 고가치 필드를 컬럼으로 백필 (재수집 불필요)
-- ※ json null은 ->>' 텍스트 추출 시 SQL NULL이 됨. 정수 컬럼은 float 문자열('84.0') 대비 ROUND 경유.

CREATE TABLE garmin_daily_raw (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    metric_date DATE NOT NULL,
    data_type VARCHAR(30) NOT NULL,   -- HRV | BODY_BATTERY | STRESS | HEART_RATE | STEPS | RESPIRATION | SPO2 | TRAINING_READINESS | TRAINING_STATUS
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_garmin_daily_raw UNIQUE (user_id, metric_date, data_type)
);

-- 수면 구조화 확장 (낮잠 포함)
ALTER TABLE garmin_sleep_sessions
    ADD COLUMN nap_seconds INTEGER,
    ADD COLUMN avg_sleep_stress NUMERIC(5,2),
    ADD COLUMN sleep_need_minutes INTEGER,
    ADD COLUMN hrv_status VARCHAR(20);

-- 일일 건강 구조화 확장
ALTER TABLE garmin_daily_health_metrics
    ADD COLUMN average_spo2 NUMERIC(5,2),
    ADD COLUMN lowest_spo2 INTEGER,
    ADD COLUMN avg_waking_respiration NUMERIC(5,2),
    ADD COLUMN floors_ascended NUMERIC(8,2),
    ADD COLUMN floors_descended NUMERIC(8,2),
    ADD COLUMN moderate_intensity_minutes INTEGER,
    ADD COLUMN vigorous_intensity_minutes INTEGER,
    ADD COLUMN active_kilocalories NUMERIC(10,2),
    ADD COLUMN bmr_kilocalories NUMERIC(10,2),
    ADD COLUMN body_battery_at_wake INTEGER,
    ADD COLUMN body_battery_charged INTEGER,
    ADD COLUMN body_battery_drained INTEGER,
    ADD COLUMN total_distance_meters INTEGER;

-- 백필: 수면
UPDATE garmin_sleep_sessions SET
    nap_seconds = ROUND(NULLIF(raw_payload->'dailySleepDTO'->>'napTimeSeconds', '')::numeric)::int,
    avg_sleep_stress = NULLIF(raw_payload->'dailySleepDTO'->>'avgSleepStress', '')::numeric,
    sleep_need_minutes = ROUND(NULLIF(raw_payload->'dailySleepDTO'->'sleepNeed'->>'actual', '')::numeric)::int,
    hrv_status = NULLIF(raw_payload->>'hrvStatus', '');

-- 백필: 일일 건강
UPDATE garmin_daily_health_metrics SET
    average_spo2 = NULLIF(raw_payload->>'averageSpo2', '')::numeric,
    lowest_spo2 = ROUND(NULLIF(raw_payload->>'lowestSpo2', '')::numeric)::int,
    avg_waking_respiration = NULLIF(raw_payload->>'avgWakingRespirationValue', '')::numeric,
    floors_ascended = NULLIF(raw_payload->>'floorsAscended', '')::numeric,
    floors_descended = NULLIF(raw_payload->>'floorsDescended', '')::numeric,
    moderate_intensity_minutes = ROUND(NULLIF(raw_payload->>'moderateIntensityMinutes', '')::numeric)::int,
    vigorous_intensity_minutes = ROUND(NULLIF(raw_payload->>'vigorousIntensityMinutes', '')::numeric)::int,
    active_kilocalories = NULLIF(raw_payload->>'activeKilocalories', '')::numeric,
    bmr_kilocalories = NULLIF(raw_payload->>'bmrKilocalories', '')::numeric,
    body_battery_at_wake = ROUND(NULLIF(raw_payload->>'bodyBatteryAtWakeTime', '')::numeric)::int,
    body_battery_charged = ROUND(NULLIF(raw_payload->>'bodyBatteryChargedValue', '')::numeric)::int,
    body_battery_drained = ROUND(NULLIF(raw_payload->>'bodyBatteryDrainedValue', '')::numeric)::int,
    total_distance_meters = ROUND(NULLIF(raw_payload->>'totalDistanceMeters', '')::numeric)::int;
