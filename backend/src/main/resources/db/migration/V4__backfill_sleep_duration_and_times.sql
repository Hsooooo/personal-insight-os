-- Backfill: Fix total_sleep_seconds from sleepTimeSeconds
UPDATE garmin_sleep_sessions
SET total_sleep_seconds = (raw_payload->'dailySleepDTO'->>'sleepTimeSeconds')::int
WHERE total_sleep_seconds = 0
  AND raw_payload->'dailySleepDTO'->>'sleepTimeSeconds' IS NOT NULL;

-- Backfill: Fix start_time from sleepStartTimestampGMT (millisecond epoch)
UPDATE garmin_sleep_sessions
SET start_time = to_timestamp((raw_payload->'dailySleepDTO'->>'sleepStartTimestampGMT')::bigint / 1000.0)::timestamptz
WHERE start_time IS NULL
  AND raw_payload->'dailySleepDTO'->>'sleepStartTimestampGMT' IS NOT NULL;

-- Backfill: Fix end_time from sleepEndTimestampGMT (millisecond epoch)
UPDATE garmin_sleep_sessions
SET end_time = to_timestamp((raw_payload->'dailySleepDTO'->>'sleepEndTimestampGMT')::bigint / 1000.0)::timestamptz
WHERE end_time IS NULL
  AND raw_payload->'dailySleepDTO'->>'sleepEndTimestampGMT' IS NOT NULL;
