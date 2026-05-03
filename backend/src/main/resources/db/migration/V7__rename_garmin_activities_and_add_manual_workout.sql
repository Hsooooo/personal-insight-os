-- Rename garmin_activities → activities and add manual workout support

-- 1. Rename main table
ALTER TABLE garmin_activities RENAME TO activities;

-- 2. Rename column: garmin_activity_id → external_activity_id (nullable for manual entries)
ALTER TABLE activities RENAME COLUMN garmin_activity_id TO external_activity_id;
ALTER TABLE activities ALTER COLUMN external_activity_id DROP NOT NULL;

-- 3. Add source_type to distinguish GARMIN vs MANUAL
ALTER TABLE activities ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'GARMIN';

-- 4. Make raw_payload nullable (manual entries don't have raw Garmin payload)
ALTER TABLE activities ALTER COLUMN raw_payload DROP NOT NULL;

-- 5. Add weight_training_detail JSONB for manual weight training entries
ALTER TABLE activities ADD COLUMN weight_training_detail JSONB;

-- 6. Rename indexes
ALTER INDEX IF EXISTS idx_garmin_activities_user_id RENAME TO idx_activities_user_id;
ALTER INDEX IF EXISTS idx_garmin_activities_start_time RENAME TO idx_activities_start_time;
ALTER INDEX IF EXISTS idx_garmin_activities_user_tag RENAME TO idx_activities_user_tag;

-- 7. Drop old unique constraint and recreate for external_activity_id
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'garmin_activities_user_id_garmin_activity_id_key'
    ) THEN
        ALTER TABLE activities DROP CONSTRAINT garmin_activities_user_id_garmin_activity_id_key;
    END IF;
END $$;

-- Only enforce uniqueness when external_activity_id is not null (Garmin sync entries)
-- PostgreSQL allows multiple NULLs in unique index
CREATE UNIQUE INDEX IF NOT EXISTS idx_activities_user_external_id
    ON activities (user_id, external_activity_id)
    WHERE external_activity_id IS NOT NULL;

-- 8. Update graph_node_mappings source_table reference
UPDATE graph_node_mappings SET source_table = 'activities' WHERE source_table = 'garmin_activities';
