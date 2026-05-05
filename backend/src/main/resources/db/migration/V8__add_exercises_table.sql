CREATE TABLE exercises (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    body_part VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, name)
);

-- 기존 weight training 데이터에서 exercise names 마이그레이션
INSERT INTO exercises (user_id, name, body_part, created_at)
SELECT DISTINCT
    a.user_id,
    trim(e->>'name') AS name,
    NULL::VARCHAR(20),
    NOW()
FROM activities a,
     jsonb_array_elements(a.weight_training_detail->'exercises') AS e
WHERE a.source_type = 'MANUAL'
  AND a.activity_type = 'WEIGHT_TRAINING'
  AND a.weight_training_detail IS NOT NULL
  AND jsonb_typeof(a.weight_training_detail->'exercises') = 'array'
ON CONFLICT (user_id, name) DO NOTHING;
