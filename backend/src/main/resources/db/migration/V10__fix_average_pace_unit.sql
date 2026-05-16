-- average_pace_seconds 단위 통일: 기존 m/s(1~50)와 sec/m(0.05~1)가 섞여 있었음
-- 목표 단위: sec/km (초/킬로미터)
-- MockDataService는 이미 sec/km를 저장하므로 50 이상 값은 건드리지 않음

-- activities: m/s로 저장된 데이터 → sec/km
UPDATE activities
SET average_pace_seconds = ROUND(1000.0 / average_pace_seconds, 2)
WHERE average_pace_seconds > 1 AND average_pace_seconds < 50;

-- activities: sec/m으로 저장된 데이터 → sec/km
UPDATE activities
SET average_pace_seconds = ROUND(average_pace_seconds * 1000, 2)
WHERE average_pace_seconds > 0.05 AND average_pace_seconds <= 1;

-- garmin_activity_laps: m/s로 저장된 데이터 → sec/km
UPDATE garmin_activity_laps
SET average_pace_seconds = ROUND(1000.0 / average_pace_seconds, 2)
WHERE average_pace_seconds > 1 AND average_pace_seconds < 50;

-- garmin_activity_laps: sec/m으로 저장된 데이터 → sec/km
UPDATE garmin_activity_laps
SET average_pace_seconds = ROUND(average_pace_seconds * 1000, 2)
WHERE average_pace_seconds > 0.05 AND average_pace_seconds <= 1;
