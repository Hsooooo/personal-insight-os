-- 하프 마라톤 21.0975km를 그대로 저장하도록 소수 4자리까지 허용
ALTER TABLE goals ALTER COLUMN race_distance_km TYPE NUMERIC(8, 4);
