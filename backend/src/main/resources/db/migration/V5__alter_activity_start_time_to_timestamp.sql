-- Change garmin_activities.start_time from TIMESTAMPTZ to TIMESTAMP (no timezone)
-- Garmin provides startTimeLocal as user-local time; we should store it as-is.

ALTER TABLE garmin_activities ALTER COLUMN start_time TYPE TIMESTAMP;

-- Backfill: restore local time from raw_payload.startTimeLocal
UPDATE garmin_activities
SET start_time = (raw_payload->>'startTimeLocal')::timestamp
WHERE raw_payload->>'startTimeLocal' IS NOT NULL;
