-- Backfill: Fix sleep_score from nested sleepScores.overall.value
UPDATE garmin_sleep_sessions
SET sleep_score = (raw_payload->'dailySleepDTO'->'sleepScores'->'overall'->>'value')::int
WHERE sleep_score = 0
  AND raw_payload->'dailySleepDTO'->'sleepScores'->'overall'->>'value' IS NOT NULL;

-- Backfill: Fix start_time from startTimeLocal (space-separated local datetime)
UPDATE garmin_activities
SET start_time = to_timestamp(raw_payload->>'startTimeLocal', 'YYYY-MM-DD HH24:MI:SS')::timestamptz
WHERE start_time IS NULL
  AND raw_payload->>'startTimeLocal' IS NOT NULL;
