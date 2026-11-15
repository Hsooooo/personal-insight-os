-- 레이스 목표(goal_type = 'RACE'): 대회 거리(km). 목표 기록은 target_value(초), 대회일은 target_date
ALTER TABLE goals ADD COLUMN race_distance_km NUMERIC(7, 3);
