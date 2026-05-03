DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'garmin_activities' AND column_name = 'user_tag'
    ) THEN
        ALTER TABLE garmin_activities ADD COLUMN user_tag VARCHAR(50);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'idx_garmin_activities_user_tag'
    ) THEN
        CREATE INDEX idx_garmin_activities_user_tag ON garmin_activities(user_tag);
    END IF;
END $$;
